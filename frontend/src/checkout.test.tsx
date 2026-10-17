import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import {
  checkout,
  fetchOrder,
  fetchSaleTerms,
  fetchSeatMap,
  fetchWaitlist,
  holdSeats,
  searchEvents,
  signIn,
  type EventSeatMap,
  type OrderView,
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
        seat({ id: 1, label: "A1", state: "free", priceCents: 5000 }),
        seat({ id: 2, label: "A2", state: "free", priceCents: 5000 }),
      ],
    },
  ],
};

function emptyOrder(tickets: OrderView["tickets"] = []): OrderView {
  return {
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: null,
    tickets,
    refunds: [],
    holds: [],
  };
}

async function holdSeatsOnMap(map: EventSeatMap = harborMap, initialOrder: OrderView = emptyOrder()) {
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
  vi.mocked(fetchWaitlist).mockResolvedValue(null);
  vi.mocked(fetchSaleTerms).mockResolvedValue({
    groupRateThreshold: 4,
    groupRateAmountCents: 800,
    serviceFeeCents: 250,
  });
  vi.mocked(fetchOrder).mockResolvedValue(initialOrder);
  const user = userEvent.setup();
  render(<App />);
  await user.type(screen.getByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));
  await user.type(await screen.findByLabelText("Search events by name"), "Harbor");
  await user.click(screen.getByRole("button", { name: "Search" }));
  await user.click(await screen.findByRole("button", { name: "Open seat map" }));
  await screen.findByRole("heading", { name: map.name });
  const chosen = map.sections.flatMap((section) => section.seats).filter((item) => item.state === "free");
  for (const seatView of chosen) {
    await user.click(screen.getByRole("button", { name: `${seatView.label} free` }));
  }
  vi.mocked(holdSeats).mockResolvedValueOnce({
    orderId: 9,
    seatIds: chosen.map((item) => item.id),
    heldUntil: "2026-06-12T23:10:00.000Z",
  });
  await user.click(screen.getByRole("button", { name: "Hold seats" }));
  await screen.findByText("Jun 12, 2026, 7:10 PM");
  return user;
}

test("checkout repeats the order summary and offers a scripted payment result before paying", async () => {
  const user = await holdSeatsOnMap();

  await user.click(screen.getByRole("button", { name: "Check out" }));

  expect(document.querySelector("section.hold-screen")).toBeNull();
  const checkoutScreen = document.querySelector("section.checkout-screen");
  if (!(checkoutScreen instanceof HTMLElement)) {
    throw new Error("checkout was not on its own screen");
  }
  expect(within(checkoutScreen).getByText("A1")).toBeTruthy();
  expect(within(checkoutScreen).getByText("A2")).toBeTruthy();
  expect(within(checkoutScreen).getByText("Jun 12, 2026, 7:10 PM")).toBeTruthy();
  const prices = within(checkoutScreen).getByRole("list", { name: "Price" });
  expect(within(prices).getAllByRole("listitem").map((item) => item.textContent)).toEqual([
    "Price tier $100.00",
    "Ticket total $100.00",
    "Service fee $2.50",
    "Total $102.50",
  ]);
  expect(within(checkoutScreen).getByRole("radio", { name: "success" })).toBeTruthy();
  expect(within(checkoutScreen).getByRole("radio", { name: "decline" })).toBeTruthy();
  expect(within(checkoutScreen).getByRole("radio", { name: "timeout" })).toBeTruthy();
  expect(within(checkoutScreen).getByRole("radio", { name: "unknown" })).toBeTruthy();
  expect(within(checkoutScreen).queryByLabelText(/card/i)).toBeNull();
  expect(within(checkoutScreen).queryByRole("button", { name: /reconcile/i })).toBeNull();
  expect(screen.queryByRole("checkbox")).toBeNull();

  const pay = within(checkoutScreen).getByRole("button", { name: "Check out" });
  const payStyle = getComputedStyle(pay);
  expect(Number.parseFloat(payStyle.minHeight)).toBeGreaterThanOrEqual(44);
  expect(Number.parseFloat(payStyle.minWidth)).toBeGreaterThanOrEqual(44);
  expect(payStyle.backgroundColor).toBe("rgb(14, 165, 233)");
  const cyanButtons = within(checkoutScreen)
    .getAllByRole("button")
    .filter((button) => getComputedStyle(button).backgroundColor === "rgb(14, 165, 233)");
  expect(cyanButtons).toEqual([pay]);
  expect(getComputedStyle(checkoutScreen).display).toBe("flex");
  expect(getComputedStyle(checkoutScreen).flexDirection).toBe("column");
  expect(getComputedStyle(checkoutScreen).fontFamily).toContain("Inter");
  expect(payStyle.animationName).toBe("none");
  expect(getComputedStyle(checkoutScreen).animationName).toBe("none");
  expect(getComputedStyle(screen.getByRole("heading", { name: "Harbor Lights" })).fontFamily).toContain(
    "Plus Jakarta Sans",
  );
  expect(getComputedStyle(document.querySelector(".events-screen")!).backgroundColor).toBe("rgb(10, 17, 24)");

  await user.click(within(checkoutScreen).getByRole("button", { name: "Return to order" }));

  expect(document.querySelector("section.checkout-screen")).toBeNull();
  expect(document.querySelector("section.hold-screen")).not.toBeNull();
  expect(screen.getByText("A1")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Check out" })).toBeTruthy();
  expect(vi.mocked(checkout)).not.toHaveBeenCalled();
});

test("a succeeded payment opens the tickets for those seats", async () => {
  const user = await holdSeatsOnMap();
  await user.click(screen.getByRole("button", { name: "Check out" }));
  vi.mocked(fetchOrder).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: { status: "succeeded", amountCents: 10250 },
    tickets: [
      { seatId: 1, label: "A1" },
      { seatId: 2, label: "A2" },
    ],
    refunds: [{ status: "succeeded", amountCents: 100 }],
    holds: [],
  });

  await user.click(screen.getByRole("radio", { name: "success" }));
  await user.click(screen.getByRole("button", { name: "Check out" }));

  const tickets = await screen.findByRole("region", { name: "Tickets" });
  expect(within(tickets).getByText("A1")).toBeTruthy();
  expect(within(tickets).getByText("A2")).toBeTruthy();
  expect(within(tickets).getByText("succeeded")).toBeTruthy();
  expect(within(tickets).getByText("$102.50")).toBeTruthy();
  expect(within(tickets).queryByRole("checkbox")).toBeNull();
  expect(within(tickets).queryByText(/refund/i)).toBeNull();
  expect(document.querySelector("section.checkout-screen")).toBeNull();
  expect(document.querySelector("section.hold-screen")).toBeNull();
  expect(vi.mocked(checkout)).toHaveBeenCalledWith(9, [1, 2], "success");
});

test("a declined payment stays on checkout, shows declined, and reloads the seat map", async () => {
  const user = await holdSeatsOnMap();
  const loadsBefore = vi.mocked(fetchSeatMap).mock.calls.length;
  await user.click(screen.getByRole("button", { name: "Check out" }));
  vi.mocked(fetchOrder).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: { status: "declined", amountCents: 10250 },
    tickets: [],
    refunds: [],
    holds: [],
  });

  await user.click(screen.getByRole("radio", { name: "decline" }));
  await user.click(screen.getByRole("button", { name: "Check out" }));

  const checkoutScreen = document.querySelector("section.checkout-screen");
  expect(checkoutScreen).not.toBeNull();
  expect(await within(checkoutScreen as HTMLElement).findByText("declined")).toBeTruthy();
  expect(screen.queryByRole("region", { name: "Tickets" })).toBeNull();
  expect(vi.mocked(checkout)).toHaveBeenCalledWith(9, [1, 2], "decline");
  expect(vi.mocked(fetchSeatMap).mock.calls.length).toBeGreaterThan(loadsBefore);
});

test("an unknown payment stays on checkout, shows unknown, and leaves the holds", async () => {
  const user = await holdSeatsOnMap();
  const loadsBefore = vi.mocked(fetchSeatMap).mock.calls.length;
  await user.click(screen.getByRole("button", { name: "Check out" }));
  vi.mocked(fetchOrder).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: { status: "unknown", amountCents: 10250 },
    tickets: [],
    refunds: [],
    holds: [
      { seatId: 1, label: "A1", heldUntil: "2026-06-12T23:10:00.000Z" },
      { seatId: 2, label: "A2", heldUntil: "2026-06-12T23:10:00.000Z" },
    ],
  });

  await user.click(screen.getByRole("radio", { name: "unknown" }));
  await user.click(screen.getByRole("button", { name: "Check out" }));

  const checkoutScreen = document.querySelector("section.checkout-screen");
  if (!(checkoutScreen instanceof HTMLElement)) {
    throw new Error("unknown left checkout");
  }
  expect((await within(checkoutScreen).findByRole("status")).textContent).toBe("unknown");
  expect(within(checkoutScreen).getByText("A1")).toBeTruthy();
  expect(within(checkoutScreen).getByText("A2")).toBeTruthy();
  expect(screen.queryByRole("region", { name: "Tickets" })).toBeNull();
  expect(screen.queryByRole("grid")).toBeNull();
  expect(vi.mocked(checkout)).toHaveBeenCalledWith(9, [1, 2], "unknown");
  expect(vi.mocked(fetchSeatMap).mock.calls.length).toBe(loadsBefore);
});

test("a checkout that does not complete shows that under Check out", async () => {
  const user = await holdSeatsOnMap();
  await user.click(screen.getByRole("button", { name: "Check out" }));
  vi.mocked(checkout).mockRejectedValueOnce(new Error("down"));

  await user.click(screen.getByRole("radio", { name: "success" }));
  const pay = screen.getByRole("button", { name: "Check out" });
  await user.click(pay);

  const failure = await screen.findByText("Checkout did not complete.");
  expect(pay.compareDocumentPosition(failure) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
    Node.DOCUMENT_POSITION_FOLLOWING,
  );
  expect(failure.closest("section.checkout-screen")).not.toBeNull();
  expect(screen.queryByRole("region", { name: "Tickets" })).toBeNull();
  expect(screen.queryByRole("status")).toBeNull();
});

test("checking out removes the earlier order link", async () => {
  const user = await holdSeatsOnMap(harborMap, emptyOrder([{ seatId: 8, label: "B4" }]));
  expect(screen.getByRole("link", { name: "Tickets B4" })).toBeTruthy();

  await user.click(screen.getByRole("button", { name: "Check out" }));
  vi.mocked(fetchOrder).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: { status: "unknown", amountCents: 10250 },
    tickets: [],
    refunds: [],
    holds: [
      { seatId: 1, label: "A1", heldUntil: "2026-06-12T23:10:00.000Z" },
      { seatId: 2, label: "A2", heldUntil: "2026-06-12T23:10:00.000Z" },
    ],
  });
  await user.click(screen.getByRole("radio", { name: "unknown" }));
  await user.click(screen.getByRole("button", { name: "Check out" }));
  await screen.findByRole("status");
  await user.click(screen.getByRole("button", { name: "Return to order" }));

  expect(screen.queryByRole("link", { name: "Tickets B4" })).toBeNull();
  expect(document.querySelector("section.hold-screen")).not.toBeNull();
  expect(screen.getByText("A1")).toBeTruthy();
  expect(screen.getByText("A2")).toBeTruthy();
});

test("a timeout result shows the order's unknown payment and leaves the holds", async () => {
  const user = await holdSeatsOnMap();
  const loadsBefore = vi.mocked(fetchSeatMap).mock.calls.length;
  await user.click(screen.getByRole("button", { name: "Check out" }));
  vi.mocked(fetchOrder).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: { status: "unknown", amountCents: 5100 },
    tickets: [],
    refunds: [],
    holds: [{ seatId: 1, label: "A1", heldUntil: "2026-06-12T23:10:00.000Z" }],
  });

  await user.click(screen.getByRole("radio", { name: "timeout" }));
  await user.click(screen.getByRole("button", { name: "Check out" }));

  expect((await screen.findByRole("status")).textContent).toBe("unknown");
  expect(screen.queryByRole("region", { name: "Tickets" })).toBeNull();
  expect(screen.getByText("A1")).toBeTruthy();
  expect(vi.mocked(checkout)).toHaveBeenCalledWith(9, [1, 2], "timeout");
  expect(vi.mocked(fetchSeatMap).mock.calls.length).toBe(loadsBefore);
});

test("a pending payment stays on checkout and shows pending", async () => {
  const user = await holdSeatsOnMap();
  await user.click(screen.getByRole("button", { name: "Check out" }));
  vi.mocked(fetchOrder).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: { status: "pending", amountCents: 10250 },
    tickets: [],
    refunds: [],
    holds: [],
  });

  await user.click(screen.getByRole("radio", { name: "unknown" }));
  await user.click(screen.getByRole("button", { name: "Check out" }));

  expect((await screen.findByRole("status")).textContent).toBe("pending");
  expect(document.querySelector("section.checkout-screen")).not.toBeNull();
  expect(screen.queryByRole("region", { name: "Tickets" })).toBeNull();
});
