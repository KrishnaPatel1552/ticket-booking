import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import {
  signIn,
  currentSession,
  fetchOrder,
  fetchSaleTerms,
  fetchSeatMap,
  fetchWaitlist,
  searchEvents,
  signOut,
  type EventSeatMap,
} from "./api";
import "./index.css";

vi.mock("./api", () => ({
  signIn: vi.fn(),
  currentSession: vi.fn().mockRejectedValue(new Error("unsigned")),
  signOut: vi.fn().mockResolvedValue(undefined),
  searchEvents: vi.fn(),
  fetchSeatMap: vi.fn(),
  fetchWaitlist: vi.fn(),
  joinWaitlist: vi.fn(),
  holdSeats: vi.fn(),
  attachPromoCode: vi.fn(),
  checkout: vi.fn(),
  fetchOrder: vi.fn(),
  fetchSaleTerms: vi.fn(),
  cancelTickets: vi.fn(),
}));

const harborMap: EventSeatMap = {
  id: 7,
  name: "Harbor Lights",
  venueName: "Harbor Hall",
  startsAt: "2026-06-12T23:00:00.000Z",
  timeZone: "America/New_York",
  earlyBirdStartsAt: null,
  earlyBirdEndsAt: null,
  sections: [
    {
      name: "Orchestra",
      seats: [
        {
          id: 1,
          label: "A1",
          priceCents: 5000,
          earlyBirdPriceCents: null,
          state: "free",
        },
      ],
    },
  ],
};

afterEach(() => {
  cleanup();
  window.history.replaceState(null, "", "/");
});

test("an unsigned session shows the sign-in card", async () => {
  render(<App />);

  expect(await screen.findByRole("button", { name: "Sign in" })).toBeTruthy();
  expect(vi.mocked(signIn)).not.toHaveBeenCalled();
  expect(vi.mocked(currentSession)).toHaveBeenCalled();
});

test("a live session opens that customer without the sign-in card", async () => {
  vi.mocked(currentSession).mockResolvedValueOnce({ email: "grace@example.com", role: "customer" });
  render(<App />);

  expect(await screen.findByText("Signed in as grace@example.com")).toBeTruthy();
  expect(screen.queryByLabelText("Password")).toBeNull();
  expect(vi.mocked(signIn)).not.toHaveBeenCalled();
});

test("a failed sign-in stays on the card and shows the failure under the button", async () => {
  vi.mocked(signIn).mockRejectedValueOnce(new Error("rejected"));
  const user = userEvent.setup();
  render(<App />);

  await user.type(await screen.findByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "wrong-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));

  const failure = await screen.findByText("That email and password did not sign you in.");
  const button = screen.getByRole("button", { name: "Sign in" });
  expect(button.compareDocumentPosition(failure) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
    Node.DOCUMENT_POSITION_FOLLOWING,
  );
  expect(screen.getByLabelText("Email")).toBeTruthy();
  expect(screen.queryByLabelText("Search events by name")).toBeNull();
});

test("a successful sign-in opens the events screen", async () => {
  vi.mocked(signIn).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  const user = userEvent.setup();
  render(<App />);

  await user.type(await screen.findByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));

  expect(await screen.findByLabelText("Search events by name")).toBeTruthy();
  expect(screen.queryByLabelText("Password")).toBeNull();
  expect(screen.queryByText("That email and password did not sign you in.")).toBeNull();
});

test("signing out and signing in as another customer shows that customer", async () => {
  vi.mocked(signIn)
    .mockResolvedValueOnce({ email: "ada@example.com", role: "customer" })
    .mockResolvedValueOnce({ email: "grace@example.com", role: "customer" });
  const user = userEvent.setup();
  render(<App />);

  await user.type(await screen.findByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));
  expect(await screen.findByText("Signed in as ada@example.com")).toBeTruthy();

  await user.click(screen.getByRole("button", { name: "Sign out" }));
  expect(vi.mocked(signOut)).toHaveBeenCalled();
  await user.type(await screen.findByLabelText("Email"), "grace@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));

  expect(await screen.findByText("Signed in as grace@example.com")).toBeTruthy();
  expect(screen.queryByText("Signed in as ada@example.com")).toBeNull();
});

test("a refresh of an open event stays on that event", async () => {
  window.history.replaceState(null, "", "/?event=7");
  vi.mocked(currentSession).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  vi.mocked(fetchSeatMap).mockResolvedValueOnce(harborMap);
  vi.mocked(fetchWaitlist).mockResolvedValueOnce(null);
  render(<App />);

  expect(await screen.findByRole("heading", { name: "Harbor Lights" })).toBeTruthy();
  expect(screen.queryByLabelText("Search events by name")).toBeNull();
  expect(vi.mocked(fetchSeatMap)).toHaveBeenCalledWith(7);
  expect(vi.mocked(searchEvents)).not.toHaveBeenCalled();
});

test("opening an event stays on that event after refresh", async () => {
  vi.mocked(signIn).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  vi.mocked(searchEvents).mockResolvedValue([
    {
      id: 7,
      name: "Harbor Lights",
      venueId: 1,
      venueName: "Harbor Hall",
      startsAt: harborMap.startsAt,
      timeZone: harborMap.timeZone,
    },
  ]);
  vi.mocked(fetchSeatMap).mockResolvedValue(harborMap);
  vi.mocked(fetchWaitlist).mockResolvedValue(null);
  const user = userEvent.setup();
  const first = render(<App />);
  await user.type(await screen.findByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));
  await user.type(await screen.findByLabelText("Search events by name"), "Harbor");
  await user.click(screen.getByRole("button", { name: "Search" }));
  await user.click(await screen.findByRole("button", { name: "Open seat map" }));
  expect(await screen.findByRole("heading", { name: "Harbor Lights" })).toBeTruthy();
  expect(window.location.search).toContain("event=7");

  first.unmount();
  vi.mocked(currentSession).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  render(<App />);

  expect(await screen.findByRole("heading", { name: "Harbor Lights" })).toBeTruthy();
  expect(screen.queryByLabelText("Search events by name")).toBeNull();
});

test("a refresh of a hold stays on the hold", async () => {
  window.history.replaceState(null, "", "/?event=7&view=hold");
  vi.mocked(currentSession).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  vi.mocked(fetchSeatMap).mockResolvedValueOnce({ ...harborMap, activeHoldOrderId: 9 });
  vi.mocked(fetchWaitlist).mockResolvedValueOnce(null);
  vi.mocked(fetchSaleTerms).mockResolvedValueOnce({
    groupRateThreshold: 4,
    groupRateAmountCents: 800,
    serviceFeeCents: 250,
  });
  vi.mocked(fetchOrder).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: null,
    tickets: [],
    refunds: [],
    holds: [{ seatId: 1, label: "A1", heldUntil: "2026-06-12T23:10:00.000Z" }],
  });
  render(<App />);

  expect(await screen.findByRole("heading", { name: "Hold" })).toBeTruthy();
  expect(screen.getByText("A1")).toBeTruthy();
  expect(screen.queryByLabelText("Search events by name")).toBeNull();
});

test("a refresh of checkout stays on checkout", async () => {
  window.history.replaceState(null, "", "/?event=7&view=checkout");
  vi.mocked(currentSession).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  vi.mocked(fetchSeatMap).mockResolvedValueOnce({ ...harborMap, activeHoldOrderId: 9 });
  vi.mocked(fetchWaitlist).mockResolvedValueOnce(null);
  vi.mocked(fetchSaleTerms).mockResolvedValueOnce({
    groupRateThreshold: 4,
    groupRateAmountCents: 800,
    serviceFeeCents: 250,
  });
  vi.mocked(fetchOrder).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: null,
    tickets: [],
    refunds: [],
    holds: [{ seatId: 1, label: "A1", heldUntil: "2026-06-12T23:10:00.000Z" }],
  });
  render(<App />);

  expect(await screen.findByRole("heading", { name: "Check out" })).toBeTruthy();
  expect(screen.queryByRole("heading", { name: "Hold" })).toBeNull();
  expect(screen.queryByLabelText("Search events by name")).toBeNull();
});

test("a refresh of tickets stays on tickets", async () => {
  window.history.replaceState(null, "", "/?event=7&view=tickets");
  vi.mocked(currentSession).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  vi.mocked(fetchSeatMap).mockResolvedValueOnce({ ...harborMap, paidOrderIds: [9] });
  vi.mocked(fetchWaitlist).mockResolvedValueOnce(null);
  vi.mocked(fetchOrder).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: { status: "succeeded", amountCents: 5250 },
    tickets: [{ seatId: 1, label: "A1" }],
    refunds: [],
    holds: [],
  });
  render(<App />);

  expect(await screen.findByRole("region", { name: "Tickets" })).toBeTruthy();
  expect(screen.queryByLabelText("Search events by name")).toBeNull();
});

test("a refresh of a search stays on those results", async () => {
  window.history.replaceState(null, "", "/?q=Harbor");
  vi.mocked(currentSession).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  vi.mocked(searchEvents).mockResolvedValueOnce([
    {
      id: 7,
      name: "Harbor Lights",
      venueId: 1,
      venueName: "Harbor Hall",
      startsAt: harborMap.startsAt,
      timeZone: harborMap.timeZone,
    },
  ]);
  render(<App />);

  expect(await screen.findByText("Harbor Lights")).toBeTruthy();
  expect(screen.getByLabelText("Search events by name")).toHaveProperty("value", "Harbor");
  expect(vi.mocked(searchEvents)).toHaveBeenCalledWith("Harbor");
});

test("the signed-out screen is the ticket booking system sign-in card", async () => {
  render(<App />);

  expect(await screen.findByText("Ticket booking system")).toBeTruthy();
  expect(screen.queryByText("Harbor Hall")).toBeNull();
  expect(screen.getByLabelText("Email")).toBeTruthy();
  expect(screen.getByLabelText("Password")).toBeTruthy();
  expect(screen.getAllByRole("button")).toHaveLength(1);

  const button = screen.getByRole("button", { name: "Sign in" });
  const style = getComputedStyle(button);
  expect(Number.parseFloat(style.minHeight)).toBeGreaterThanOrEqual(44);
  expect(Number.parseFloat(style.minWidth)).toBeGreaterThanOrEqual(44);

  const page = document.body.textContent ?? "";
  for (const forbidden of [
    "42°21'30\"N",
    "71°03'10\"W",
    "PORTAL // ACCESS",
    "VENUE TIER",
    "Live Gateway",
    "customer ID",
    "Authorized patron access",
    "Show",
    "HARBOR HALL VENUE SYSTEMS",
    "PROD // ONLINE",
    "encryption",
  ]) {
    expect(page).not.toContain(forbidden);
  }
});
