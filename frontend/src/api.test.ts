import { afterEach, expect, test, vi } from "vitest";
import { currentSession, searchEvents, signIn, signOut } from "./api";

afterEach(() => {
  sessionStorage.clear();
  vi.unstubAllGlobals();
});

test("a sign-in is kept on this tab and sent as a bearer token without a cookie", async () => {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path === "/api/sign-in") {
      return new Response(JSON.stringify({ email: "ada@example.com", role: "customer", token: "ada-tab" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }
    return new Response(JSON.stringify([]), { status: 200, headers: { "Content-Type": "application/json" } });
  });
  vi.stubGlobal("fetch", fetchMock);

  await signIn("ada@example.com", "password");
  await searchEvents("lights");

  expect(sessionStorage.getItem("ticket-booking.tab-session")).toBe("ada-tab");
  const searchCall = fetchMock.mock.calls[1];
  expect(searchCall?.[0]).toBe("/api/events?name=lights");
  const init = searchCall?.[1];
  expect(init?.credentials).toBe("omit");
  expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer ada-tab");
});

test("a tab with no sign-in does not ask who is signed in", async () => {
  const fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);

  await expect(currentSession()).resolves.toBeNull();
  expect(fetchMock).not.toHaveBeenCalled();
});

test("signing out drops this tab's sign-in", async () => {
  sessionStorage.setItem("ticket-booking.tab-session", "ada-tab");
  const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetchMock);

  await signOut();

  expect(sessionStorage.getItem("ticket-booking.tab-session")).toBeNull();
  expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
    method: "POST",
    credentials: "omit",
  });
  expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("Authorization")).toBe("Bearer ada-tab");
});
