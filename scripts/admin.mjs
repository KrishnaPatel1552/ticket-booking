/**
 * Runs admin API actions from a JSON file.
 *
 *   node scripts/admin.mjs block
 *   node scripts/admin.mjs sales
 *   node scripts/admin.mjs scripts/admin/my-demo.json
 *
 * A file is one action, or an array of actions in order.
 * Edit the file. The command stays the same.
 *
 * cancellation, reschedule, and exceptional-refund run only when the file
 * has "confirm": true.
 *
 * Requires the API on http://localhost:8080.
 * BASE, ADMIN_EMAIL, and ADMIN_PASSWORD override the defaults.
 */
import { readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const files = {
  event: "event.json",
  "price-tier": "price-tier.json",
  promo: "promo.json",
  block: "block.json",
  release: "release.json",
  cancellation: "cancellation.json",
  reschedule: "reschedule.json",
  "exceptional-refund": "exceptional-refund.json",
  audit: "audit.json",
  sales: "sales.json",
};

const base = process.env.BASE ?? "http://localhost:8080";
const email = process.env.ADMIN_EMAIL ?? "admin@example.com";
const password = process.env.ADMIN_PASSWORD ?? "password";
const guarded = new Set(["cancellation", "reschedule", "exceptional-refund"]);

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

function printCall(method, path, body) {
  console.log(`\n${method} ${path}`);
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

function configPath(argument) {
  if (argument == null) {
    const names = Object.keys(files).map((name) => `  ${name}`).join("\n");
    throw new Error(`Name an action file:\n\n${names}\n\nExample: node scripts/admin.mjs block`);
  }
  if (argument.endsWith(".json") || argument.includes("/")) {
    return resolve(argument);
  }
  const file = files[argument];
  if (file == null) {
    throw new Error(`No action file named ${argument}.`);
  }
  return join(here, "admin", file);
}

async function loadSteps(path) {
  const parsed = JSON.parse(await readFile(path, "utf8"));
  const steps = Array.isArray(parsed) ? parsed : [parsed];
  if (steps.length === 0) {
    throw new Error(`${path} has no actions.`);
  }
  return steps;
}

async function signIn() {
  printCall("POST", "/api/sign-in");
  const signedIn = await call("POST", "/api/sign-in", { email, password });
  printResult({ status: signedIn.status, json: signedIn.json });
  if (signedIn.status !== 200 || signedIn.json?.role !== "admin") {
    throw new Error("Admin sign-in failed.");
  }
}

async function eventByName(name) {
  const result = await call("GET", `/api/events?name=${encodeURIComponent(name)}`);
  if (result.status !== 200 || !Array.isArray(result.json)) {
    printResult(result);
    throw new Error(`Event search failed for ${name}.`);
  }
  const found = result.json.find((event) => event.name === name);
  if (found == null) {
    throw new Error(`No event named ${name}.`);
  }
  return found;
}

async function venueId(venueName) {
  const term = venueName.split(" ")[0];
  const result = await call("GET", `/api/events?name=${encodeURIComponent(term)}`);
  if (result.status !== 200 || !Array.isArray(result.json)) {
    printResult(result);
    throw new Error(`Could not find venue ${venueName}.`);
  }
  const found = result.json.find((event) => event.venueName === venueName);
  if (found == null) {
    throw new Error(`No event is at venue ${venueName}, so its id is unknown.`);
  }
  return found.venueId;
}

async function seatByLabel(eventId, label) {
  const result = await call("GET", `/api/events/${eventId}`);
  if (result.status !== 200) {
    printResult(result);
    throw new Error("The seat map did not open.");
  }
  for (const section of result.json.sections) {
    const seat = section.seats.find((item) => item.label === label);
    if (seat != null) {
      return seat;
    }
  }
  throw new Error(`Seat ${label} is not on this event.`);
}

function requireFields(step, fields) {
  for (const field of fields) {
    if (step[field] == null || step[field] === "") {
      throw new Error(`${step.action} needs ${field}.`);
    }
  }
}

async function runStep(step) {
  if (guarded.has(step.action) && step.confirm !== true) {
    throw new Error(`${step.action} needs "confirm": true in the file. Nothing was sent.`);
  }

  if (step.action === "event") {
    requireFields(step, ["name", "venue", "startsAt", "timeZone"]);
    const body = {
      name: step.name,
      venueId: await venueId(step.venue),
      startsAt: step.startsAt,
      timeZone: step.timeZone,
    };
    if (step.earlyBirdStartsAt != null) {
      body.earlyBirdStartsAt = step.earlyBirdStartsAt;
    }
    if (step.earlyBirdEndsAt != null) {
      body.earlyBirdEndsAt = step.earlyBirdEndsAt;
    }
    return send("POST", "/api/admin/events", body, 201);
  }

  if (step.action === "price-tier") {
    requireFields(step, ["name", "venue", "priceCents"]);
    const body = {
      name: step.name,
      venueId: await venueId(step.venue),
      priceCents: step.priceCents,
    };
    if (step.earlyBirdPriceCents != null) {
      body.earlyBirdPriceCents = step.earlyBirdPriceCents;
    }
    return send("POST", "/api/admin/price-tiers", body, 201);
  }

  if (step.action === "promo") {
    requireFields(step, ["code", "amountCents", "globalLimit", "perCustomerLimit", "exclusive"]);
    return send("POST", "/api/admin/promo-codes", {
      code: step.code,
      amountCents: step.amountCents,
      globalLimit: step.globalLimit,
      perCustomerLimit: step.perCustomerLimit,
      exclusive: step.exclusive,
    }, 201);
  }

  if (step.action === "block" || step.action === "release") {
    requireFields(step, ["event", "seat", "reason"]);
    const event = await eventByName(step.event);
    const seat = await seatByLabel(event.id, step.seat);
    const verb = step.action === "block" ? "block" : "release";
    return send("POST", `/api/admin/events/${event.id}/seats/${seat.id}/${verb}`, { reason: step.reason }, 200);
  }

  if (step.action === "cancellation") {
    requireFields(step, ["event", "reason", "mock"]);
    const event = await eventByName(step.event);
    const body = { reason: step.reason, mock: step.mock };
    if (step.lookup != null) {
      body.lookup = step.lookup;
    }
    return send("POST", `/api/admin/events/${event.id}/cancellation`, body, 200);
  }

  if (step.action === "reschedule") {
    requireFields(step, ["event", "startsAt", "reason"]);
    const event = await eventByName(step.event);
    return send("POST", `/api/admin/events/${event.id}/reschedule`, {
      startsAt: step.startsAt,
      reason: step.reason,
    }, 200);
  }

  if (step.action === "exceptional-refund") {
    requireFields(step, ["orderId", "amountCents", "reason", "mock"]);
    return send("POST", `/api/admin/orders/${step.orderId}/exceptional-refunds`, {
      amountCents: step.amountCents,
      reason: step.reason,
      mock: step.mock,
    }, 200);
  }

  if (step.action === "audit") {
    requireFields(step, ["event"]);
    const event = await eventByName(step.event);
    return send("GET", `/api/admin/events/${event.id}/audit-entries`, null, 200);
  }

  if (step.action === "sales") {
    requireFields(step, ["event"]);
    const event = await eventByName(step.event);
    return send("GET", `/api/admin/events/${event.id}/sales`, null, 200);
  }

  throw new Error(`Unknown action ${step.action}.`);
}

async function send(method, path, body, expected) {
  printCall(method, path, body);
  const result = await call(method, path, body);
  printResult(result);
  if (result.status !== expected) {
    throw new Error(`${method} ${path} returned ${result.status}.`);
  }
}

try {
  const path = configPath(process.argv[2]);
  const steps = await loadSteps(path);
  await signIn();
  for (const step of steps) {
    await runStep(step);
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
