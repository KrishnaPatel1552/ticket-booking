import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import {
  attachPromoCode,
  checkout,
  fetchOrder,
  fetchSaleTerms,
  fetchSeatMap,
  fetchWaitlist,
  holdSeats,
  releaseOrderHolds,
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
  releaseOrderHolds: vi.fn(),
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
  earlyBirdStartsAt: "2020-01-01T00:00:00.000Z",
  earlyBirdEndsAt: "2030-01-01T00:00:00.000Z",
  sections: [
    {
      name: "Orchestra",
      seats: [
        seat({ id: 1, label: "A1", state: "free", priceCents: 5000, earlyBirdPriceCents: 3500 }),
        seat({ id: 2, label: "A2", state: "free", priceCents: 5000, earlyBirdPriceCents: 3500 }),
        seat({ id: 3, label: "A3", state: "free", priceCents: 5000, earlyBirdPriceCents: 3500 }),
        seat({ id: 4, label: "A4", state: "free", priceCents: 5000, earlyBirdPriceCents: 3500 }),
      ],
    },
  ],
};

async function holdSeatsOnMap(
  map: EventSeatMap = harborMap,
  labels?: string[],
  saleTerms: "loaded" | "missing" = "loaded",
) {
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
  if (saleTerms === "missing") {
    vi.mocked(fetchSaleTerms).mockRejectedValue(new Error("down"));
  } else {
    vi.mocked(fetchSaleTerms).mockResolvedValue({
      groupRateThreshold: 4,
      groupRateAmountCents: 800,
      serviceFeeCents: 250,
    });
  }
  vi.mocked(fetchOrder).mockResolvedValue({
    id: 9,
    eventId: map.id,
    promoCode: null,
    payment: null,
    tickets: [],
    refunds: [],
    holds: [],
  });
  const user = userEvent.setup();
  render(<App />);
  await user.type(screen.getByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));
  await user.type(await screen.findByLabelText("Search events by name"), "Harbor");
  await user.click(screen.getByRole("button", { name: "Search" }));
  await user.click(await screen.findByRole("button", { name: "Open seat map" }));
  await screen.findByRole("heading", { name: map.name });
  const chosen = map.sections
    .flatMap((section) => section.seats)
    .filter((item) => item.state === "free" && (labels == null || labels.includes(item.label)));
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

test("a held order shows the seats, the hold end, and the price in order", async () => {
  await holdSeatsOnMap();

  const order = screen.getByText("Jun 12, 2026, 7:10 PM").closest("section");
  if (order == null) {
    throw new Error("the hold was not on its own screen");
  }
  expect(within(order).getByText("A1")).toBeTruthy();
  expect(within(order).getByText("A2")).toBeTruthy();
  expect(within(order).getByText("A3")).toBeTruthy();
  expect(within(order).getByText("A4")).toBeTruthy();
  expect(screen.queryByRole("grid")).toBeNull();

  const prices = within(order).getByRole("list", { name: "Price" });
  expect(within(prices).getAllByRole("listitem").map((item) => item.textContent)).toEqual([
    "Price tier $200.00",
    "Early-bird $140.00",
    "Ticket total $140.00",
    "Group rate $8.00",
    "Service fee $2.50",
    "Total $134.50",
  ]);

  const checkoutButton = within(order).getByRole("button", { name: "Check out" });
  const checkoutStyle = getComputedStyle(checkoutButton);
  expect(Number.parseFloat(checkoutStyle.minHeight)).toBeGreaterThanOrEqual(44);
  expect(Number.parseFloat(checkoutStyle.minWidth)).toBeGreaterThanOrEqual(44);
  expect(checkoutStyle.backgroundColor).toBe("rgb(14, 165, 233)");
  const cyanButtons = within(order)
    .getAllByRole("button")
    .filter((button) => getComputedStyle(button).backgroundColor === "rgb(14, 165, 233)");
  expect(cyanButtons).toEqual([checkoutButton]);

  expect(getComputedStyle(order).flexDirection).toBe("column");
  expect(getComputedStyle(order).borderColor).toBe("rgb(245, 158, 11)");
  expect(getComputedStyle(order).fontFamily).toContain("Inter");
  expect(getComputedStyle(prices).fontVariantNumeric).toContain("tabular-nums");
  expect(getComputedStyle(screen.getByRole("heading", { name: "Harbor Lights" })).fontFamily).toContain(
    "Plus Jakarta Sans",
  );
  expect(vi.mocked(checkout)).not.toHaveBeenCalled();
  expect(vi.mocked(attachPromoCode)).not.toHaveBeenCalled();
});

test("an attached promo code is shown by its code", async () => {
  const user = await holdSeatsOnMap({
    ...harborMap,
    earlyBirdStartsAt: null,
    earlyBirdEndsAt: null,
    sections: [
      {
        name: "Orchestra",
        seats: [seat({ id: 1, label: "A1", state: "free", priceCents: 5000 })],
      },
    ],
  });

  vi.mocked(attachPromoCode).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: "HARBOR",
    payment: null,
    tickets: [],
    refunds: [],
    holds: [],
  });
  await user.type(screen.getByLabelText("Promo code"), "HARBOR");
  await user.click(screen.getByRole("button", { name: "Attach promo code" }));

  const prices = screen.getByRole("list", { name: "Price" });
  expect(within(prices).getAllByRole("listitem").map((item) => item.textContent)).toEqual([
    "Price tier $50.00",
    "Ticket total $50.00",
    "HARBOR",
    "Service fee $2.50",
    "Total $52.50",
  ]);
  expect(vi.mocked(attachPromoCode)).toHaveBeenCalledWith(9, "HARBOR");
});

test("a rejected promo code shows that it could not be attached", async () => {
  const user = await holdSeatsOnMap({
    ...harborMap,
    earlyBirdStartsAt: null,
    earlyBirdEndsAt: null,
    sections: [
      {
        name: "Orchestra",
        seats: [seat({ id: 1, label: "A1", state: "free", priceCents: 5000 })],
      },
    ],
  });

  vi.mocked(attachPromoCode).mockRejectedValueOnce(new Error("rejected"));
  await user.type(screen.getByLabelText("Promo code"), "NOPE");
  const attach = screen.getByRole("button", { name: "Attach promo code" });
  await user.click(attach);

  const failure = await screen.findByText("That promo code could not be attached.");
  expect(attach.compareDocumentPosition(failure) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
    Node.DOCUMENT_POSITION_FOLLOWING,
  );
  expect(within(screen.getByRole("list", { name: "Price" })).queryByText("NOPE")).toBeNull();
  expect(screen.getByRole("button", { name: "Check out" })).toBeTruthy();
});

test("returning to the seat map shows the held seat so a different free seat can be held", async () => {
  const map: EventSeatMap = {
    ...harborMap,
    earlyBirdStartsAt: null,
    earlyBirdEndsAt: null,
    sections: [
      {
        name: "Orchestra",
        seats: [
          seat({ id: 1, label: "A1", state: "free", priceCents: 5000 }),
          seat({ id: 2, label: "A2", state: "free", priceCents: 4200 }),
        ],
      },
    ],
  };
  const user = await holdSeatsOnMap(map, ["A1"]);
  const loadsBefore = vi.mocked(fetchSeatMap).mock.calls.length;
  vi.mocked(fetchSeatMap).mockResolvedValueOnce({
    ...map,
    sections: [
      {
        name: "Orchestra",
        seats: [
          seat({ id: 1, label: "A1", state: "held", priceCents: 5000 }),
          seat({ id: 2, label: "A2", state: "free", priceCents: 4200 }),
        ],
      },
    ],
  });

  await user.click(screen.getByRole("button", { name: "Change seats" }));

  expect(await screen.findByRole("button", { name: "A1 held" })).toBeTruthy();
  expect(screen.getByRole("heading", { name: "Harbor Lights" })).toBeTruthy();
  expect(screen.getByRole("grid", { name: "Orchestra" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Check out" })).toBeNull();
  expect(screen.queryByText("Jun 12, 2026, 7:10 PM")).toBeNull();
  expect(screen.queryByRole("button", { name: "Open seat map" })).toBeNull();
  expect(vi.mocked(fetchSeatMap).mock.calls.length).toBeGreaterThan(loadsBefore);

  await user.click(screen.getByRole("button", { name: "A2 free" }));
  vi.mocked(holdSeats).mockResolvedValueOnce({
    orderId: 10,
    seatIds: [2],
    heldUntil: "2026-06-12T23:20:00.000Z",
  });
  await user.click(screen.getByRole("button", { name: "Hold seats" }));

  expect(vi.mocked(holdSeats)).toHaveBeenLastCalledWith(7, [2]);
  expect(await screen.findByText("Jun 12, 2026, 7:20 PM")).toBeTruthy();
  const order = screen.getByText("Jun 12, 2026, 7:20 PM").closest("section");
  if (order == null) {
    throw new Error("the new hold was not on its own screen");
  }
  expect(within(order).getByText("A2")).toBeTruthy();
  expect(within(order).queryByText("A1")).toBeNull();
  expect(screen.queryByRole("grid")).toBeNull();
  expect(within(order).getByText("Price tier $42.00")).toBeTruthy();
});

test("a price that does not load says so and keeps the seats and the hold end", async () => {
  await holdSeatsOnMap(harborMap, undefined, "missing");

  const order = screen.getByText("Jun 12, 2026, 7:10 PM").closest("section");
  if (order == null) {
    throw new Error("the hold was not on its own screen");
  }
  expect(within(order).getByText("A1")).toBeTruthy();
  expect(within(order).getByText("A4")).toBeTruthy();
  expect(within(order).getByText("Jun 12, 2026, 7:10 PM")).toBeTruthy();
  const prices = within(order).getByRole("list", { name: "Price" });
  expect(within(prices).getAllByRole("listitem").map((item) => item.textContent)).toEqual([
    "The price did not load.",
  ]);
  expect(within(prices).queryByText(/Service fee/)).toBeNull();
  expect(within(prices).queryByText(/^Total/)).toBeNull();
});

test("releasing a hold from the hold screen returns to the seat map with the seat free", async () => {
  const user = await holdSeatsOnMap(harborMap, ["A1"]);
  vi.mocked(releaseOrderHolds).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: null,
    tickets: [],
    refunds: [],
    holds: [],
  });
  vi.mocked(fetchSeatMap).mockResolvedValueOnce(harborMap);

  await user.click(screen.getByRole("button", { name: "Release hold" }));

  expect(vi.mocked(releaseOrderHolds)).toHaveBeenCalledWith(9);
  expect(await screen.findByRole("button", { name: "A1 free" })).toBeTruthy();
  expect(document.querySelector("section.hold-screen")).toBeNull();
});

test("reopening the seat map with an active hold offers Check out, Release hold, and Search events", async () => {
  const user = await holdSeatsOnMap(harborMap, ["A1"]);
  vi.mocked(fetchSeatMap).mockResolvedValueOnce({
    ...harborMap,
    activeHoldOrderId: 9,
    sections: [
      {
        name: "Orchestra",
        seats: [
          seat({ id: 1, label: "A1", state: "held", yours: true }),
          seat({ id: 2, label: "A2", state: "free" }),
        ],
      },
    ],
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

  await user.click(screen.getByRole("button", { name: "Change seats" }));

  const banner = await screen.findByRole("region", { name: "Active hold" });
  expect(within(banner).getByRole("button", { name: "Check out" })).toBeTruthy();
  expect(within(banner).getByRole("button", { name: "Release hold" })).toBeTruthy();

  await user.click(screen.getByRole("button", { name: "Search events" }));
  expect(await screen.findByLabelText("Search events by name")).toBeTruthy();
});

