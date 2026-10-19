import { useState } from "react";

export const STYLE_VARIANTS = [
  { key: "A", name: "Program" },
  { key: "B", name: "Counter" },
  { key: "C", name: "Plan" },
] as const;

export type VariantKey = (typeof STYLE_VARIANTS)[number]["key"];

export type SeatViewState = "free" | "held" | "blocked" | "ticket" | "selected";

export type Seat = {
  id: number;
  section: "Orchestra" | "Balcony";
  label: string;
  priceCents: number;
  earlyBirdPriceCents: number;
  fixed: "free" | "held" | "blocked" | "ticket";
};

export type Quote = {
  count: number;
  ticketCents: number;
  groupCents: number;
  feeCents: number;
  totalCents: number;
};

export type NextStep =
  | { kind: "hint"; hint: string }
  | { kind: "act"; hint: string; label: string; run: () => void };

const EARLY_BIRD_CENTS = 4000;
const STANDARD_CENTS = 5000;
const SERVICE_FEE_CENTS = 100;
const GROUP_RATE_CENTS = 500;
const GROUP_RATE_AT = 4;
const HOLD_LIMIT = 6;

const eventWhen = new Intl.DateTimeFormat("en-US", {
  weekday: "long",
  month: "long",
  day: "numeric",
  timeZone: "America/New_York",
});

const eventTime = new Intl.DateTimeFormat("en-US", {
  hour: "numeric",
  minute: "2-digit",
  timeZone: "America/New_York",
});

function clock(instant: string): string {
  const date = new Date(instant);
  return `${eventWhen.format(date)}, ${eventTime.format(date)}`;
}

export const eventCopy = {
  email: "ada@example.com",
  eventName: "Harbor Lights",
  venueName: "Harbor Hall",
  whenLabel: clock("2026-12-15T01:00:00Z"),
  earlyBirdLabel: `Early-bird ends ${clock("2026-11-15T00:00:00Z")}. Standard is ${formatCents(STANDARD_CENTS)} after that.`,
  priceTier: "Standard",
};

function buildSeats(): Seat[] {
  const seats: Seat[] = [];
  let id = 1;
  for (let number = 1; number <= 12; number += 1) {
    seats.push({
      id,
      section: "Orchestra",
      label: `A${number}`,
      priceCents: STANDARD_CENTS,
      earlyBirdPriceCents: EARLY_BIRD_CENTS,
      fixed: "free",
    });
    id += 1;
  }
  for (let number = 1; number <= 8; number += 1) {
    seats.push({
      id,
      section: "Balcony",
      label: `B${number}`,
      priceCents: STANDARD_CENTS,
      earlyBirdPriceCents: EARLY_BIRD_CENTS,
      fixed: "free",
    });
    id += 1;
  }
  const fixed: Record<number, Seat["fixed"]> = {
    1: "ticket",
    2: "held",
    6: "blocked",
    16: "ticket",
  };
  return seats.map((seat) => ({ ...seat, fixed: fixed[seat.id] ?? seat.fixed }));
}

export function formatCents(cents: number): string {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(cents / 100);
}

export function stateWord(state: SeatViewState): string {
  switch (state) {
    case "free":
      return "Free";
    case "held":
      return "Held";
    case "blocked":
      return "Blocked";
    case "ticket":
      return "Ticket";
    case "selected":
      return "Selected";
    default: {
      const unexpected: never = state;
      return unexpected;
    }
  }
}

export function quoteFor(count: number): Quote | null {
  if (count === 0) {
    return null;
  }
  const ticketCents = count * EARLY_BIRD_CENTS;
  const groupCents = count >= GROUP_RATE_AT ? GROUP_RATE_CENTS : 0;
  return {
    count,
    ticketCents,
    groupCents,
    feeCents: SERVICE_FEE_CENTS,
    totalCents: ticketCents - groupCents + SERVICE_FEE_CENTS,
  };
}

export type Preview = {
  seats: Seat[];
  selectedIds: number[];
  heldIds: number[];
  ownTicketIds: number[];
  cancelIds: number[];
  promoCode: string | null;
  promoDraft: string;
  waitlistPlace: number | null;
  heldUntilLabel: string | null;
  limitNote: string | null;
  seatView: (seat: Seat) => SeatViewState;
  counts: { free: number; held: number; blocked: number; ticket: number };
  focusIds: number[];
  quote: Quote | null;
  next: NextStep;
  toggleSeat: (id: number) => void;
  toggleCancel: (id: number) => void;
  setPromoDraft: (value: string) => void;
  attachPromo: () => void;
};

function viewFor(
  seat: Seat,
  selectedIds: number[],
  heldIds: number[],
  ownTicketIds: number[],
): SeatViewState {
  if (ownTicketIds.includes(seat.id) || seat.fixed === "ticket") {
    return "ticket";
  }
  if (heldIds.includes(seat.id) || seat.fixed === "held") {
    return "held";
  }
  if (seat.fixed === "blocked") {
    return "blocked";
  }
  if (selectedIds.includes(seat.id)) {
    return "selected";
  }
  return "free";
}

export function usePreview(): Preview {
  const [seats] = useState(buildSeats);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [heldIds, setHeldIds] = useState<number[]>([]);
  const [ownTicketIds, setOwnTicketIds] = useState<number[]>([]);
  const [cancelIds, setCancelIds] = useState<number[]>([]);
  const [promoCode, setPromoCode] = useState<string | null>(null);
  const [promoDraft, setPromoDraft] = useState("");
  const [waitlistPlace, setWaitlistPlace] = useState<number | null>(null);
  const [heldUntilLabel, setHeldUntilLabel] = useState<string | null>(null);
  const [limitNote, setLimitNote] = useState<string | null>(null);

  function seatView(seat: Seat): SeatViewState {
    return viewFor(seat, selectedIds, heldIds, ownTicketIds);
  }

  const counts = { free: 0, held: 0, blocked: 0, ticket: 0 };
  for (const seat of seats) {
    const state = seatView(seat);
    if (state === "selected") {
      counts.free += 1;
    } else {
      counts[state] += 1;
    }
  }

  function toggleSeat(id: number) {
    if (heldIds.length > 0 || ownTicketIds.length > 0) {
      return;
    }
    const seat = seats.find((candidate) => candidate.id === id);
    if (seat == null) {
      return;
    }
    const state = viewFor(seat, selectedIds, heldIds, ownTicketIds);
    if (state === "held" || state === "blocked" || state === "ticket") {
      return;
    }
    if (selectedIds.includes(id)) {
      setSelectedIds(selectedIds.filter((current) => current !== id));
      setLimitNote(null);
      return;
    }
    if (selectedIds.length >= HOLD_LIMIT) {
      setLimitNote("An order holds at most 6 seats.");
      return;
    }
    setLimitNote(null);
    setSelectedIds([...selectedIds, id]);
  }

  function hold() {
    if (selectedIds.length === 0) {
      return;
    }
    const until = new Date(Date.now() + 10 * 60 * 1000);
    setHeldIds(selectedIds);
    setSelectedIds([]);
    setHeldUntilLabel(
      new Intl.DateTimeFormat("en-US", {
        hour: "numeric",
        minute: "2-digit",
        timeZone: "America/New_York",
      }).format(until),
    );
    setLimitNote(null);
  }

  function checkout() {
    if (heldIds.length === 0) {
      return;
    }
    setOwnTicketIds(heldIds);
    setHeldIds([]);
    setHeldUntilLabel(null);
    setCancelIds([]);
  }

  function cancel() {
    if (cancelIds.length === 0) {
      return;
    }
    setOwnTicketIds(ownTicketIds.filter((id) => !cancelIds.includes(id)));
    setCancelIds([]);
    if (ownTicketIds.every((id) => cancelIds.includes(id))) {
      setPromoCode(null);
    }
  }

  function joinWaitlist() {
    setWaitlistPlace(4);
  }

  function attachPromo() {
    const code = promoDraft.trim();
    if (code === "" || heldIds.length === 0 || promoCode != null) {
      return;
    }
    setPromoCode(code);
    setPromoDraft("");
  }

  function toggleCancel(id: number) {
    setCancelIds((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  }

  const focusIds = ownTicketIds.length > 0 ? ownTicketIds : heldIds.length > 0 ? heldIds : selectedIds;
  const quote = quoteFor(focusIds.length);
  const next = buildNext({
    selectedCount: selectedIds.length,
    heldCount: heldIds.length,
    ownCount: ownTicketIds.length,
    cancelCount: cancelIds.length,
    freeCount: counts.free,
    waitlistPlace,
    heldUntilLabel,
    limitNote,
    hold,
    checkout,
    cancel,
    joinWaitlist,
  });

  return {
    seats,
    selectedIds,
    heldIds,
    ownTicketIds,
    cancelIds,
    promoCode,
    promoDraft,
    waitlistPlace,
    heldUntilLabel,
    limitNote,
    seatView,
    counts,
    focusIds,
    quote,
    next,
    toggleSeat,
    toggleCancel,
    setPromoDraft,
    attachPromo,
  };
}

function buildNext(input: {
  selectedCount: number;
  heldCount: number;
  ownCount: number;
  cancelCount: number;
  freeCount: number;
  waitlistPlace: number | null;
  heldUntilLabel: string | null;
  limitNote: string | null;
  hold: () => void;
  checkout: () => void;
  cancel: () => void;
  joinWaitlist: () => void;
}): NextStep {
  if (input.ownCount > 0) {
    if (input.cancelCount === 0) {
      return { kind: "hint", hint: "Choose tickets to cancel." };
    }
    const noun = input.cancelCount === 1 ? "ticket" : "tickets";
    return {
      kind: "act",
      hint: `${input.cancelCount} ${noun} chosen.`,
      label: input.cancelCount === 1 ? "Cancel ticket" : "Cancel tickets",
      run: input.cancel,
    };
  }
  if (input.heldCount > 0) {
    const until = input.heldUntilLabel == null ? "Held." : `Held until ${input.heldUntilLabel}.`;
    return {
      kind: "act",
      hint: `${until} A hold lasts 10 minutes.`,
      label: "Check out",
      run: input.checkout,
    };
  }
  if (input.freeCount === 0 && input.waitlistPlace == null) {
    return { kind: "act", hint: "No seat is free.", label: "Join waitlist", run: input.joinWaitlist };
  }
  if (input.waitlistPlace != null && input.selectedCount === 0) {
    return { kind: "hint", hint: `You are number ${input.waitlistPlace} in line.` };
  }
  if (input.limitNote != null) {
    return { kind: "hint", hint: input.limitNote };
  }
  if (input.selectedCount === 0) {
    return { kind: "hint", hint: "Select a free seat." };
  }
  const noun = input.selectedCount === 1 ? "seat" : "seats";
  return {
    kind: "act",
    hint: `${input.selectedCount} ${noun} selected.`,
    label: input.selectedCount === 1 ? "Hold seat" : "Hold seats",
    run: input.hold,
  };
}

export function labelsFor(seats: Seat[], ids: number[]): string {
  return ids
    .map((id) => seats.find((seat) => seat.id === id)?.label)
    .filter((label): label is string => label != null)
    .join(", ");
}

export function sectionSeats(seats: Seat[], section: Seat["section"]): Seat[] {
  return seats.filter((seat) => seat.section === section);
}
