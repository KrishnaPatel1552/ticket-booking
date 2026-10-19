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

function refusalReason(error: unknown): unknown {
  return typeof error === "object" && error != null && "reason" in error ? error.reason : null;
}

function holdRefusalMessage(error: unknown): string {
  const reason = refusalReason(error);
  if (reason === "order-limit") {
    return "Those seats are over the limit of 6 on this order.";
  }
  if (reason === "event-limit") {
    return "Those seats are over the limit of 12 for this event.";
  }
  return "Those seats could not be held.";
}

function waitlistRefusalMessage(error: unknown): string {
  if (refusalReason(error) === "event-limit") {
    return "You already have the limit of 12 tickets for this event.";
  }
  return "The waitlist did not accept that request.";
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
  paymentStatus?: PaymentStatus | null;
};

type CustomerView = "search" | "map" | "hold" | "checkout" | "tickets";

type CustomerPlace = {
  query: string;
  eventId: number | null;
  view: CustomerView;
};

function readPlace(): CustomerPlace {
  const params = new URLSearchParams(window.location.search);
  const eventRaw = params.get("event");
  const eventId = eventRaw != null && /^[1-9]\d*$/.test(eventRaw) ? Number(eventRaw) : null;
  const viewRaw = params.get("view");
  let view: CustomerView = eventId == null ? "search" : "map";
  if (eventId != null && (viewRaw === "hold" || viewRaw === "checkout" || viewRaw === "tickets")) {
    view = viewRaw;
  }
  return { query: params.get("q") ?? "", eventId, view };
}

function placeUrl(place: CustomerPlace): string {
  const params = new URLSearchParams();
  if (place.query !== "") {
    params.set("q", place.query);
  }
  if (place.eventId != null) {
    params.set("event", String(place.eventId));
  }
  if (place.view === "hold" || place.view === "checkout" || place.view === "tickets") {
    params.set("view", place.view);
  }
  const search = params.toString();
  return `${window.location.pathname}${search === "" ? "" : `?${search}`}`;
}

function writePlace(place: CustomerPlace, mode: "push" | "replace") {
  const url = placeUrl(place);
  if (`${window.location.pathname}${window.location.search}` === url) {
    return;
  }
  if (mode === "push") {
    window.history.pushState(null, "", url);
    return;
  }
  window.history.replaceState(null, "", url);
}

function summaryFromMap(map: EventSeatMap): EventSummary {
  return {
    id: map.id,
    name: map.name,
    venueId: 0,
    venueName: map.venueName,
    startsAt: map.startsAt,
    timeZone: map.timeZone,
  };
}

export default function App() {
  const [sessionReady, setSessionReady] = useState(false);
  const [signedIn, setSignedIn] = useState<SignedIn | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [signInError, setSignInError] = useState<string | null>(null);
  const [signingIn, setSigningIn] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [signOutError, setSignOutError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const queryRef = useRef("");
  queryRef.current = query;
  const signedInRef = useRef<SignedIn | null>(null);
  const savedPlaceRef = useRef<(place: CustomerPlace) => Promise<void>>(async () => {});
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
  const [paidOrders, setPaidOrders] = useState<OrderView[]>([]);
  const [ticketsOpen, setTicketsOpen] = useState(false);
  const [promoCode, setPromoCode] = useState<string | null>(null);
  const [promoAmountCents, setPromoAmountCents] = useState<number | null>(null);
  const [promoExclusive, setPromoExclusive] = useState<boolean | null>(null);
  const [promoDraft, setPromoDraft] = useState("");
  const [promoError, setPromoError] = useState<string | null>(null);
  const [attaching, setAttaching] = useState(false);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [paymentMock, setPaymentMock] = useState<PaymentMock | null>(null);
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus | null>(null);
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

  useEffect(() => {
    let cancelled = false;
    api.currentSession()
      .then(async (session) => {
        if (cancelled) {
          return;
        }
        signedInRef.current = session;
        setSignedIn(session);
        if (session != null) {
          await savedPlaceRef.current(readPlace());
        }
      })
      .catch(() => {
        if (!cancelled) {
          signedInRef.current = null;
          setSignedIn(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setSessionReady(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    function onPopState() {
      if (signedInRef.current == null) {
        return;
      }
      void savedPlaceRef.current(readPlace());
    }
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  function leaveCustomer() {
    setEmail("");
    setPassword("");
    setSignInError(null);
    setSignOutError(null);
    setQuery("");
    setEvents([]);
    setSearchError(null);
    setSearching(false);
    setOpenEventId(null);
    setSeatMap(null);
    setMapError(null);
    setOpening(false);
    setSelectedSeatIds([]);
    setHolding(false);
    setReleasing(false);
    setHoldError(null);
    setConfirmedHold(null);
    setActiveHold(null);
    setSaleTerms(null);
    setPriceUnavailable(false);
    setPaidOrders([]);
    setTicketsOpen(false);
    setPromoCode(null);
    setPromoAmountCents(null);
    setPromoExclusive(null);
    setPromoDraft("");
    setPromoError(null);
    setAttaching(false);
    setCheckoutOpen(false);
    setPaymentMock(null);
    setPaymentStatus(null);
    setTicketIdsToCancel([]);
    setRefundMock(null);
    setRefundScript(null);
    setRefundAmountCents(null);
    setCancelError(null);
    setCheckoutError(null);
    setTabStops({});
    setFocusedSeatId(null);
    setPricedAt(0);
    setWaitlist(null);
    setOfferedHolds([]);
    setJoining(false);
    setJoinError(null);
    seatRefs.current.clear();
    pendingFocusId.current = null;
  }

  function remember(
    view: CustomerView,
    eventId: number | null,
    mode: "push" | "replace" = "push",
    nextQuery = queryRef.current.trim(),
  ) {
    writePlace({ query: nextQuery, eventId, view }, mode);
  }

  async function loadSeatMap(eventId: number) {
    setOpening(true);
    setMapError(null);
    try {
      const map = await api.fetchSeatMap(eventId);
      setSeatMap(map);
      setEvents((current) =>
        current.some((event) => event.id === map.id) ? current : [summaryFromMap(map), ...current],
      );
      setPricedAt(readClock());
      setSelectedSeatIds((current) => current.filter((id) => seatById(map, id)?.state === "free"));
      await loadWaitlist(eventId);
      return await syncCustomerEventOrders(map);
    } catch {
      setSeatMap(null);
      setMapError("The seat map did not load.");
      return null;
    } finally {
      setOpening(false);
    }
  }

  async function syncCustomerEventOrders(map: EventSeatMap) {
    let hold: ActiveHoldState | null = null;
    if (map.activeHoldOrderId != null) {
      try {
        const order = await api.fetchOrder(map.activeHoldOrderId);
        if (order != null && order.holds.length > 0) {
          hold = {
            orderId: order.id,
            labels: order.holds.map((item) => item.label),
            seatIds: order.holds.map((item) => item.seatId),
            heldUntil: order.holds[0].heldUntil,
            paymentStatus: order.payment?.status ?? null,
          };
          setPromoCode(order.promoCode);
          setPromoAmountCents(order.promoAmountCents ?? null);
          setPromoExclusive(order.promoExclusive ?? null);
        }
      } catch {
        hold = null;
      }
    }
    setActiveHold(hold);
    if (map.paidOrderIds == null) {
      return { hold, paid: [] as OrderView[] };
    }
    const paidIds = map.paidOrderIds;
    if (paidIds.length === 0) {
      setPaidOrders([]);
      return { hold, paid: [] as OrderView[] };
    }
    const loaded = await Promise.all(
      paidIds.map(async (orderId) => {
        try {
          return await api.fetchOrder(orderId);
        } catch {
          return null;
        }
      }),
    );
    const paid = loaded
      .filter((order): order is OrderView => order != null && order.tickets.length > 0)
      .sort((left, right) => left.id - right.id);
    setPaidOrders(paid);
    return { hold, paid };
  }

  async function openSavedPlace(place: CustomerPlace) {
    setTicketsOpen(false);
    setCheckoutOpen(false);
    setConfirmedHold(null);
    setPaymentStatus(null);
    setPaymentMock(null);
    setMapError(null);
    setQuery(place.query);
    if (place.eventId == null) {
      setOpenEventId(null);
      setSeatMap(null);
      setActiveHold(null);
      setPaidOrders([]);
      if (place.query === "") {
        setEvents([]);
        setSearchError(null);
        return;
      }
      setSearching(true);
      setSearchError(null);
      try {
        const found = await api.searchEvents(place.query);
        setEvents(found);
        if (found.length === 0) {
          setSearchError("No events match that name.");
        }
      } catch {
        setEvents([]);
        setSearchError("The search did not complete.");
      } finally {
        setSearching(false);
      }
      return;
    }
    if (place.query !== "") {
      try {
        const found = await api.searchEvents(place.query);
        setEvents(found);
        if (found.length === 0) {
          setSearchError("No events match that name.");
        }
      } catch {
        setSearchError("The search did not complete.");
      }
    }
    setOpenEventId(place.eventId);
    const synced = await loadSeatMap(place.eventId);
    if (synced == null) {
      return;
    }
    const visibleTickets = synced.paid.some((order) => order.payment != null && order.tickets.length > 0);
    if (synced.hold != null && (place.view === "hold" || place.view === "checkout" || place.view === "tickets")) {
      setConfirmedHold(synced.hold);
      setActiveHold(synced.hold);
      if (place.view === "hold" || place.view === "checkout") {
        setSaleTerms(await loadSaleTerms());
      }
      if (place.view === "checkout") {
        setPaymentStatus(synced.hold.paymentStatus ?? null);
        setCheckoutOpen(true);
        return;
      }
      if (place.view === "hold") {
        return;
      }
    }
    if (place.view === "tickets" && visibleTickets) {
      setTicketsOpen(true);
      return;
    }
    if (place.view !== "map") {
      writePlace({ ...place, view: "map" }, "replace");
    }
  }

  savedPlaceRef.current = openSavedPlace;

  function storePaidOrder(order: OrderView) {
    setPaidOrders((current) => {
      const rest = current.filter((item) => item.id !== order.id);
      return [...rest, order].sort((left, right) => left.id - right.id);
    });
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
    } catch (error) {
      setJoinError(waitlistRefusalMessage(error));
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
      signedInRef.current = session;
      setSignedIn(session);
      setPassword("");
    } catch {
      setSignedIn(null);
      setSignInError("That email and password did not sign you in.");
    } finally {
      setSigningIn(false);
    }
  }

  async function submitSignOut() {
    setSigningOut(true);
    setSignOutError(null);
    try {
      await api.signOut();
      leaveCustomer();
      signedInRef.current = null;
      writePlace({ query: "", eventId: null, view: "search" }, "replace");
      setSignedIn(null);
    } catch {
      setSignOutError("Sign out did not finish.");
    } finally {
      setSigningOut(false);
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
    remember("search", null, "push", name);
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
    remember("map", eventId);
    setOpenEventId(eventId);
    setSeatMap(null);
    setSelectedSeatIds([]);
    setHoldError(null);
    setConfirmedHold(null);
    setActiveHold(null);
    setSaleTerms(null);
    setPriceUnavailable(false);
    setPaidOrders([]);
    setTicketsOpen(false);
    setPromoCode(null);
    setPromoAmountCents(null);
    setPromoExclusive(null);
    setPromoDraft("");
    setPromoError(null);
    setCheckoutOpen(false);
    setPaymentMock(null);
    setPaymentStatus(null);
    setTicketsOpen(false);
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
    remember("search", null);
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
    setPaidOrders([]);
    setTicketsOpen(false);
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
    if (seatMap != null) {
      remember("hold", seatMap.id);
    }
  }

  async function resumeHoldCheckout() {
    if (activeHold == null) {
      return;
    }
    if (saleTerms == null && !priceUnavailable) {
      setSaleTerms(await loadSaleTerms());
    }
    setConfirmedHold(activeHold);
    setPaymentStatus(activeHold.paymentStatus ?? null);
    setCheckoutError(null);
    setCheckoutOpen(true);
    if (seatMap != null) {
      remember("checkout", seatMap.id);
    }
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
      remember("hold", eventId);
    } catch (error) {
      setHoldError(holdRefusalMessage(error));
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
        remember("map", eventId);
      }
    } catch {
      if (target.paymentStatus === "unknown" || target.paymentStatus === "pending") {
        setHoldError("Those seats could not be released while payment is unresolved. Reconcile payment on Check out first.");
      } else {
        setHoldError("Those seats could not be released.");
      }
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
    if (!paidOrders.some((order) => order.tickets.length > 0)) {
      return;
    }
    setTicketsOpen(true);
    setTicketIdsToCancel([]);
    setRefundMock(null);
    setRefundScript(null);
    setRefundAmountCents(null);
    setCancelError(null);
    const eventId = seatMap?.id ?? openEventId;
    if (eventId != null) {
      remember("tickets", eventId);
    }
  }

  function closeTickets() {
    setTicketsOpen(false);
    setTicketIdsToCancel([]);
    setRefundMock(null);
    setRefundScript(null);
    setRefundAmountCents(null);
    setCancelError(null);
  }

  function markBookedSeats(seatIds: number[]) {
    const booked = new Set(seatIds);
    setSeatMap((current) => {
      if (current == null) {
        return current;
      }
      return {
        ...current,
        sections: current.sections.map((section) => ({
          ...section,
          seats: section.seats.map((seat) =>
            booked.has(seat.id) ? { ...seat, state: "ticket", yours: true } : seat,
          ),
        })),
      };
    });
  }

  async function backToSeatMap() {
    const eventId = seatMap?.id ?? openEventId;
    if (eventId != null) {
      await loadSeatMap(eventId);
    }
    closeTickets();
    setConfirmedHold(null);
    setCheckoutOpen(false);
    if (eventId != null) {
      remember("map", eventId);
    }
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
    setTicketsOpen(false);
    setTicketIdsToCancel([]);
    setRefundMock(null);
    setRefundScript(null);
    setRefundAmountCents(null);
    setCancelError(null);
    setCheckoutError(null);
    if (eventId != null) {
      await loadSeatMap(eventId);
      remember("map", eventId);
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
      if (status === "succeeded" && order.tickets.length > 0) {
        storePaidOrder(order);
      }
      if (status === "succeeded") {
        markBookedSeats(seatIds);
        setTicketsOpen(true);
        setConfirmedHold(null);
        setActiveHold(null);
        setTicketIdsToCancel([]);
        setRefundMock(null);
        setRefundScript(null);
        setRefundAmountCents(null);
        setCancelError(null);
        setCheckoutOpen(false);
        if (eventId != null) {
          remember("tickets", eventId);
        }
        return;
      }
      setPaymentStatus(status);
      if (status === "declined") {
        setConfirmedHold(null);
        setActiveHold(null);
        if (eventId != null) {
          await loadSeatMap(eventId);
        }
      } else {
        setConfirmedHold((current) => (current == null ? null : { ...current, paymentStatus: status }));
        setActiveHold((current) => (current == null ? null : { ...current, paymentStatus: status }));
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
      if (status === "succeeded" && order.tickets.length > 0) {
        storePaidOrder(order);
      }
      if (status === "succeeded") {
        markBookedSeats(order.tickets.map((ticket) => ticket.seatId));
        setTicketsOpen(true);
        setConfirmedHold(null);
        setActiveHold(null);
        setTicketIdsToCancel([]);
        setRefundMock(null);
        setRefundScript(null);
        setRefundAmountCents(null);
        setCancelError(null);
        setCheckoutOpen(false);
        if (eventId != null) {
          remember("tickets", eventId);
        }
        return;
      }
      setPaymentStatus(status);
      if (status === "declined") {
        setConfirmedHold(null);
        setActiveHold(null);
        if (eventId != null) {
          await loadSeatMap(eventId);
        }
      } else {
        setConfirmedHold((current) => (current == null ? null : { ...current, paymentStatus: status }));
        setActiveHold((current) => (current == null ? null : { ...current, paymentStatus: status }));
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
    if (refundMock == null || ticketIdsToCancel.length === 0) {
      return;
    }
    const selections = paidOrders
      .map((order) => ({
        orderId: order.id,
        seatIds: order.tickets.map((ticket) => ticket.seatId).filter((seatId) => ticketIdsToCancel.includes(seatId)),
      }))
      .filter((selection) => selection.seatIds.length > 0);
    if (selections.length === 0) {
      return;
    }
    const mock = refundMock;
    setCancelError(null);
    try {
      let latestRefundAmount: number | null = null;
      for (const selection of selections) {
        const order = await api.cancelTickets(selection.orderId, selection.seatIds, mock);
        storePaidOrder(order);
        const latestRefund = order.refunds[order.refunds.length - 1];
        if (latestRefund != null) {
          latestRefundAmount = latestRefund.amountCents;
        }
      }
      setTicketIdsToCancel([]);
      setRefundScript(mock);
      setRefundAmountCents(latestRefundAmount);
    } catch {
      setCancelError("Those tickets could not be cancelled.");
    }
  }

  async function submitReconcileRefund() {
    if (refundMock == null || typeof api.reconcileRefund !== "function") {
      return;
    }
    const unresolved = paidOrders.filter((order) =>
      order.refunds.some((refund) => refund.status === "unknown" || refund.status === "pending"),
    );
    if (unresolved.length === 0) {
      return;
    }
    setCancelError(null);
    try {
      let latestRefundAmount: number | null = null;
      for (const current of unresolved) {
        const order = await api.reconcileRefund(current.id, refundMock);
        storePaidOrder(order);
        const latestRefund = order.refunds[order.refunds.length - 1];
        if (latestRefund != null) {
          latestRefundAmount = latestRefund.amountCents;
        }
      }
      setRefundScript(refundMock);
      setRefundAmountCents(latestRefundAmount);
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

  const openedEvent =
    events.find((event) => event.id === openEventId) ??
    (seatMap != null && seatMap.id === openEventId ? summaryFromMap(seatMap) : null);
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
  const ticketLinkLabels = paidOrders.flatMap((order) => order.tickets.map((ticket) => ticket.label)).join(", ");
  const visiblePaidOrders = paidOrders.filter((order) => order.payment != null);
  const hasUnresolvedRefund = visiblePaidOrders.some((order) =>
    order.refunds.some((refund) => refund.status === "unknown" || refund.status === "pending"),
  );

  if (!sessionReady) {
    return <main className="sign-in-screen" />;
  }

  if (signedIn == null) {
    return (
      <main className="sign-in-screen">
        <form className="sign-in-card" onSubmit={submitSignIn}>
          <p className="sign-in-product">Ticket booking system</p>
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
        <div>
          <h1>Ticket Booking</h1>
          <p className="who">Signed in as {signedIn.email}</p>
        </div>
        <button type="button" disabled={signingOut} onClick={() => void submitSignOut()}>
          {signingOut ? "Signing out…" : "Sign out"}
        </button>
        {signOutError != null ? <p className="error">{signOutError}</p> : null}
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
      {ticketsOpen && visiblePaidOrders.length > 0 ? (
        <section className="tickets-screen" aria-label="Tickets">
          <h3>Tickets</h3>
          {visiblePaidOrders.map((order) => (
            <div key={order.id} className="ticket-order">
              <h4>Order {order.id}</h4>
              <ul>
                {order.tickets.map((ticket) => (
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
              <p>{order.payment?.status}</p>
              <p className="amount">{formatCents(order.payment?.amountCents ?? 0)}</p>
            </div>
          ))}
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
            <button
              type="button"
              className="secondary"
              onClick={() => {
                closeTickets();
                const eventId = seatMap?.id ?? openEventId;
                if (eventId != null) {
                  remember("hold", eventId);
                }
              }}
            >
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
              <button
                type="button"
                className="primary"
                disabled={paymentStatus === "unknown" || paymentStatus === "pending"}
                onClick={() => void submitCheckout()}
              >
                Check out
              </button>
              {paymentStatus === "unknown" || paymentStatus === "pending" ? (
                <button
                  type="button"
                  className="primary"
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
              const eventId = seatMap?.id ?? openEventId;
              if (eventId != null) {
                remember(confirmedHold == null ? "map" : "hold", eventId);
              }
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
          {ticketLinkLabels !== "" ? (
            <a
              className="ticket-link"
              href="#tickets"
              onClick={(event) => {
                event.preventDefault();
                openEarlierTickets();
              }}
            >
              Tickets {ticketLinkLabels}
            </a>
          ) : null}
          <button
            type="button"
            className="primary"
            onClick={() => {
              setPaymentStatus(confirmedHold.paymentStatus ?? null);
              setCheckoutError(null);
              setCheckoutOpen(true);
              if (seatMap != null) {
                remember("checkout", seatMap.id);
              }
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
          {holdError != null ? <p className="error">{holdError}</p> : null}
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
              {holdError != null ? <p className="error">{holdError}</p> : null}
            </div>
          ) : null}
          {ticketLinkLabels !== "" ? (
            <a
              className="ticket-link"
              href="#tickets"
              onClick={(event) => {
                event.preventDefault();
                openEarlierTickets();
              }}
            >
              Tickets {ticketLinkLabels}
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
