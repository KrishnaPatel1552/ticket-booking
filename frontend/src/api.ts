export type SeatState = "free" | "held" | "blocked" | "ticket";

export type EventSummary = {
  id: number;
  name: string;
  venueId: number;
  venueName: string;
  startsAt: string;
  timeZone: string;
};

export type SeatView = {
  id: number;
  label: string;
  priceCents: number;
  earlyBirdPriceCents: number | null;
  state: SeatState;
  yours?: boolean;
  heldUntil?: string | null;
};

export type SectionSeats = {
  name: string;
  seats: SeatView[];
};

export type EventSeatMap = {
  id: number;
  name: string;
  venueName: string;
  startsAt: string;
  timeZone: string;
  earlyBirdStartsAt: string | null;
  earlyBirdEndsAt: string | null;
  sections: SectionSeats[];
  cancelled?: boolean;
  rescheduled?: boolean;
  activeHoldOrderId?: number | null;
  paidOrderIds?: number[];
};

export type SignedIn = {
  email: string;
  role: "customer" | "admin";
};

const TAB_SESSION_KEY = "ticket-booking.tab-session";

function tabToken(): string | null {
  return sessionStorage.getItem(TAB_SESSION_KEY);
}

function withTabToken(init?: HeadersInit): Headers {
  const headers = new Headers(init);
  const token = tabToken();
  if (token != null) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  return headers;
}

function call(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(path, {
    ...init,
    credentials: "omit",
    headers: withTabToken(init.headers),
  });
}

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(String(response.status));
  }
  return response.json() as Promise<T>;
}

export async function signIn(email: string, password: string): Promise<SignedIn> {
  const response = await call("/api/sign-in", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const signedIn = await readJson<SignedIn & { token: string }>(response);
  sessionStorage.setItem(TAB_SESSION_KEY, signedIn.token);
  return { email: signedIn.email, role: signedIn.role };
}

export async function currentSession(): Promise<SignedIn | null> {
  if (tabToken() == null) {
    return null;
  }
  const response = await call("/api/me");
  if (response.status === 401) {
    sessionStorage.removeItem(TAB_SESSION_KEY);
    return null;
  }
  const signedIn = await readJson<SignedIn>(response);
  return { email: signedIn.email, role: signedIn.role };
}

export async function signOut(): Promise<void> {
  const response = await call("/api/sign-out", { method: "POST" });
  if (!response.ok && response.status !== 401) {
    throw new Error(String(response.status));
  }
  sessionStorage.removeItem(TAB_SESSION_KEY);
}

export async function searchEvents(name: string): Promise<EventSummary[]> {
  const response = await call(`/api/events?name=${encodeURIComponent(name)}`);
  return readJson<EventSummary[]>(response);
}

export async function fetchSeatMap(eventId: number): Promise<EventSeatMap> {
  const response = await call(`/api/events/${eventId}`);
  return readJson<EventSeatMap>(response);
}

export type PaymentStatus = "pending" | "unknown" | "succeeded" | "declined";

export type RefundStatus = "pending" | "unknown" | "succeeded" | "failed";

export type PaymentMock = "success" | "decline" | "timeout" | "unknown";

export type HeldSeats = {
  orderId: number;
  seatIds: number[];
  heldUntil: string;
};

export type TicketView = {
  seatId: number;
  label: string;
};

export type RefundView = {
  status: RefundStatus;
  amountCents: number;
};

export type HoldView = {
  seatId: number;
  label: string;
  heldUntil: string;
};

export type OrderView = {
  id: number;
  eventId: number;
  promoCode: string | null;
  payment: { status: PaymentStatus; amountCents: number } | null;
  tickets: TicketView[];
  refunds: RefundView[];
  holds: HoldView[];
  promoAmountCents?: number | null;
  promoExclusive?: boolean | null;
};

export type WaitlistPlace = {
  place: number;
  orderId: number | null;
};

export async function joinWaitlist(eventId: number): Promise<WaitlistPlace> {
  const response = await call(`/api/events/${eventId}/waitlist`, {
    method: "POST",
  });
  if (!response.ok) {
    throw Object.assign(new Error(String(response.status)), { reason: await holdLimitReason(response) });
  }
  return response.json() as Promise<WaitlistPlace>;
}

export async function fetchWaitlist(eventId: number): Promise<WaitlistPlace | null> {
  const response = await call(`/api/events/${eventId}/waitlist`);
  if (response.status === 404) {
    return null;
  }
  return readJson<WaitlistPlace>(response);
}

export async function holdSeats(eventId: number, seatIds: number[]): Promise<HeldSeats> {
  const response = await call(`/api/events/${eventId}/holds`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ seatIds }),
  });
  if (!response.ok) {
    throw Object.assign(new Error(String(response.status)), { reason: await holdLimitReason(response) });
  }
  return response.json() as Promise<HeldSeats>;
}

async function holdLimitReason(response: Response): Promise<string | null> {
  try {
    const body = (await response.json()) as { reason?: unknown };
    return body.reason === "order-limit" || body.reason === "event-limit" ? body.reason : null;
  } catch {
    return null;
  }
}

export async function releaseOrderHolds(orderId: number): Promise<OrderView> {
  const response = await call(`/api/orders/${orderId}/holds`, {
    method: "DELETE",
  });
  return readJson<OrderView>(response);
}

export async function releaseSeatHold(orderId: number, seatId: number): Promise<OrderView> {
  const response = await call(`/api/orders/${orderId}/holds/${seatId}`, {
    method: "DELETE",
  });
  return readJson<OrderView>(response);
}

export async function attachPromoCode(orderId: number, code: string): Promise<OrderView> {
  const response = await call(`/api/orders/${orderId}/promo-code`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code }),
  });
  return readJson<OrderView>(response);
}

export async function checkout(orderId: number, seatIds: number[], mock: PaymentMock): Promise<void> {
  const response = await call(`/api/orders/${orderId}/checkout`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ seatIds, mock }),
  });
  if (!response.ok) {
    throw new Error(String(response.status));
  }
}

export async function reconcilePayment(orderId: number, lookup: PaymentMock): Promise<OrderView> {
  const response = await call(`/api/orders/${orderId}/reconcile`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ lookup }),
  });
  return readJson<OrderView>(response);
}

export async function cancelTickets(orderId: number, seatIds: number[], mock: PaymentMock): Promise<OrderView> {
  const response = await call(`/api/orders/${orderId}/cancel`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ seatIds, mock }),
  });
  return readJson<OrderView>(response);
}

export async function reconcileRefund(orderId: number, lookup: PaymentMock): Promise<OrderView> {
  const response = await call(`/api/orders/${orderId}/refunds/reconcile`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ lookup }),
  });
  return readJson<OrderView>(response);
}

export async function fetchOrder(orderId: number): Promise<OrderView> {
  const response = await call(`/api/orders/${orderId}`);
  return readJson<OrderView>(response);
}

export type SaleTermsView = {
  groupRateThreshold: number;
  groupRateAmountCents: number;
  serviceFeeCents: number;
};

export async function fetchSaleTerms(): Promise<SaleTermsView> {
  const response = await call("/api/sale-terms");
  return readJson<SaleTermsView>(response);
}
