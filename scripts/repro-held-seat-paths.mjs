/**
 * Red when a customer who holds seats cannot release them, check out later,
 * or return to search. Green when those three paths exist.
 *
 * Requires the customer site on http://127.0.0.1:5173 and the API behind it.
 * Run: node scripts/repro-held-seat-paths.mjs
 * Playwright is loaded from /tmp/hold-repro (npm install playwright).
 */
import playwright from "/tmp/hold-repro/node_modules/playwright/index.js";

const { chromium } = playwright;

const base = process.env.HOLD_REPRO_BASE ?? "http://127.0.0.1:5173";
const email = process.env.HOLD_REPRO_EMAIL ?? "grace@example.com";
const password = process.env.HOLD_REPRO_PASSWORD ?? "password";

const failures = [];

function note(ok, message) {
  const line = `${ok ? "ok" : "FAIL"} ${message}`;
  console.log(line);
  if (!ok) {
    failures.push(message);
  }
}

async function signIn(page) {
  await page.goto(base);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.getByLabel("Search events by name").waitFor();
}

async function openHarborLights(page) {
  await page.getByLabel("Search events by name").fill("Harbor");
  await page.getByRole("button", { name: "Search" }).click();
  const item = page.locator("li").filter({ hasText: "Harbor Lights" });
  await item.getByRole("button", { name: "Open seat map" }).click();
  await page.getByRole("grid").first().waitFor();
}

async function controlNames(page) {
  return page.getByRole("button").evaluateAll((nodes) => nodes.map((node) => node.textContent?.trim() ?? ""));
}

function hasNamed(names, pattern) {
  return names.some((name) => pattern.test(name));
}

async function canSearch(page, names) {
  return (await page.getByLabel("Search events by name").count()) > 0 || hasNamed(names, /search/i);
}

async function serverSeatState(page, label) {
  return page.evaluate(async (seatLabel) => {
    const events = await fetch("/api/events?name=Harbor", { credentials: "include" }).then((response) =>
      response.json(),
    );
    const event = events.find((item) => item.name === "Harbor Lights");
    const map = await fetch(`/api/events/${event.id}`, { credentials: "include" }).then((response) => response.json());
    for (const section of map.sections) {
      const seat = section.seats.find((item) => item.label === seatLabel);
      if (seat != null) {
        return seat.state;
      }
    }
    return "missing";
  }, label);
}

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await browser.newPage();

try {
  await signIn(page);
  await openHarborLights(page);

  const free = page.getByRole("button", { name: / free$/ });
  const freeCount = await free.count();
  if (freeCount < 1) {
    console.log("LOOP-BROKEN no free seat to hold");
    process.exit(2);
  }
  const freeName = await free.first().getAttribute("aria-label");
  if (freeName == null) {
    console.log("LOOP-BROKEN free seat has no name");
    process.exit(2);
  }
  const label = freeName.replace(/ free$/, "");
  await free.first().click();
  await page.getByRole("button", { name: "Hold seats" }).click();

  const heldHeading = page.getByRole("heading", { name: "Hold" });
  const refused = page.getByText("Those seats could not be held.");
  await heldHeading.or(refused).waitFor({ timeout: 8000 });
  if ((await heldHeading.count()) === 0) {
    console.log("LOOP-BROKEN hold was refused");
    process.exit(2);
  }

  let names = await controlNames(page);
  note(hasNamed(names, /^Check out$/), "hold screen offers Check out immediately after Hold seats");
  note(hasNamed(names, /release/i), "hold screen offers a manual release");
  note(await canSearch(page, names), "hold screen can return to search");

  await page.getByRole("button", { name: "Change seats" }).click();
  await page.getByRole("grid").first().waitFor();
  const shownFree = (await page.getByRole("button", { name: `${label} free` }).count()) === 1;
  const stateAfterChange = await serverSeatState(page, label);
  console.log(`OBS ${label} button says ${shownFree ? "free" : "not free"}; server says ${stateAfterChange}`);
  names = await controlNames(page);
  const openHold = stateAfterChange === "held";
  note(!openHold || hasNamed(names, /release/i), `${label} can be released after leaving the hold screen`);
  note(!openHold || hasNamed(names, /^Check out$/), `${label} can be checked out after leaving the hold screen`);
  note(!openHold || (await canSearch(page, names)), "seat map can return to search while a hold is open");

  await page.reload();
  await signIn(page);
  await openHarborLights(page);
  const stateAfterReload = await serverSeatState(page, label);
  const shownHeld = (await page.getByRole("button", { name: `${label} held` }).count()) === 1;
  console.log(`OBS ${label} button says ${shownHeld ? "held" : "not held"}; server says ${stateAfterReload}`);
  names = await controlNames(page);
  const heldAfterReload = stateAfterReload === "held";
  note(!heldAfterReload || hasNamed(names, /^Check out$/), "reopened seat map offers Check out for the existing hold");
  note(!heldAfterReload || hasNamed(names, /release/i), "reopened seat map offers a manual release");
  note(!heldAfterReload || (await canSearch(page, names)), "reopened seat map can return to search");
} finally {
  await browser.close();
}

if (failures.length > 0) {
  console.log(`RED ${failures.length} missing path(s)`);
  process.exit(1);
}
console.log("GREEN hold, release, checkout, and search paths are present");
