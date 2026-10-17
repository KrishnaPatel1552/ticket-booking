import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { fetchSeatMap, fetchWaitlist, holdSeats, searchEvents, signIn, type EventSeatMap, type SeatView } from "./api";
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
        seat({ id: 1, label: "A1", state: "free" }),
        seat({ id: 2, label: "A2", state: "held" }),
        seat({ id: 3, label: "A3", state: "blocked" }),
      ],
    },
    {
      name: "Balcony",
      seats: [seat({ id: 4, label: "B1", state: "ticket" })],
    },
  ],
};

async function openHarborMap(map: EventSeatMap = harborMap) {
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

function markKind(seatButton: HTMLElement): string {
  const mark = seatButton.querySelector(".mark");
  if (mark == null) {
    return "";
  }
  return mark.getAttribute("data-mark") ?? "";
}

test("each seat shows its label, one word, and a distinct mark, with a legend and the stage", async () => {
  const user = await openHarborMap();

  const freeSeat = screen.getByRole("button", { name: "A1 free" });
  expect(within(freeSeat).getByText("A1")).toBeTruthy();
  expect(within(freeSeat).getByText("free")).toBeTruthy();
  expect(markKind(freeSeat)).toBe("ring");

  const heldSeat = screen.getByRole("button", { name: "A2 held" });
  expect(within(heldSeat).getByText("held")).toBeTruthy();
  expect(markKind(heldSeat)).toBe("amber");

  const blockedSeat = screen.getByRole("button", { name: "A3 blocked" });
  expect(within(blockedSeat).getByText("blocked")).toBeTruthy();
  expect(markKind(blockedSeat)).toBe("hash");

  const ticketSeat = screen.getByRole("button", { name: "B1 ticket" });
  expect(within(ticketSeat).getByText("ticket")).toBeTruthy();
  expect(markKind(ticketSeat)).toBe("slash");

  await user.click(freeSeat);
  const selectedSeat = screen.getByRole("button", { name: "A1 free" });
  expect(within(selectedSeat).getByText("selected")).toBeTruthy();
  expect(within(selectedSeat).queryByText("free")).toBeNull();
  expect(markKind(selectedSeat)).toBe("check");

  const legend = screen.getByRole("list", { name: "Legend" });
  for (const word of ["free", "held", "blocked", "ticket", "selected"]) {
    const item = within(legend).getByText(word).closest("li");
    if (item == null) {
      throw new Error(`${word} is missing from the legend`);
    }
    expect(item.querySelector(".mark")?.getAttribute("data-mark")).toBeTruthy();
  }
  expect(screen.getByText("Stage")).toBeTruthy();
});

test("a free or selected seat shows the early-bird price, and one line states the price tier after the window", async () => {
  const user = await openHarborMap({
    ...harborMap,
    earlyBirdStartsAt: "2020-01-01T00:00:00.000Z",
    earlyBirdEndsAt: "2030-01-01T00:00:00.000Z",
    sections: [
      {
        name: "Orchestra",
        seats: [
          seat({ id: 1, label: "A1", state: "free", priceCents: 5000, earlyBirdPriceCents: 4000 }),
          seat({ id: 2, label: "A2", state: "held", priceCents: 5000, earlyBirdPriceCents: 4000 }),
        ],
      },
    ],
  });

  const freeSeat = screen.getByRole("button", { name: "A1 free" });
  expect(within(freeSeat).getByText("$40.00")).toBeTruthy();
  expect(within(freeSeat).queryByText("$50.00")).toBeNull();
  expect(within(screen.getByRole("button", { name: "A2 held" })).queryByText("$40.00")).toBeNull();
  expect(
    screen.getByText("Early-bird ends Dec 31, 2029, 7:00 PM. The price tier is $50.00 after that."),
  ).toBeTruthy();

  await user.click(freeSeat);
  expect(within(screen.getByRole("button", { name: "A1 free" })).getByText("$40.00")).toBeTruthy();
});

test("outside the early-bird window a free seat shows the price tier", async () => {
  await openHarborMap({
    ...harborMap,
    earlyBirdStartsAt: "2019-01-01T00:00:00.000Z",
    earlyBirdEndsAt: "2020-06-01T00:00:00.000Z",
    sections: [
      {
        name: "Orchestra",
        seats: [seat({ id: 1, label: "A1", state: "free", priceCents: 5000, earlyBirdPriceCents: 4000 })],
      },
    ],
  });

  expect(within(screen.getByRole("button", { name: "A1 free" })).getByText("$50.00")).toBeTruthy();
  expect(
    screen.getByText("Early-bird ends May 31, 2020, 8:00 PM. The price tier is $50.00 after that."),
  ).toBeTruthy();
});

test("with no early-bird window the line and the seat show the price tier", async () => {
  await openHarborMap({
    ...harborMap,
    earlyBirdStartsAt: null,
    earlyBirdEndsAt: null,
    sections: [
      {
        name: "Orchestra",
        seats: [seat({ id: 1, label: "A1", state: "free", priceCents: 3200, earlyBirdPriceCents: 2000 })],
      },
    ],
  });

  expect(within(screen.getByRole("button", { name: "A1 free" })).getByText("$32.00")).toBeTruthy();
  expect(screen.getByText("The price tier is $32.00.")).toBeTruthy();
  expect(screen.queryByText(/Early-bird ends/)).toBeNull();
});

test("the selection shows the count and the ticket total, without a group rate, promo code, or service fee", async () => {
  const user = await openHarborMap({
    ...harborMap,
    earlyBirdStartsAt: "2020-01-01T00:00:00.000Z",
    earlyBirdEndsAt: "2030-01-01T00:00:00.000Z",
    sections: [
      {
        name: "Orchestra",
        seats: [
          seat({ id: 1, label: "A1", state: "free", priceCents: 5000, earlyBirdPriceCents: 4000 }),
          seat({ id: 2, label: "A2", state: "free", priceCents: 5000, earlyBirdPriceCents: 4000 }),
          seat({ id: 3, label: "A3", state: "free", priceCents: 5000, earlyBirdPriceCents: 4000 }),
          seat({ id: 4, label: "A4", state: "free", priceCents: 5000, earlyBirdPriceCents: 4000 }),
        ],
      },
    ],
  });

  await user.click(screen.getByRole("button", { name: "A1 free" }));
  await user.click(screen.getByRole("button", { name: "A2 free" }));
  await user.click(screen.getByRole("button", { name: "A3 free" }));
  await user.click(screen.getByRole("button", { name: "A4 free" }));

  expect(screen.getByText("4 selected")).toBeTruthy();
  expect(screen.getByText("Ticket total $160.00")).toBeTruthy();
  expect(screen.queryByText(/group rate/i)).toBeNull();
  expect(screen.queryByLabelText(/promo code/i)).toBeNull();
  expect(screen.queryByText(/promo code/i)).toBeNull();
  expect(screen.queryByText(/service fee/i)).toBeNull();
  expect(screen.queryByRole("button", { name: "Check out" })).toBeNull();
  expect(screen.queryByRole("button", { name: /Cancel/ })).toBeNull();
});

test("Hold seats waits for a free seat, then a successful hold opens the order screen", async () => {
  const user = await openHarborMap({
    ...harborMap,
    sections: [
      {
        name: "Orchestra",
        seats: [
          seat({ id: 1, label: "A1", state: "free", priceCents: 5000 }),
          seat({ id: 2, label: "A2", state: "free", priceCents: 5000 }),
          seat({ id: 3, label: "A3", state: "held", priceCents: 5000 }),
        ],
      },
    ],
  });

  const hold = screen.getByRole("button", { name: "Hold seats" });
  expect(hold.hasAttribute("disabled")).toBe(true);
  expect(screen.getByText("Select free seats to hold.")).toBeTruthy();
  expect(vi.mocked(holdSeats)).not.toHaveBeenCalled();

  await user.click(screen.getByRole("button", { name: "A3 held" }));
  expect(hold.hasAttribute("disabled")).toBe(true);

  await user.click(screen.getByRole("button", { name: "A1 free" }));
  await user.click(screen.getByRole("button", { name: "A2 free" }));
  expect(hold.hasAttribute("disabled")).toBe(false);

  vi.mocked(holdSeats).mockResolvedValueOnce({
    orderId: 9,
    seatIds: [1, 2],
    heldUntil: "2026-06-12T23:10:00.000Z",
  });
  await user.click(hold);

  expect(vi.mocked(holdSeats)).toHaveBeenCalledWith(7, [1, 2]);
  expect(await screen.findByText("Jun 12, 2026, 7:10 PM")).toBeTruthy();
  expect(screen.getByText("A1")).toBeTruthy();
  expect(screen.getByText("A2")).toBeTruthy();
  expect(screen.queryByRole("grid")).toBeNull();
  expect(screen.queryByRole("button", { name: "Hold seats" })).toBeNull();
  expect(screen.queryByRole("button", { name: "Check out" })).toBeNull();
  expect(screen.queryByLabelText(/promo code/i)).toBeNull();
  expect(screen.queryByRole("button", { name: /Cancel/ })).toBeNull();
  expect(screen.queryByText(/service fee/i)).toBeNull();
  expect(screen.queryByText(/group rate/i)).toBeNull();
  const holdScreen = screen.getByText("Jun 12, 2026, 7:10 PM").closest("section");
  if (holdScreen == null) {
    throw new Error("the hold was not on its own screen");
  }
  expect(getComputedStyle(holdScreen).borderColor).toBe("rgb(245, 158, 11)");
});

test("a failed hold stays on the map and shows that those seats could not be held", async () => {
  vi.mocked(holdSeats).mockRejectedValueOnce(new Error("taken"));
  const user = await openHarborMap();

  await user.click(screen.getByRole("button", { name: "A1 free" }));
  const hold = screen.getByRole("button", { name: "Hold seats" });
  await user.click(hold);

  const failure = await screen.findByText("Those seats could not be held.");
  expect(hold.compareDocumentPosition(failure) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
    Node.DOCUMENT_POSITION_FOLLOWING,
  );
  expect(screen.getByRole("grid", { name: "Orchestra" })).toBeTruthy();
  expect(screen.queryByRole("heading", { name: "Hold" })).toBeNull();
});

test("a seat map that does not load shows that failure", async () => {
  vi.mocked(signIn).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  vi.mocked(searchEvents).mockResolvedValueOnce([
    {
      id: harborMap.id,
      name: harborMap.name,
      venueId: 1,
      venueName: harborMap.venueName,
      startsAt: harborMap.startsAt,
      timeZone: harborMap.timeZone,
    },
  ]);
  vi.mocked(fetchSeatMap).mockRejectedValueOnce(new Error("down"));
  const user = userEvent.setup();
  render(<App />);
  await user.type(screen.getByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));
  await user.type(await screen.findByLabelText("Search events by name"), "Harbor");
  await user.click(screen.getByRole("button", { name: "Search" }));
  await user.click(await screen.findByRole("button", { name: "Open seat map" }));

  expect(await screen.findByText("The seat map did not load.")).toBeTruthy();
  expect(screen.getByRole("heading", { name: "Harbor Lights" })).toBeTruthy();
  expect(screen.queryByRole("grid")).toBeNull();
  expect(screen.queryByRole("button", { name: "Hold seats" })).toBeNull();
});

test("arrow keys stay inside a section, Tab moves between sections, and only a free seat toggles", async () => {
  const user = await openHarborMap({
    ...harborMap,
    sections: [
      {
        name: "Orchestra",
        seats: [
          seat({ id: 1, label: "A1", state: "free" }),
          seat({ id: 2, label: "A2", state: "held" }),
          seat({ id: 3, label: "A3", state: "free" }),
          seat({ id: 4, label: "B1", state: "blocked" }),
          seat({ id: 5, label: "B2", state: "ticket" }),
          seat({ id: 6, label: "B3", state: "free" }),
        ],
      },
      {
        name: "Balcony",
        seats: [seat({ id: 7, label: "C1", state: "free" })],
      },
    ],
  });

  const a1 = screen.getByRole("button", { name: "A1 free" });
  const a2 = screen.getByRole("button", { name: "A2 held" });
  const a3 = screen.getByRole("button", { name: "A3 free" });
  const b1 = screen.getByRole("button", { name: "B1 blocked" });
  const b2 = screen.getByRole("button", { name: "B2 ticket" });
  const b3 = screen.getByRole("button", { name: "B3 free" });
  const c1 = screen.getByRole("button", { name: "C1 free" });

  a1.focus();
  await user.keyboard("{ArrowLeft}");
  expect(document.activeElement).toBe(a1);
  await user.keyboard("{ArrowUp}");
  expect(document.activeElement).toBe(a1);
  await user.keyboard("{ArrowRight}");
  expect(document.activeElement).toBe(a2);
  await user.keyboard("{ArrowRight}");
  expect(document.activeElement).toBe(a3);
  await user.keyboard("{ArrowRight}");
  expect(document.activeElement).toBe(a3);
  await user.keyboard("{ArrowDown}");
  expect(document.activeElement).toBe(b3);
  await user.keyboard("{ArrowDown}");
  expect(document.activeElement).toBe(b3);
  await user.keyboard("{ArrowLeft}");
  expect(document.activeElement).toBe(b2);
  await user.keyboard("{ArrowLeft}");
  expect(document.activeElement).toBe(b1);
  await user.keyboard("{ArrowLeft}");
  expect(document.activeElement).toBe(b1);
  await user.keyboard("{ArrowUp}");
  expect(document.activeElement).toBe(a1);

  a1.focus();
  await user.tab();
  expect(document.activeElement).toBe(c1);
  await user.tab({ shift: true });
  expect(document.activeElement).toBe(a1);

  await user.keyboard("[Space]");
  expect(a1.getAttribute("aria-pressed")).toBe("true");
  expect(within(a1).getByText("selected")).toBeTruthy();
  expect(screen.getByRole("button", { name: "A1 free" })).toBe(a1);
  expect(document.activeElement).toBe(a1);
  await user.keyboard("{Enter}");
  expect(a1.getAttribute("aria-pressed")).toBe("false");
  expect(within(a1).getByText("free")).toBeTruthy();
  expect(document.activeElement).toBe(a1);

  for (const unavailable of [a2, b1, b2]) {
    unavailable.focus();
    expect(unavailable.getAttribute("aria-disabled")).toBe("true");
    expect(unavailable.hasAttribute("aria-pressed")).toBe(false);
    expect(unavailable.hasAttribute("disabled")).toBe(false);
    await user.keyboard("[Space]");
    await user.keyboard("{Enter}");
    expect(unavailable.hasAttribute("aria-pressed")).toBe(false);
    expect(document.activeElement).toBe(unavailable);
  }
  expect(screen.getByText("Select free seats to hold.")).toBeTruthy();

  a1.focus();
  expect(getComputedStyle(a1).outlineStyle).not.toBe("none");
  expect(getComputedStyle(a1).outlineColor).toBe("rgb(56, 189, 248)");
});

test("the seat map is one column, the grid wraps, and each seat target is at least 44px", async () => {
  await openHarborMap();

  const hold = screen.getByRole("button", { name: "Hold seats" });
  const map = hold.closest("section");
  if (map == null) {
    throw new Error("Hold seats is not on the seat map");
  }
  expect(getComputedStyle(map).flexDirection).toBe("column");
  expect(getComputedStyle(screen.getByRole("main")).flexDirection).toBe("column");

  const row = screen.getAllByRole("row")[0];
  expect(getComputedStyle(row).flexWrap).toBe("wrap");

  const seatButton = screen.getByRole("button", { name: "A1 free" });
  const seatStyle = getComputedStyle(seatButton);
  expect(Number.parseFloat(seatStyle.minHeight)).toBeGreaterThanOrEqual(44);
  expect(Number.parseFloat(seatStyle.minWidth)).toBeGreaterThanOrEqual(44);
  expect(getComputedStyle(within(seatButton).getByText("A1")).fontVariantNumeric).toContain("tabular-nums");
  expect(getComputedStyle(map).fontFamily).toContain("Inter");
  expect(getComputedStyle(hold).backgroundColor).toBe("rgb(14, 165, 233)");
  expect(getComputedStyle(screen.getByRole("heading", { name: "Harbor Lights" })).fontFamily).toContain(
    "Plus Jakarta Sans",
  );
});
