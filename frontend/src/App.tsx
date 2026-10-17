import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import * as api from "./api";
import type {
  EventSeatMap,
  EventSummary,
  HoldView,
  OrderView,
  PaymentMock,
  PaymentStatus,
  SaleTermsView,
  SeatState,
  SeatView,
  SignedIn,
  WaitlistPlace,
} from "./api";

function readClock(): number {
  return Date.now();
}

function formatCents(cents: number): string {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(cents / 100);
}

function formatWhen(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone,
  }).format(new Date(instant));
}

function currentPriceCents(map: EventSeatMap, seat: SeatView, now: number): number {
  if (seat.earlyBirdPriceCents == null || map.earlyBirdStartsAt == null || map.earlyBirdEndsAt == null) {
    return seat.priceCents;
  }
  const start = new Date(map.earlyBirdStartsAt).getTime();
  const end = new Date(map.earlyBirdEndsAt).getTime();
  if (now >= start && now < end) {
    return seat.earlyBirdPriceCents;
  }
  return seat.priceCents;
}

function orderPriceLines(
  map: EventSeatMap,
  seatIds: number[],
  now: number,
  terms: SaleTermsView | null,
  promoCode: string | null,
  promoAmountCents: number | null,
  promoExclusive: boolean | null,
): string[] {
  const held = seatIds
    .map((id) => seatById(map, id))
    .filter((seat): seat is SeatView => seat != null);
  const exclusive = promoCode != null && promoCode !== "" && promoExclusive === true;
  const priceTierCents = held.reduce((sum, seat) => sum + seat.priceCents, 0);
  const ticketTotalCents = exclusive
    ? priceTierCents
    : held.reduce((sum, seat) => sum + currentPriceCents(map, seat, now), 0);
  const lines = [`Price tier ${formatCents(priceTierCents)}`];
  if (!exclusive && earlyBirdWindowOpen(map, now)) {
    const earlyBirdCents = held.reduce((sum, seat) => sum + currentPriceCents(map, seat, now), 0);
    lines.push(`Early-bird ${formatCents(earlyBirdCents)}`);
  }
  lines.push(`Ticket total ${formatCents(ticketTotalCents)}`);
  let groupRateCents = 0;
  if (!exclusive && terms != null && held.length >= terms.groupRateThreshold) {
    groupRateCents = Math.min(terms.groupRateAmountCents, ticketTotalCents);
    lines.push(`Group rate ${formatCents(groupRateCents)}`);
  }
  let promoDiscountCents = 0;
  if (promoCode != null && promoCode !== "") {
    if (promoAmountCents != null) {
      promoDiscountCents = Math.min(promoAmountCents, Math.max(ticketTotalCents - groupRateCents, 0));
      lines.push(`${promoCode} -${formatCents(promoDiscountCents)}`);
    } else {
      lines.push(promoCode);
    }
  }
  if (terms != null) {
    lines.push(`Service fee ${formatCents(terms.serviceFeeCents)}`);
    lines.push(`Total ${formatCents(ticketTotalCents - groupRateCents - promoDiscountCents + terms.serviceFeeCents)}`);
  }
  return lines;
}

function priceTierLine(map: EventSeatMap): string | null {
  const tiers = new Set<number>();
  for (const section of map.sections) {
    for (const seat of section.seats) {
      tiers.add(seat.priceCents);
    }
  }
  if (tiers.size === 0) {
    return null;
  }
  const formatted = [...tiers].sort((left, right) => left - right).map((cents) => formatCents(cents)).join(", ");
  if (map.earlyBirdEndsAt != null) {
    return `Early-bird ends ${formatWhen(map.earlyBirdEndsAt, map.timeZone)}. The price tier is ${formatted} after that.`;
  }
  return `The price tier is ${formatted}.`;
}

function earlyBirdWindowOpen(map: EventSeatMap, now: number): boolean {
  if (map.earlyBirdStartsAt == null || map.earlyBirdEndsAt == null) {
    return false;
  }
  const start = new Date(map.earlyBirdStartsAt).getTime();
  const end = new Date(map.earlyBirdEndsAt).getTime();
  return now >= start && now < end;
}

function seatById(map: EventSeatMap, seatId: number): SeatView | undefined {
  for (const section of map.sections) {
    const seat = section.seats.find((candidate) => candidate.id === seatId);
    if (seat != null) {
      return seat;
    }
  }
  return undefined;
}

type SeatMarkKind = "ring" | "check" | "amber" | "hash" | "slash";

const legendMarks: { word: string; mark: SeatMarkKind }[] = [
  { word: "free", mark: "ring" },
  { word: "held", mark: "amber" },
  { word: "blocked", mark: "hash" },
  { word: "ticket", mark: "slash" },
  { word: "selected", mark: "check" },
];

function markKind(state: SeatState, selected: boolean): SeatMarkKind {
  if (selected) {
    return "check";
  }
  switch (state) {
    case "free":
      return "ring";
    case "held":
      return "amber";
    case "blocked":
      return "hash";
    case "ticket":
      return "slash";
    default: {
      const unexpected: never = state;
      return unexpected;
    }
  }
}

function SeatMark({ kind }: { kind: SeatMarkKind }) {
  return (
    <span className={`mark mark-${kind}`} data-mark={kind} aria-hidden="true">
      {kind === "check" ? "✓" : null}
      {kind === "hash" ? "×" : null}
      {kind === "slash" ? "∕" : null}
    </span>
  );
}

type PlacedSeat = { row: string; column: number; seat: SeatView };

function placedSeats(seats: SeatView[]): PlacedSeat[] {
  const placed: PlacedSeat[] = [];
  for (const seat of seats) {
    const match = /^([A-Za-z]+)(\d+)$/.exec(seat.label);
    if (match != null) {
      placed.push({ row: match[1], column: Number(match[2]), seat });
    }
  }
  return placed;
}

function seatRows(seats: SeatView[]): { key: string; seats: SeatView[] }[] {
  const placed = placedSeats(seats);
  const placedIds = new Set(placed.map((item) => item.seat.id));
  const names = [...new Set(placed.map((item) => item.row))].sort((left, right) => left.localeCompare(right));
  const rows = names.map((name) => ({
    key: name,
    seats: placed
      .filter((item) => item.row === name)
      .sort((left, right) => left.column - right.column)
      .map((item) => item.seat),
  }));
  const loose = seats.filter((seat) => !placedIds.has(seat.id));
  if (loose.length > 0) {
    rows.push({ key: "other", seats: loose });
  }
  return rows;
}

function seatInDirection(seats: SeatView[], seatId: number, key: string): SeatView | null {
  const placed = placedSeats(seats);
  const current = placed.find((item) => item.seat.id === seatId);
  if (current == null) {
    return null;
  }
  const rows = [...new Set(placed.map((item) => item.row))].sort((left, right) => left.localeCompare(right));
  const rowIndex = rows.indexOf(current.row);
  if (key === "ArrowLeft") {
    return placed.find((item) => item.row === current.row && item.column === current.column - 1)?.seat ?? null;
  }
  if (key === "ArrowRight") {
    return placed.find((item) => item.row === current.row && item.column === current.column + 1)?.seat ?? null;
  }
  if (key === "ArrowUp") {
    const row = rows[rowIndex - 1];
    return row == null ? null : placed.find((item) => item.row === row && item.column === current.column)?.seat ?? null;
  }
  if (key === "ArrowDown") {
    const row = rows[rowIndex + 1];
    return row == null ? null : placed.find((item) => item.row === row && item.column === current.column)?.seat ?? null;
  }
  return null;
}

type ActiveHoldState = {
  orderId: number;
  labels: string[];
  seatIds: number[];
  heldUntil: string;
};

export default function App() {
  const [signedIn, setSignedIn] = useState<SignedIn | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [signInError, setSignInError] = useState<string | null>(null);
  const [signingIn, setSigningIn] = useState(false);
  const [query, setQuery] = useState("");
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [searching, setSearching] = useState(false);
  const [openEventId, setOpenEventId] = useState<number | null>(null);
  const [seatMap, setSeatMap] = useState<EventSeatMap | null>(null);
  const [mapError, setMapError] = useState<string | null>(null);
  const [opening, setOpening] = useState(false);
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([]);
  const [holding, setHolding] = useState(false);
  const [releasing, setReleasing] = useState(false);
  const [holdError, setHoldError] = useState<string | null>(null);
  const [confirmedHold, setConfirmedHold] = useState<ActiveHoldState | null>(null);
  const [activeHold, setActiveHold] = useState<ActiveHoldState | null>(null);
  const [saleTerms, setSaleTerms] = useState<SaleTermsView | null>(null);
  const [priceUnavailable, setPriceUnavailable] = useState(false);
  const [earlierOrder, setEarlierOrder] = useState<OrderView | null>(null);
  const [promoCode, setPromoCode] = useState<string | null>(null);
  const [promoAmountCents, setPromoAmountCents] = useState<number | null>(null);
  const [promoExclusive, setPromoExclusive] = useState<boolean | null>(null);
  const [promoDraft, setPromoDraft] = useState("");
  const [promoError, setPromoError] = useState<string | null>(null);
  const [attaching, setAttaching] = useState(false);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [paymentMock, setPaymentMock] = useState<PaymentMock | null>(null);
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus | null>(null);
  const [paidOrder, setPaidOrder] = useState<OrderView | null>(null);
  const [ticketIdsToCancel, setTicketIdsToCancel] = useState<number[]>([]);
  const [refundMock, setRefundMock] = useState<PaymentMock | null>(null);
  const [refundScript, setRefundScript] = useState<PaymentMock | null>(null);
  const [refundAmountCents, setRefundAmountCents] = useState<number | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [tabStops, setTabStops] = useState<Record<string, number>>({});
  const [focusedSeatId, setFocusedSeatId] = useState<number | null>(null);
  const seatRefs = useRef(new Map<number, HTMLButtonElement>());
  const pendingFocusId = useRef<number | null>(null);
  const [pricedAt, setPricedAt] = useState(0);
  const [waitlist, setWaitlist] = useState<WaitlistPlace | null>(null);
  const [offeredHolds, setOfferedHolds] = useState<HoldView[]>([]);
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);

  useEffect(() => {
    if (pendingFocusId.current == null) {
      return;
    }
    const node = seatRefs.current.get(pendingFocusId.current);
    pendingFocusId.current = null;
    node?.focus();
  });

  async function loadSeatMap(eventId: number) {
    setOpening(true);
    setMapError(null);
    try {
      const map = await api.fetchSeatMap(eventId);
      setSeatMap(map);
      setPricedAt(readClock());
      setSelectedSeatIds((current) => current.filter((id) => seatById(map, id)?.state === "free"));
      await loadWaitlist(eventId);
      await syncCustomerEventOrders(map);
    } catch {
      setSeatMap(null);
      setMapError("The seat map did not load.");
    } finally {
      setOpening(false);
    }
  }

  async function syncCustomerEventOrders(map: EventSeatMap) {
    if (map.activeHoldOrderId != null) {
      try {
        const order = await api.fetchOrder(map.activeHoldOrderId);
        if (order != null && order.holds.length > 0) {
          setActiveHold({
            orderId: order.id,
            labels: order.holds.map((hold) => hold.label),
            seatIds: order.holds.map((hold) => hold.seatId),
            heldUntil: order.holds[0].heldUntil,
          });
          setPromoCode(order.promoCode);
          setPromoAmountCents(order.promoAmountCents ?? null);
          setPromoExclusive(order.promoExclusive ?? null);
        } else {
          setActiveHold(null);
        }
      } catch {
        setActiveHold(null);
      }
    } else {
      setActiveHold(null);
    }
    if (map.paidOrderIds != null && map.paidOrderIds.length > 0) {
      const latestPaidId = map.paidOrderIds[map.paidOrderIds.length - 1];
      try {
        const paid = await api.fetchOrder(latestPaidId);
        if (paid != null && paid.tickets.length > 0) {
          setEarlierOrder(paid);
        }
      } catch {
        // Keep existing earlierOrder state if lookup fails
      }
    }
  }

  async function loadWaitlist(eventId: number) {
    try {
      const line = await api.fetchWaitlist(eventId);
      setWaitlist(line);
      if (line?.orderId == null) {
        setOfferedHolds([]);
        return;
      }
      const offered = await api.fetchOrder(line.orderId);
      setOfferedHolds(offered.holds);
    } catch {
      setJoinError("The waitlist did not load.");
    }
  }

  async function submitJoin() {
    if (seatMap == null) {
      return;
    }
    setJoining(true);
    setJoinError(null);
    try {
      setWaitlist(await api.joinWaitlist(seatMap.id));
    } catch {
      setJoinError("The waitlist did not accept that request.");
    } finally {
      setJoining(false);
    }
  }

  async function submitSignIn(event: FormEvent) {
    event.preventDefault();
    setSigningIn(true);
    setSignInError(null);
    try {
      const session = await api.signIn(email, password);
      setSignedIn(session);
      setPassword("");
    } catch {
      setSignedIn(null);
      setSignInError("That email and password did not sign you in.");
    } finally {
      setSigningIn(false);
    }
  }

  async function submitSearch(event: FormEvent) {
    event.preventDefault();
    const name = query.trim();
    if (name === "") {
      setSearchError("Enter an event name.");
      setEvents([]);
      return;
    }
    setSearching(true);
    setSearchError(null);
    try {
      const found = await api.searchEvents(name);
      setEvents(found);
      if (found.length === 0) {
        setSearchError("No events match that name.");
      }
      if (openEventId != null) {
        await loadSeatMap(openEventId);
      }
    } catch {
      setEvents([]);
      setSearchError("The search did not complete.");
    } finally {
      setSearching(false);
    }
  }

  async function openEvent(eventId: number) {
    setOpenEventId(eventId);
    setSeatMap(null);
    setSelectedSeatIds([]);
    setHoldError(null);
    setConfirmedHold(null);
    setActiveHold(null);
    setSaleTerms(null);
    setPriceUnavailable(false);
    setEarlierOrder(null);
    setPromoCode(null);
    setPromoAmountCents(null);
    setPromoExclusive(null);
    setPromoDraft("");
    setPromoError(null);
    setCheckoutOpen(false);
    setPaymentMock(null);
    setPaymentStatus(null);
    setPaidOrder(null);
    setTicketIdsToCancel([]);
    setRefundMock(null);
    setRefundScript(null);
    setRefundAmountCents(null);
    setCancelError(null);
    setCheckoutError(null);
    setTabStops({});
    setWaitlist(null);
    setOfferedHolds([]);
    setJoinError(null);
    await loadSeatMap(eventId);
  }

  function backToSearch() {
    setOpenEventId(null);
    setSeatMap(null);
    setMapError(null);
    setSelectedSeatIds([]);
    setHoldError(null);
    setConfirmedHold(null);
    setActiveHold(null);
    setCheckoutOpen(false);
    setPaymentMock(null);
    setPaymentStatus(null);
    setPaidOrder(null);
    setTicketIdsToCancel([]);
    setRefundMock(null);
    setRefundScript(null);
    setRefundAmountCents(null);
    setCancelError(null);
    setCheckoutError(null);
  }

  function moveSeatFocus(sectionName: string, seatId: number) {
    pendingFocusId.current = seatId;
    setTabStops((current) => ({ ...current, [sectionName]: seatId }));
  }

  function onSeatKeyDown(
    sectionName: string,
    seats: SeatView[],
    seat: SeatView,
    event: KeyboardEvent<HTMLButtonElement>,
  ) {
    const arrow =
      event.key === "ArrowLeft" ||
      event.key === "ArrowRight" ||
      event.key === "ArrowUp" ||
      event.key === "ArrowDown";
    if (!arrow) {
      return;
    }
    event.preventDefault();
    const next = seatInDirection(seats, seat.id, event.key);
    if (next != null) {
      moveSeatFocus(sectionName, next.id);
    }
  }

  function toggleSeat(seat: SeatView) {
    if (seat.state !== "free" || holding) {
      return;
    }
    setHoldError(null);
    setSelectedSeatIds((current) =>
      current.includes(seat.id) ? current.filter((id) => id !== seat.id) : [...current, seat.id],
    );
  }

  async function openOfferedHold() {
    if (offeredHolds.length === 0 || waitlist?.orderId == null) {
      return;
    }
    const orderId = waitlist.orderId;
    setSaleTerms(await loadSaleTerms());
    setPromoCode(null);
    setPromoAmountCents(null);
    setPromoExclusive(null);
    setPromoDraft("");
    setPromoError(null);
    const holdState: ActiveHoldState = {
      orderId,
      labels: offeredHolds.map((hold) => hold.label),
      seatIds: offeredHolds.map((hold) => hold.seatId),
      heldUntil: offeredHolds[0].heldUntil,
    };
    setConfirmedHold(holdState);
    setActiveHold(holdState);
  }

  async function resumeHoldCheckout() {
    if (activeHold == null) {
      return;
    }
    if (saleTerms == null && !priceUnavailable) {
      setSaleTerms(await loadSaleTerms());
    }
    setConfirmedHold(activeHold);
    setPaymentStatus(null);
    setCheckoutError(null);
    setCheckoutOpen(true);
  }

  async function submitHold() {
    if (seatMap == null || selectedSeatIds.length === 0) {
      return;
    }
    const eventId = seatMap.id;
    const seatsToHold = selectedSeatIds;
    setHolding(true);
    setHoldError(null);
    try {
      const held = await api.holdSeats(eventId, seatsToHold);
      const effectiveSeatIds = held.seatIds.length > 0 ? held.seatIds : seatsToHold;
      const labels = effectiveSeatIds
        .map((id) => seatById(seatMap, id)?.label)
        .filter((label): label is string => label != null);
      setSaleTerms(await loadSaleTerms());
      setPromoCode(null);
      setPromoAmountCents(null);
      setPromoExclusive(null);
      setPromoDraft("");
      setPromoError(null);
      const nextHold: ActiveHoldState = {
        orderId: held.orderId,
        labels,
        seatIds: effectiveSeatIds,
        heldUntil: held.heldUntil,
      };
      setConfirmedHold(nextHold);
      setActiveHold(nextHold);
      setSelectedSeatIds([]);
    } catch {
      setHoldError("Those seats could not be held.");
      await loadSeatMap(eventId);
    } finally {
      setHolding(false);
    }
  }

  async function submitReleaseHold() {
    const target = confirmedHold ?? activeHold;
    if (target == null || releasing) {
      return;
    }
    const eventId = seatMap?.id;
    setReleasing(true);
    setHoldError(null);
    setPromoError(null);
    try {
      if (typeof api.releaseOrderHolds === "function") {
        await api.releaseOrderHolds(target.orderId);
      }
      setConfirmedHold(null);
      setActiveHold(null);
      setPromoCode(null);
      setPromoAmountCents(null);
      setPromoExclusive(null);
      setPromoDraft("");
      setCheckoutOpen(false);
      setPaymentMock(null);
      setPaymentStatus(null);
      if (eventId != null) {
        await loadSeatMap(eventId);
      }
    } catch {
      setHoldError("Those seats could not be released.");
    } finally {
      setReleasing(false);
    }
  }

  async function loadSaleTerms(): Promise<SaleTermsView | null> {
    try {
      const terms = await api.fetchSaleTerms();
      setPriceUnavailable(false);
      return terms;
    } catch {
      setPriceUnavailable(true);
      return null;
    }
  }

  function openEarlierTickets() {
    if (earlierOrder == null || earlierOrder.tickets.length === 0) {
      return;
    }
    setPaidOrder(earlierOrder);
    setTicketIdsToCancel([]);
    setRefundMock(null);
    setRefundScript(null);
    setRefundAmountCents(null);
    setCancelError(null);
  }

  function closeTickets() {
    setPaidOrder(null);
    setTicketIdsToCancel([]);
    setRefundMock(null);
    setRefundScript(null);
    setRefundAmountCents(null);
    setCancelError(null);
  }

  function backToSeatMap() {
    closeTickets();
    setConfirmedHold(null);
    setCheckoutOpen(false);
  }

  async function leaveHold() {
    const eventId = seatMap?.id;
    setConfirmedHold(null);
    setPromoCode(null);
    setPromoAmountCents(null);
    setPromoExclusive(null);
    setPromoDraft("");
    setPromoError(null);
    setSelectedSeatIds([]);
    setCheckoutOpen(false);
    setPaymentMock(null);
    setPaymentStatus(null);
    setPaidOrder(null);
    setTicketIdsToCancel([]);
    setRefundMock(null);
    setRefundScript(null);
    setRefundAmountCents(null);
    setCancelError(null);
    setCheckoutError(null);
    if (eventId != null) {
      await loadSeatMap(eventId);
    }
  }

  async function submitCheckout() {
    if (confirmedHold == null || paymentMock == null) {
      return;
    }
    const orderId = confirmedHold.orderId;
    const seatIds = confirmedHold.seatIds;
    const mock = paymentMock;
    const eventId = seatMap?.id;
    setCheckoutError(null);
    try {
      await api.checkout(orderId, seatIds, mock);
      const order = await api.fetchOrder(orderId);
      const status = order.payment?.status ?? null;
      setEarlierOrder(status === "succeeded" && order.tickets.length > 0 ? order : null);
      if (status === "succeeded") {
        setPaidOrder(order);
        setConfirmedHold(null);
        setActiveHold(null);
        setTicketIdsToCancel([]);
        setRefundMock(null);
        setRefundScript(null);
        setRefundAmountCents(null);
        setCancelError(null);
        setCheckoutOpen(false);
        return;
      }
      setPaymentStatus(status);
      if (status === "declined") {
        setConfirmedHold(null);
        setActiveHold(null);
        if (eventId != null) {
          await loadSeatMap(eventId);
        }
      }
    } catch {
      setCheckoutError("Checkout did not complete.");
    }
  }

  async function submitReconcilePayment() {
    if (confirmedHold == null || paymentMock == null || typeof api.reconcilePayment !== "function") {
      return;
    }
    const orderId = confirmedHold.orderId;
    const eventId = seatMap?.id;
    setCheckoutError(null);
    try {
      const order = await api.reconcilePayment(orderId, paymentMock);
      const status = order.payment?.status ?? null;
      setEarlierOrder(status === "succeeded" && order.tickets.length > 0 ? order : null);
      if (status === "succeeded") {
        setPaidOrder(order);
        setConfirmedHold(null);
        setActiveHold(null);
        setTicketIdsToCancel([]);
        setRefundMock(null);
        setRefundScript(null);
        setRefundAmountCents(null);
        setCancelError(null);
        setCheckoutOpen(false);
        return;
      }
      setPaymentStatus(status);
      if (status === "declined") {
        setConfirmedHold(null);
        setActiveHold(null);
        if (eventId != null) {
          await loadSeatMap(eventId);
        }
      }
    } catch {
      setCheckoutError("Payment lookup did not complete.");
    }
  }

  function toggleTicket(seatId: number) {
    setTicketIdsToCancel((current) =>
      current.includes(seatId) ? current.filter((id) => id !== seatId) : [...current, seatId],
    );
  }

  async function submitCancel() {
    if (paidOrder == null || refundMock == null || ticketIdsToCancel.length === 0) {
      return;
    }
    const orderId = paidOrder.id;
    const seatIds = paidOrder.tickets
      .map((ticket) => ticket.seatId)
      .filter((seatId) => ticketIdsToCancel.includes(seatId));
    const mock = refundMock;
    setCancelError(null);
    try {
      const order = await api.cancelTickets(orderId, seatIds, mock);
      const latestRefund = order.refunds[order.refunds.length - 1];
      setPaidOrder(order);
      setEarlierOrder((current) => {
        if (current == null || current.id !== order.id) {
          return current;
        }
        return order.tickets.length > 0 ? order : null;
      });
      setTicketIdsToCancel([]);
      setRefundScript(mock);
      setRefundAmountCents(latestRefund == null ? null : latestRefund.amountCents);
    } catch {
      setCancelError("Those tickets could not be cancelled.");
    }
  }

  async function submitReconcileRefund() {
    if (paidOrder == null || refundMock == null || typeof api.reconcileRefund !== "function") {
      return;
    }
    setCancelError(null);
    try {
      const order = await api.reconcileRefund(paidOrder.id, refundMock);
      const latestRefund = order.refunds[order.refunds.length - 1];
      setPaidOrder(order);
      setEarlierOrder((current) => {
        if (current == null || current.id !== order.id) {
          return current;
        }
        return order.tickets.length > 0 ? order : null;
      });
      setRefundScript(refundMock);
      setRefundAmountCents(latestRefund == null ? null : latestRefund.amountCents);
    } catch {
      setCancelError("Refund lookup did not complete.");
    }
  }

  async function submitPromo(event: FormEvent) {
    event.preventDefault();
    if (confirmedHold == null) {
      return;
    }
    const code = promoDraft.trim();
    if (code === "") {
      return;
    }
    setAttaching(true);
    setPromoError(null);
    try {
      const order = await api.attachPromoCode(confirmedHold.orderId, code);
      setPromoCode(order.promoCode);
      setPromoAmountCents(order.promoAmountCents ?? null);
      setPromoExclusive(order.promoExclusive ?? null);
    } catch {
      setPromoError("That promo code could not be attached.");
    } finally {
      setAttaching(false);
    }
  }

  function priceLines(seatIds: number[]) {
    if (seatMap == null) {
      return null;
    }
    return (
      <ul className="price-lines" aria-label="Price">
        {priceUnavailable ? (
          <li>The price did not load.</li>
        ) : (
          orderPriceLines(
            seatMap,
            seatIds,
            pricedAt,
            saleTerms,
            promoCode,
            promoAmountCents,
            promoExclusive,
          ).map((line) => <li key={line}>{line}</li>)
        )}
      </ul>
    );
  }

  const openedEvent = events.find((event) => event.id === openEventId) ?? null;
  const ticketTotalCents =
    seatMap == null
      ? 0
      : selectedSeatIds.reduce((sum, id) => {
          const chosen = seatById(seatMap, id);
          return chosen == null ? sum : sum + currentPriceCents(seatMap, chosen, pricedAt);
        }, 0);
  const joinWaitlistShown =
    seatMap != null &&
    waitlist == null &&
    offeredHolds.length === 0 &&
    seatMap.sections.every((section) => section.seats.every((seat) => seat.state !== "free"));
  const hasUnresolvedRefund =
    paidOrder?.refunds.some((refund) => refund.status === "unknown" || refund.status === "pending") ?? false;

  if (signedIn == null) {
    return (
      <main className="sign-in-screen">
        <form className="sign-in-card" onSubmit={submitSignIn}>
          <p className="sign-in-venue">Harbor Hall</p>
          <h1>Sign in</h1>
          <label>
            Email
            <input
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </label>
          <button type="submit" disabled={signingIn}>
            {signingIn ? "Signing in…" : "Sign in"}
          </button>
          {signInError != null ? <p className="error">{signInError}</p> : null}
        </form>
      </main>
    );
  }

  return (
    <main className="events-screen">
      <header className="events-mast">
        <h1>Ticket Booking</h1>
        <p className="who">Signed in as {signedIn.email}</p>
      </header>
      {openedEvent == null ? (
        <>
          <form className="events-search" onSubmit={submitSearch}>
            <label>
              Search events by name
              <input value={query} onChange={(event) => setQuery(event.target.value)} />
            </label>
            <button type="submit" disabled={searching}>
              {searching ? "Searching…" : "Search"}
            </button>
            {searchError != null ? <p className="error">{searchError}</p> : null}
          </form>
          <ul className="results">
            {events.map((event) => (
              <li key={event.id}>
                <p className="result-name">{event.name}</p>
                <p>{event.venueName}</p>
                <p>{formatWhen(event.startsAt, event.timeZone)}</p>
                <button type="button" onClick={() => void openEvent(event.id)}>
                  Open seat map
                </button>
              </li>
            ))}
          </ul>
        </>
      ) : (
        <section className="opened-event">
          <div className="opened-event-bar">
            <div>
              <h2 className="event-name">{openedEvent.name}</h2>
              <p className="event-meta">
                {openedEvent.venueName}
                <span aria-hidden="true"> · </span>
                {formatWhen(openedEvent.startsAt, openedEvent.timeZone)}
              </p>
            </div>
            <button type="button" className="secondary" onClick={backToSearch}>
              Search events
            </button>
          </div>
        </section>
      )}
      {opening ? <p className="status">Loading the seat map…</p> : null}
      {mapError != null ? <p className="error">{mapError}</p> : null}
      {paidOrder?.payment != null ? (
        <section className="tickets-screen" aria-label="Tickets">
          <h3>Tickets</h3>
          <ul>
            {paidOrder.tickets.map((ticket) => (
              <li key={ticket.seatId}>
                <label className="ticket-choice">
                  <input
                    type="checkbox"
                    checked={ticketIdsToCancel.includes(ticket.seatId)}
                    onChange={() => toggleTicket(ticket.seatId)}
                  />
                  {ticket.label}
                </label>
              </li>
            ))}
          </ul>
          <p>{paidOrder.payment.status}</p>
          <p className="amount">{formatCents(paidOrder.payment.amountCents)}</p>
          <fieldset className="refund-script">
            <legend>Result</legend>
            {(["success", "decline", "timeout", "unknown"] as const).map((mock) => (
              <label key={mock}>
                <input
                  type="radio"
                  name="refund-result"
                  value={mock}
                  checked={refundMock === mock}
                  onChange={() => setRefundMock(mock)}
                />
                {mock}
              </label>
            ))}
          </fieldset>
          {refundScript != null ? <p>{refundScript}</p> : null}
          {refundAmountCents != null ? <p className="amount">{formatCents(refundAmountCents)}</p> : null}
          <button
            type="button"
            className="primary"
            disabled={ticketIdsToCancel.length === 0}
            onClick={() => void submitCancel()}
          >
            Cancel selected
          </button>
          {hasUnresolvedRefund ? (
            <button
              type="button"
              className="secondary"
              disabled={refundMock == null}
              onClick={() => void submitReconcileRefund()}
            >
              Reconcile refund
            </button>
          ) : null}
          {cancelError != null ? <p className="error">{cancelError}</p> : null}
          {confirmedHold == null ? (
            <button type="button" className="secondary" onClick={backToSeatMap}>
              Seat map
            </button>
          ) : (
            <button type="button" className="secondary" onClick={closeTickets}>
              Return to order
            </button>
          )}
        </section>
      ) : checkoutOpen && openedEvent != null && seatMap != null && (confirmedHold != null || paymentStatus != null) ? (
        <section className="checkout-screen">
          <h3>Check out</h3>
          {confirmedHold != null ? (
            <>
              <p>{formatWhen(confirmedHold.heldUntil, openedEvent.timeZone)}</p>
              <ul>
                {confirmedHold.labels.map((label) => (
                  <li key={label}>{label}</li>
                ))}
              </ul>
              {priceLines(confirmedHold.seatIds)}
              <fieldset className="payment-result">
                <legend>Payment result</legend>
                {(["success", "decline", "timeout", "unknown"] as const).map((mock) => (
                  <label key={mock}>
                    <input
                      type="radio"
                      name="payment-result"
                      value={mock}
                      checked={paymentMock === mock}
                      onChange={() => setPaymentMock(mock)}
                    />
                    {mock}
                  </label>
                ))}
              </fieldset>
              <button type="button" className="primary" onClick={() => void submitCheckout()}>
                Check out
              </button>
              {paymentStatus === "unknown" || paymentStatus === "pending" ? (
                <button
                  type="button"
                  className="secondary"
                  disabled={paymentMock == null}
                  onClick={() => void submitReconcilePayment()}
                >
                  Reconcile payment
                </button>
              ) : null}
              {checkoutError != null ? <p className="error">{checkoutError}</p> : null}
            </>
          ) : null}
          {paymentStatus != null ? (
            <p className="payment-status" role="status">
              {paymentStatus}
            </p>
          ) : null}
          <button
            type="button"
            className="secondary"
            onClick={() => {
              setCheckoutError(null);
              setPaymentStatus(null);
              setCheckoutOpen(false);
            }}
          >
            Return to order
          </button>
        </section>
      ) : confirmedHold != null && openedEvent != null && seatMap != null ? (
        <section className="hold-screen">
          <h3>Hold</h3>
          <p>{formatWhen(confirmedHold.heldUntil, openedEvent.timeZone)}</p>
          <ul>
            {confirmedHold.labels.map((label) => (
              <li key={label}>{label}</li>
            ))}
          </ul>
          {priceLines(confirmedHold.seatIds)}
          <form className="promo" onSubmit={(event) => void submitPromo(event)}>
            <label>
              Promo code
              <input value={promoDraft} onChange={(event) => setPromoDraft(event.target.value)} />
            </label>
            <button type="submit" className="secondary" disabled={attaching}>
              {attaching ? "Attaching…" : "Attach promo code"}
            </button>
            {promoError != null ? <p className="error">{promoError}</p> : null}
          </form>
          {earlierOrder != null && earlierOrder.tickets.length > 0 ? (
            <a
              className="ticket-link"
              href={`#tickets-${earlierOrder.id}`}
              onClick={(event) => {
                event.preventDefault();
                openEarlierTickets();
              }}
            >
              Tickets {earlierOrder.tickets.map((ticket) => ticket.label).join(", ")}
            </a>
          ) : null}
          <button
            type="button"
            className="primary"
            onClick={() => {
              setPaymentStatus(null);
              setCheckoutError(null);
              setCheckoutOpen(true);
            }}
          >
            Check out
          </button>
          <button type="button" className="secondary" onClick={() => void leaveHold()}>
            Change seats
          </button>
          <button type="button" className="secondary" disabled={releasing} onClick={() => void submitReleaseHold()}>
            {releasing ? "Releasing…" : "Release hold"}
          </button>
        </section>
      ) : seatMap != null ? (
        <section className="map">
          {priceTierLine(seatMap) != null ? <p className="price-line">{priceTierLine(seatMap)}</p> : null}
          {activeHold != null ? (
            <div className="active-hold-banner" role="region" aria-label="Active hold">
              <p>
                Held for you: {activeHold.labels.join(", ")} (until{" "}
                {formatWhen(activeHold.heldUntil, seatMap.timeZone)})
              </p>
              <div className="active-hold-actions">
                <button type="button" className="secondary" onClick={() => void resumeHoldCheckout()}>
                  Check out
                </button>
                <button
                  type="button"
                  className="secondary"
                  disabled={releasing}
                  onClick={() => void submitReleaseHold()}
                >
                  {releasing ? "Releasing…" : "Release hold"}
                </button>
              </div>
            </div>
          ) : null}
          {earlierOrder != null && earlierOrder.tickets.length > 0 ? (
            <a
              className="ticket-link"
              href={`#tickets-${earlierOrder.id}`}
              onClick={(event) => {
                event.preventDefault();
                openEarlierTickets();
              }}
            >
              Tickets {earlierOrder.tickets.map((ticket) => ticket.label).join(", ")}
            </a>
          ) : null}
          <ul className="legend" aria-label="Legend">
            {legendMarks.map((item) => (
              <li key={item.word}>
                <SeatMark kind={item.mark} />
                {item.word}
              </li>
            ))}
          </ul>
          <p className="stage">Stage</p>
          {seatMap.sections.map((section) => {
            const rows = seatRows(section.seats);
            const tabStopId =
              tabStops[section.name] != null && section.seats.some((seat) => seat.id === tabStops[section.name])
                ? tabStops[section.name]
                : rows.flatMap((row) => row.seats)[0]?.id;
            return (
              <div key={section.name} className="section">
                <h3 id={`section-${section.name}`}>{section.name}</h3>
                <div className="seat-grid" role="grid" aria-labelledby={`section-${section.name}`}>
                  {rows.map((row) => (
                    <div key={row.key} className="seat-row" role="row">
                      {row.key === "other" ? null : (
                        <span className="row-label" aria-hidden="true">
                          {row.key}
                        </span>
                      )}
                      {row.seats.map((seat) => {
                        const selected = seat.state === "free" && selectedSeatIds.includes(seat.id);
                        const word = selected ? "selected" : seat.state;
                        return (
                          <div key={seat.id} role="gridcell">
                            <button
                              type="button"
                              ref={(node) => {
                                if (node == null) {
                                  seatRefs.current.delete(seat.id);
                                  return;
                                }
                                seatRefs.current.set(seat.id, node);
                              }}
                              className={`seat seat-${word}${seat.yours ? " seat-yours" : ""}${seat.id === focusedSeatId ? " seat-focused" : ""}`}
                              data-yours={seat.yours ? "true" : undefined}
                              aria-label={`${seat.label} ${seat.state === "free" ? "free" : seat.state}`}
                              aria-pressed={seat.state === "free" ? selected : undefined}
                              aria-disabled={seat.state === "free" ? undefined : true}
                              tabIndex={seat.id === tabStopId ? 0 : -1}
                              onFocus={() => {
                                setFocusedSeatId(seat.id);
                                setTabStops((current) =>
                                  current[section.name] === seat.id ? current : { ...current, [section.name]: seat.id },
                                );
                              }}
                              onBlur={() => setFocusedSeatId((current) => (current === seat.id ? null : current))}
                              onKeyDown={(event) => onSeatKeyDown(section.name, section.seats, seat, event)}
                              onClick={() => toggleSeat(seat)}
                            >
                              <span className="label">{seat.label}</span>
                              <SeatMark kind={markKind(seat.state, selected)} />
                              <span className="state">{word}</span>
                              {seat.state === "free" ? (
                                <span className="price">{formatCents(currentPriceCents(seatMap, seat, pricedAt))}</span>
                              ) : null}
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
          {waitlist != null ||
          offeredHolds.length > 0 ||
          joinError != null ||
          seatMap.sections.every((section) => section.seats.every((seat) => seat.state !== "free")) ? (
            <div className="hold-bar">
              {offeredHolds.length > 0 ? (
                <a
                  className="offered-hold"
                  href="#hold"
                  onClick={(event) => {
                    event.preventDefault();
                    return openOfferedHold();
                  }}
                >
                  Held for you: {offeredHolds.map((hold) => hold.label).join(", ")}
                </a>
              ) : waitlist != null ? (
                <p>You are number {waitlist.place} in line.</p>
              ) : seatMap.sections.every((section) => section.seats.every((seat) => seat.state !== "free")) ? (
                <button type="button" className="waitlist" onClick={() => void submitJoin()} disabled={joining}>
                  {joining ? "Joining…" : "Join waitlist"}
                </button>
              ) : null}
              {joinError != null ? <p className="error">{joinError}</p> : null}
            </div>
          ) : null}
          <div className="selection-bar">
            <div className="selection-copy">
              <p>{selectedSeatIds.length === 0 ? "Select free seats to hold." : `${selectedSeatIds.length} selected`}</p>
              {selectedSeatIds.length > 0 ? (
                <p className="ticket-total">Ticket total {formatCents(ticketTotalCents)}</p>
              ) : null}
            </div>
            <div className="hold-action">
              <button
                type="button"
                className={joinWaitlistShown ? "secondary" : "primary"}
                onClick={() => void submitHold()}
                disabled={holding || selectedSeatIds.length === 0}
              >
                {holding ? "Holding…" : "Hold seats"}
              </button>
              {holdError != null ? <p className="error">{holdError}</p> : null}
            </div>
          </div>
        </section>
      ) : null}
    </main>
  );
}
