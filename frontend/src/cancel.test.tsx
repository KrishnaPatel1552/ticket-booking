import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import {
  cancelTickets,
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

function emptyOrder(): OrderView {
  return {
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: null,
    tickets: [],
    refunds: [],
    holds: [],
  };
}

async function openPaidTickets() {
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
  vi.mocked(fetchSeatMap).mockResolvedValue(harborMap);
  vi.mocked(fetchWaitlist).mockResolvedValue(null);
  vi.mocked(fetchSaleTerms).mockResolvedValue({
    groupRateThreshold: 4,
    groupRateAmountCents: 800,
    serviceFeeCents: 250,
  });
  vi.mocked(fetchOrder).mockResolvedValue(emptyOrder());
  const user = userEvent.setup();
  render(<App />);
  await user.type(await screen.findByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));
  await user.type(await screen.findByLabelText("Search events by name"), "Harbor");
  await user.click(screen.getByRole("button", { name: "Search" }));
  await user.click(await screen.findByRole("button", { name: "Open seat map" }));
  await screen.findByRole("heading", { name: harborMap.name });
  await user.click(screen.getByRole("button", { name: "A1 free" }));
  await user.click(screen.getByRole("button", { name: "A2 free" }));
  vi.mocked(holdSeats).mockResolvedValueOnce({
    orderId: 9,
    seatIds: [1, 2],
    heldUntil: "2026-06-12T23:10:00.000Z",
  });
  await user.click(screen.getByRole("button", { name: "Hold seats" }));
  await screen.findByText("Jun 12, 2026, 7:10 PM");
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
    refunds: [],
    holds: [],
  });
  await user.click(screen.getByRole("radio", { name: "success" }));
  await user.click(screen.getByRole("button", { name: "Check out" }));
  const tickets = await screen.findByRole("region", { name: "Tickets" });
  return { user, tickets };
}

function scriptedResultLine(tickets: HTMLElement, word: string): HTMLElement {
  const line = within(tickets)
    .getAllByText(word)
    .find((node) => node.tagName === "P");
  if (!(line instanceof HTMLElement)) {
    throw new Error(`${word} was not its own line`);
  }
  return line;
}

test("a partial cancel drops the checked tickets and shows the scripted refund on its own line", async () => {
  const { user, tickets } = await openPaidTickets();

  expect(within(tickets).getByText("A1")).toBeTruthy();
  expect(within(tickets).getByText("A2")).toBeTruthy();
  expect(within(tickets).getByText("succeeded")).toBeTruthy();
  expect(within(tickets).getByText("$102.50")).toBeTruthy();
  expect(within(tickets).getByRole("radio", { name: "success" })).toBeTruthy();
  expect(within(tickets).getByRole("radio", { name: "decline" })).toBeTruthy();
  expect(within(tickets).getByRole("radio", { name: "timeout" })).toBeTruthy();
  expect(within(tickets).getByRole("radio", { name: "unknown" })).toBeTruthy();
  expect(within(tickets).queryByRole("button", { name: /cancel all/i })).toBeNull();
  expect(within(tickets).queryByRole("button", { name: /reconcile/i })).toBeNull();

  const cancel = within(tickets).getByRole("button", { name: "Cancel selected" });
  expect(cancel.hasAttribute("disabled")).toBe(true);
  const cancelStyle = getComputedStyle(cancel);
  expect(Number.parseFloat(cancelStyle.minHeight)).toBeGreaterThanOrEqual(44);
  expect(Number.parseFloat(cancelStyle.minWidth)).toBeGreaterThanOrEqual(44);
  expect(cancelStyle.backgroundColor).toBe("rgb(14, 165, 233)");
  const cyanButtons = within(tickets)
    .getAllByRole("button")
    .filter((button) => getComputedStyle(button).backgroundColor === "rgb(14, 165, 233)");
  expect(cyanButtons).toEqual([cancel]);
  expect(getComputedStyle(tickets).display).toBe("flex");
  expect(getComputedStyle(tickets).flexDirection).toBe("column");
  expect(getComputedStyle(tickets).fontFamily).toContain("Inter");
  expect(cancelStyle.animationName).toBe("none");
  expect(getComputedStyle(tickets).animationName).toBe("none");
  expect(getComputedStyle(screen.getByRole("heading", { name: "Harbor Lights" })).fontFamily).toContain(
    "Plus Jakarta Sans",
  );
  expect(getComputedStyle(document.querySelector(".events-screen")!).backgroundColor).toBe("rgb(10, 17, 24)");
  expect(getComputedStyle(within(tickets).getByText("$102.50")).fontVariantNumeric).toBe("tabular-nums");

  for (const box of within(tickets).getAllByRole("checkbox")) {
    const choice = box.closest("label");
    if (!(choice instanceof HTMLElement)) {
      throw new Error("ticket choice was missing");
    }
    const choiceStyle = getComputedStyle(choice);
    expect(Number.parseFloat(choiceStyle.minHeight)).toBeGreaterThanOrEqual(44);
    expect(Number.parseFloat(choiceStyle.minWidth)).toBeGreaterThanOrEqual(44);
    expect(choiceStyle.fontFamily).toContain("Inter");
    expect(choiceStyle.animationName).toBe("none");
  }

  await user.click(within(tickets).getByRole("checkbox", { name: "A1" }));

  expect(cancel.hasAttribute("disabled")).toBe(false);
  expect((within(tickets).getByRole("checkbox", { name: "A2" }) as HTMLInputElement).checked).toBe(false);

  vi.mocked(cancelTickets).mockResolvedValueOnce({
    id: 9,
    eventId: 7,
    promoCode: null,
    payment: { status: "succeeded", amountCents: 10250 },
    tickets: [{ seatId: 2, label: "A2" }],
    refunds: [{ status: "succeeded", amountCents: 5000 }],
    holds: [],
  });
  await user.click(within(tickets).getByRole("radio", { name: "success" }));
  await user.click(cancel);

  expect(within(tickets).queryByRole("checkbox", { name: "A1" })).toBeNull();
  expect(within(tickets).getByRole("checkbox", { name: "A2" })).toBeTruthy();
  expect(within(tickets).getByText("succeeded")).toBeTruthy();
  expect(within(tickets).getByText("$102.50")).toBeTruthy();
  const result = scriptedResultLine(tickets, "success");
  const refundAmount = within(tickets).getByText("$50.00");
  expect(result.compareDocumentPosition(refundAmount) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
    Node.DOCUMENT_POSITION_FOLLOWING,
  );
  expect(refundAmount.tagName).toBe("P");
  expect(getComputedStyle(refundAmount).fontVariantNumeric).toBe("tabular-nums");
  expect(vi.mocked(cancelTickets)).toHaveBeenCalledWith(9, [1], "success");
  expect(vi.mocked(checkout)).toHaveBeenCalledWith(9, [1, 2], "success");
});

test("a cancel that does not complete keeps the tickets and says so under Cancel selected", async () => {
  const { user, tickets } = await openPaidTickets();
  vi.mocked(cancelTickets).mockRejectedValueOnce(new Error("down"));

  await user.click(within(tickets).getByRole("checkbox", { name: "A1" }));
  await user.click(within(tickets).getByRole("checkbox", { name: "A2" }));
  await user.click(within(tickets).getByRole("radio", { name: "timeout" }));
  const cancel = within(tickets).getByRole("button", { name: "Cancel selected" });
  await user.click(cancel);

  const failure = await screen.findByText("Those tickets could not be cancelled.");
  expect(cancel.compareDocumentPosition(failure) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
    Node.DOCUMENT_POSITION_FOLLOWING,
  );
  expect(failure.closest("section.tickets-screen")).toBe(tickets);
  expect(within(tickets).getByRole("checkbox", { name: "A1" })).toBeTruthy();
  expect(within(tickets).getByRole("checkbox", { name: "A2" })).toBeTruthy();
  expect(within(tickets).queryByText("$50.00")).toBeNull();
  expect(
    within(tickets)
      .getAllByText("timeout")
      .some((node) => node.tagName === "P"),
  ).toBe(false);
  expect(vi.mocked(cancelTickets)).toHaveBeenCalledWith(9, [1, 2], "timeout");
});
