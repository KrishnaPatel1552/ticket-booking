/**
 * Signs in as an admin, blocks one free seat, then releases that block.
 *
 * The API word is release. A release puts a blocked seat back to free.
 *
 * Requires the API on http://localhost:8080.
 * Run: node scripts/demo-block-release.mjs
 *
 * Optional:
 *   BASE=http://localhost:8080
 *   EVENT=Harbor Lights
 *   SEAT=A12                 a seat label; the default is the first free seat
 *   BLOCK_REASON="Artist hold"
 *   RELEASE_REASON="Artist is seated"
 *   PAUSE=1                  press Enter between block and release (default when a terminal is attached)
 */
import { createInterface } from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";

const base = process.env.BASE ?? "http://localhost:8080";
const email = process.env.ADMIN_EMAIL ?? "admin@example.com";
const password = process.env.ADMIN_PASSWORD ?? "password";
const eventName = process.env.EVENT ?? "Harbor Lights";
const seatLabel = process.env.SEAT ?? "";
const blockReason = process.env.BLOCK_REASON ?? "Artist hold";
const releaseReason = process.env.RELEASE_REASON ?? "Artist is seated";
const pause = process.env.PAUSE != null ? process.env.PAUSE !== "0" : Boolean(input.isTTY);

const jar = new Map();

function storeCookies(response) {
  const lines = typeof response.headers.getSetCookie === "function" ? response.headers.getSetCookie() : [];
  for (const line of lines) {
    const [pair] = line.split(";");
    const eq = pair.indexOf("=");
    if (eq > 0) {
      jar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
    }
  }
}

async function call(method, path, body) {
  const headers = { accept: "application/json" };
  if (jar.size > 0) {
    headers.cookie = [...jar.entries()].map(([name, value]) => `${name}=${value}`).join("; ");
  }
  if (body != null) {
    headers["content-type"] = "application/json";
  }
  const response = await fetch(`${base}${path}`, {
    method,
    headers,
    body: body == null ? undefined : JSON.stringify(body),
  });
  storeCookies(response);
  const text = await response.text();
  let json = null;
  if (text.length > 0) {
    try {
      json = JSON.parse(text);
    } catch {
      json = text;
    }
  }
  return { status: response.status, json };
}

function seatsOf(map) {
  return map.sections.flatMap((section) => section.seats);
}

function findSeat(map, label) {
  return seatsOf(map).find((seat) => seat.label === label) ?? null;
}

function printStep(title) {
  console.log(`\n${title}`);
}

function printCall(method, path, body) {
  console.log(`${method} ${path}`);
  if (body != null) {
    console.log(JSON.stringify(body));
  }
}

function printResult(result) {
  console.log(result.status);
  if (result.json != null) {
    console.log(JSON.stringify(result.json, null, 2));
  }
}

async function main() {
  printStep("1. Sign in as admin");
  printCall("POST", "/api/sign-in", { email, password });
  const signedIn = await call("POST", "/api/sign-in", { email, password });
  printResult(signedIn);
  if (signedIn.status !== 200 || signedIn.json?.role !== "admin") {
    throw new Error("Admin sign-in failed.");
  }

  printStep(`2. Open ${eventName}`);
  const searchPath = `/api/events?name=${encodeURIComponent(eventName)}`;
  printCall("GET", searchPath);
  const search = await call("GET", searchPath);
  printResult(search);
  const event = Array.isArray(search.json) ? search.json.find((item) => item.name === eventName) : null;
  if (event == null) {
    throw new Error(`No event named ${eventName}.`);
  }

  const mapPath = `/api/events/${event.id}`;
  printCall("GET", mapPath);
  const opened = await call("GET", mapPath);
  if (opened.status !== 200) {
    printResult(opened);
    throw new Error("The seat map did not open.");
  }
  const chosen = seatLabel === "" ? seatsOf(opened.json).find((seat) => seat.state === "free") : findSeat(opened.json, seatLabel);
  if (chosen == null) {
    throw new Error(seatLabel === "" ? "No free seat to block." : `Seat ${seatLabel} is not on this event.`);
  }
  console.log(`${chosen.label} (id ${chosen.id}) is ${chosen.state}.`);
  if (chosen.state !== "free") {
    throw new Error(`A block needs a free seat. ${chosen.label} is ${chosen.state}.`);
  }

  const blockPath = `/api/admin/events/${event.id}/seats/${chosen.id}/block`;
  const releasePath = `/api/admin/events/${event.id}/seats/${chosen.id}/release`;
  const auditPath = `/api/admin/events/${event.id}/audit-entries`;

  printStep(`3. Block ${chosen.label}`);
  printCall("POST", blockPath, { reason: blockReason });
  const blocked = await call("POST", blockPath, { reason: blockReason });
  printResult(blocked);
  if (blocked.status !== 200) {
    throw new Error("Block failed.");
  }

  const afterBlock = await call("GET", mapPath);
  const blockedSeat = findSeat(afterBlock.json, chosen.label);
  console.log(`Seat map: ${chosen.label} is ${blockedSeat?.state ?? "missing"}.`);

  printCall("GET", auditPath);
  const afterBlockAudit = await call("GET", auditPath);
  printResult(afterBlockAudit);

  if (pause) {
    const prompt = createInterface({ input, output });
    console.log("\nRefresh the seat map. Press Enter to release the block.");
    await prompt.question("");
    prompt.close();
  }

  printStep(`4. Release the block on ${chosen.label}`);
  printCall("POST", releasePath, { reason: releaseReason });
  const released = await call("POST", releasePath, { reason: releaseReason });
  printResult(released);
  if (released.status !== 200) {
    throw new Error("Release failed.");
  }

  const afterRelease = await call("GET", mapPath);
  const releasedSeat = findSeat(afterRelease.json, chosen.label);
  console.log(`Seat map: ${chosen.label} is ${releasedSeat?.state ?? "missing"}.`);

  printCall("GET", auditPath);
  const afterReleaseAudit = await call("GET", auditPath);
  printResult(afterReleaseAudit);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
