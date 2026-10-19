import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { fetchSeatMap, fetchWaitlist, searchEvents, signIn, type EventSummary } from "./api";
import "./index.css";

const harborLights: EventSummary = {
  id: 7,
  name: "Harbor Lights",
  venueId: 1,
  venueName: "Harbor Hall",
  startsAt: "2026-06-12T23:00:00.000Z",
  timeZone: "America/New_York",
};

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

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

async function signInAsCustomer() {
  vi.mocked(signIn).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  const user = userEvent.setup();
  render(<App />);
  await user.type(await screen.findByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));
  await screen.findByLabelText("Search events by name");
  return user;
}

test("an empty search asks for an event name and does not call the client", async () => {
  const user = await signInAsCustomer();

  await user.click(screen.getByRole("button", { name: "Search" }));

  const search = screen.getByLabelText("Search events by name");
  const message = screen.getByText("Enter an event name.");
  expect(search.closest("form")).toBe(message.closest("form"));
  expect(vi.mocked(searchEvents)).not.toHaveBeenCalled();
});

test("a search with no events shows that nothing matched", async () => {
  vi.mocked(searchEvents).mockResolvedValueOnce([]);
  const user = await signInAsCustomer();

  await user.type(screen.getByLabelText("Search events by name"), "No Such Night");
  await user.click(screen.getByRole("button", { name: "Search" }));

  const message = await screen.findByText("No events match that name.");
  const search = screen.getByLabelText("Search events by name");
  expect(search.closest("form")).toBe(message.closest("form"));
  expect(screen.queryByRole("button", { name: "Open seat map" })).toBeNull();
  expect(screen.queryByText("Harbor Lights")).toBeNull();
});

test("a failed search shows that the search did not complete", async () => {
  vi.mocked(searchEvents).mockRejectedValueOnce(new Error("offline"));
  const user = await signInAsCustomer();

  await user.type(screen.getByLabelText("Search events by name"), "Harbor Lights");
  await user.click(screen.getByRole("button", { name: "Search" }));

  const message = await screen.findByText("The search did not complete.");
  const search = screen.getByLabelText("Search events by name");
  expect(search.closest("form")).toBe(message.closest("form"));
  expect(screen.queryByRole("button", { name: "Open seat map" })).toBeNull();
});

test("a search lists only the events it returns on the harbor events screen", async () => {
  vi.mocked(searchEvents).mockResolvedValueOnce([harborLights]);
  const user = await signInAsCustomer();

  const header = screen.getByRole("banner");
  expect(header.textContent).toContain("Ticket Booking");
  expect(header.textContent).toContain("ada@example.com");
  expect(header.textContent).not.toContain("Harbor Hall");
  expect(screen.queryByRole("link", { name: "My Orders" })).toBeNull();
  expect(screen.queryByRole("link", { name: "Tickets" })).toBeNull();
  expect(screen.queryByRole("link", { name: "Venue info" })).toBeNull();
  expect(screen.queryByRole("button", { name: "My Orders" })).toBeNull();
  expect(screen.queryByRole("button", { name: "Tickets" })).toBeNull();
  expect(screen.queryByRole("button", { name: "Venue info" })).toBeNull();
  expect(screen.queryByRole("combobox")).toBeNull();
  expect(screen.queryByRole("img")).toBeNull();
  expect(screen.queryByText("My Orders")).toBeNull();
  expect(screen.queryByText("Tickets")).toBeNull();
  expect(screen.queryByText("Venue info")).toBeNull();

  await user.type(screen.getByLabelText("Search events by name"), "Harbor");
  const searchButton = screen.getByRole("button", { name: "Search" });
  expect(getComputedStyle(searchButton).backgroundColor).toBe("rgb(14, 165, 233)");
  await user.click(searchButton);

  expect(await screen.findByText("Harbor Lights")).toBeTruthy();
  expect(screen.getByText("Harbor Hall")).toBeTruthy();
  expect(screen.getByText("Jun 12, 2026, 7:00 PM")).toBeTruthy();
  expect(screen.queryByText("River Night")).toBeNull();
  expect(screen.getAllByRole("button", { name: "Open seat map" })).toHaveLength(1);

  const openSeatMap = screen.getByRole("button", { name: "Open seat map" });
  const openStyle = getComputedStyle(openSeatMap);
  expect(Number.parseFloat(openStyle.minHeight)).toBeGreaterThanOrEqual(44);
  expect(Number.parseFloat(openStyle.minWidth)).toBeGreaterThanOrEqual(44);

  const list = screen.getByRole("list");
  expect(getComputedStyle(list).fontFamily).toContain("Inter");

  const screenColumn = screen.getByRole("main");
  const columnStyle = getComputedStyle(screenColumn);
  expect(columnStyle.flexDirection).toBe("column");
  expect(columnStyle.backgroundColor).toBe("rgb(10, 17, 24)");

  expect(getComputedStyle(openSeatMap).backgroundColor).toBe("rgb(14, 165, 233)");
});

test("opening an event leaves the results and shows that event", async () => {
  vi.mocked(searchEvents).mockResolvedValueOnce([
    harborLights,
    {
      id: 8,
      name: "River Night",
      venueId: 2,
      venueName: "River House",
      startsAt: "2026-07-01T23:00:00.000Z",
      timeZone: "America/New_York",
    },
  ]);
  vi.mocked(fetchSeatMap).mockResolvedValueOnce({
    id: harborLights.id,
    name: harborLights.name,
    venueName: harborLights.venueName,
    startsAt: harborLights.startsAt,
    timeZone: harborLights.timeZone,
    earlyBirdStartsAt: null,
    earlyBirdEndsAt: null,
    sections: [],
  });
  vi.mocked(fetchWaitlist).mockResolvedValueOnce(null);
  const user = await signInAsCustomer();

  await user.type(screen.getByLabelText("Search events by name"), "Harbor");
  await user.click(screen.getByRole("button", { name: "Search" }));
  const harborResult = (await screen.findByText("Harbor Lights")).closest("li");
  if (harborResult == null) {
    throw new Error("Harbor Lights was not listed");
  }
  await user.click(within(harborResult).getByRole("button", { name: "Open seat map" }));

  expect(screen.queryByRole("button", { name: "Open seat map" })).toBeNull();
  expect(screen.queryByText("River Night")).toBeNull();
  const eventName = await screen.findByRole("heading", { name: "Harbor Lights" });
  expect(screen.getByText(/Harbor Hall/)).toBeTruthy();
  expect(screen.getByText(/Jun 12, 2026, 7:00 PM/)).toBeTruthy();
  expect(getComputedStyle(eventName).fontFamily).toContain("Plus Jakarta Sans");
});
