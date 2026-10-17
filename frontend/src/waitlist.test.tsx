import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import {
  fetchOrder,
  fetchSeatMap,
  fetchWaitlist,
  joinWaitlist,
  searchEvents,
  signIn,
  type EventSeatMap,
  type SeatView,
} from "./api";
import "./index.css";

vi.mock("./api", () => ({
  signIn: vi.fn(),
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

function seat(partial: Pick<SeatView, "id" | "label" | "state"> & Partial<SeatView>): SeatView {
  return {
    priceCents: 5000,
    earlyBirdPriceCents: null,
    ...partial,
  };
}

const soldOut: EventSeatMap = {
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
        seat({ id: 1, label: "A1", state: "held" }),
        seat({ id: 2, label: "A2", state: "ticket" }),
      ],
    },
    {
      name: "Balcony",
      seats: [seat({ id: 3, label: "B1", state: "blocked" })],
    },
  ],
};

async function openMap(map: EventSeatMap) {
  vi.mocked(signIn).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  vi.mocked(searchEvents).mockResolvedValueOnce([
    {
      id: map.id,
      name: map.name,
      venueId: 1,
      venueName: map.venueName,
      startsAt: map.startsAt,
      timeZone: map.timeZone,
    },
  ]);
  vi.mocked(fetchSeatMap).mockResolvedValue(map);
  const user = userEvent.setup();
  render(<App />);
  await user.type(screen.getByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));
  await user.type(await screen.findByLabelText("Search events by name"), "Harbor");
  await user.click(screen.getByRole("button", { name: "Search" }));
  await user.click(await screen.findByRole("button", { name: "Open seat map" }));
  await screen.findByRole("heading", { name: map.name });
  return user;
}

test("Join waitlist appears only when no seat is free and this customer has no waitlist entry", async () => {
  vi.mocked(fetchWaitlist).mockResolvedValue(null);
  await openMap(soldOut);

  expect(screen.getByRole("button", { name: "Join waitlist" })).toBeTruthy();
  expect(screen.queryByText(/You are number/)).toBeNull();

  cleanup();
  vi.clearAllMocks();

  vi.mocked(fetchWaitlist).mockResolvedValue({ place: 2, orderId: null });
  await openMap(soldOut);

  expect(await screen.findByText("You are number 2 in line.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Join waitlist" })).toBeNull();

  cleanup();
  vi.clearAllMocks();

  vi.mocked(fetchWaitlist).mockResolvedValue(null);
  await openMap({
    ...soldOut,
    sections: [
      {
        name: "Orchestra",
        seats: [seat({ id: 1, label: "A1", state: "free" }), seat({ id: 2, label: "A2", state: "ticket" })],
      },
    ],
  });

  expect(screen.queryByRole("button", { name: "Join waitlist" })).toBeNull();
});

test("joining the waitlist shows a place in line", async () => {
  vi.mocked(fetchWaitlist).mockResolvedValue(null);
  vi.mocked(joinWaitlist).mockResolvedValue({ place: 4, orderId: null });
  const user = await openMap(soldOut);

  await user.click(screen.getByRole("button", { name: "Join waitlist" }));

  expect(await screen.findByText("You are number 4 in line.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Join waitlist" })).toBeNull();
  expect(vi.mocked(joinWaitlist)).toHaveBeenCalledWith(7);
});

test("a rejected join shows that the waitlist did not accept that request", async () => {
  vi.mocked(fetchWaitlist).mockResolvedValue(null);
  vi.mocked(joinWaitlist).mockRejectedValue(new Error("rejected"));
  const user = await openMap(soldOut);

  const join = screen.getByRole("button", { name: "Join waitlist" });
  await user.click(join);

  const failure = await screen.findByText("The waitlist did not accept that request.");
  expect(join.compareDocumentPosition(failure) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
    Node.DOCUMENT_POSITION_FOLLOWING,
  );
  expect(screen.queryByText(/You are number/)).toBeNull();
});

test("a waitlist that does not load shows that failure", async () => {
  vi.mocked(fetchWaitlist).mockRejectedValue(new Error("down"));
  await openMap(soldOut);

  expect(await screen.findByText("The waitlist did not load.")).toBeTruthy();
  expect(screen.queryByText(/You are number/)).toBeNull();
});

test("an offered hold is named on the seat map and opens the order screen", async () => {
  vi.mocked(fetchWaitlist).mockResolvedValue({ place: 1, orderId: 44 });
  vi.mocked(fetchOrder).mockResolvedValue({
    id: 44,
    eventId: 7,
    promoCode: null,
    payment: null,
    tickets: [],
    refunds: [],
    holds: [
      { seatId: 1, label: "A1", heldUntil: "2026-06-12T23:15:00.000Z" },
      { seatId: 2, label: "A2", heldUntil: "2026-06-12T23:15:00.000Z" },
    ],
  });
  const user = await openMap(soldOut);

  expect(screen.queryByRole("button", { name: "Join waitlist" })).toBeNull();
  const offer = await screen.findByRole("link", { name: "Held for you: A1, A2" });
  expect(document.querySelector("section.hold-screen")).toBeNull();

  await user.click(offer);

  const holdScreen = document.querySelector("section.hold-screen");
  expect(holdScreen).not.toBeNull();
  expect(holdScreen?.textContent).toContain("A1");
  expect(holdScreen?.textContent).toContain("A2");
  expect(holdScreen?.textContent).toContain("Jun 12, 2026, 7:15 PM");
  expect(screen.queryByRole("grid")).toBeNull();
  expect(screen.getByRole("button", { name: "Check out" })).toBeTruthy();
  expect(screen.getByLabelText(/promo code/i)).toBeTruthy();
});

test("Join waitlist is the cyan action when no seat is free", async () => {
  vi.mocked(fetchWaitlist).mockResolvedValue(null);
  await openMap(soldOut);

  const join = screen.getByRole("button", { name: "Join waitlist" });
  const hold = screen.getByRole("button", { name: "Hold seats" });
  expect(getComputedStyle(join).backgroundColor).toBe("rgb(14, 165, 233)");
  expect(getComputedStyle(hold).backgroundColor).not.toBe("rgb(14, 165, 233)");
});

test("Join waitlist is one column and its target is at least 44px", async () => {
  vi.mocked(fetchWaitlist).mockResolvedValue(null);
  await openMap(soldOut);

  const join = screen.getByRole("button", { name: "Join waitlist" });
  const column = join.parentElement;
  if (column == null) {
    throw new Error("Join waitlist is not in a column");
  }
  const columnStyle = getComputedStyle(column);
  expect(columnStyle.display).toBe("flex");
  expect(columnStyle.flexDirection).toBe("column");

  const joinStyle = getComputedStyle(join);
  expect(Number.parseFloat(joinStyle.minHeight)).toBeGreaterThanOrEqual(44);
  expect(Number.parseFloat(joinStyle.minWidth)).toBeGreaterThanOrEqual(44);
  expect(joinStyle.backgroundColor).toBe("rgb(14, 165, 233)");
  expect(joinStyle.animationName).toBe("none");
  expect(columnStyle.animationName).toBe("none");
  expect(vi.mocked(fetchWaitlist)).toHaveBeenCalledTimes(1);
});
