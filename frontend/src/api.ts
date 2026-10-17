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
};

export type SignedIn = {
  email: string;
  role: "customer" | "admin";
};

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(String(response.status));
  }
  return response.json() as Promise<T>;
}

export async function signIn(email: string, password: string): Promise<SignedIn> {
  const response = await fetch("/api/sign-in", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ email, password }),
  });
  return readJson<SignedIn>(response);
}

export async function searchEvents(name: string): Promise<EventSummary[]> {
  const response = await fetch(`/api/events?name=${encodeURIComponent(name)}`, {
    credentials: "include",
  });
  return readJson<EventSummary[]>(response);
}

export async function fetchSeatMap(eventId: number): Promise<EventSeatMap> {
  const response = await fetch(`/api/events/${eventId}`, {
    credentials: "include",
  });
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
};

export type WaitlistPlace = {
  place: number;
  orderId: number | null;
};

export async function joinWaitlist(eventId: number): Promise<WaitlistPlace> {
  const response = await fetch(`/api/events/${eventId}/waitlist`, {
    method: "POST",
    credentials: "include",
  });
  return readJson<WaitlistPlace>(response);
}

export async function fetchWaitlist(eventId: number): Promise<WaitlistPlace | null> {
  const response = await fetch(`/api/events/${eventId}/waitlist`, {
    credentials: "include",
  });
  if (response.status === 404) {
    return null;
  }
  return readJson<WaitlistPlace>(response);
}

export async function holdSeats(eventId: number, seatIds: number[]): Promise<HeldSeats> {
  const response = await fetch(`/api/events/${eventId}/holds`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ seatIds }),
  });
  return readJson<HeldSeats>(response);
}

export async function attachPromoCode(orderId: number, code: string): Promise<OrderView> {
  const response = await fetch(`/api/orders/${orderId}/promo-code`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ code }),
  });
  return readJson<OrderView>(response);
}

export async function checkout(orderId: number, seatIds: number[], mock: PaymentMock): Promise<void> {
  const response = await fetch(`/api/orders/${orderId}/checkout`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ seatIds, mock }),
  });
  if (!response.ok) {
    throw new Error(String(response.status));
  }
}

export async function cancelTickets(orderId: number, seatIds: number[], mock: PaymentMock): Promise<OrderView> {
  const response = await fetch(`/api/orders/${orderId}/cancel`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ seatIds, mock }),
  });
  return readJson<OrderView>(response);
}

export async function fetchOrder(orderId: number): Promise<OrderView> {
  const response = await fetch(`/api/orders/${orderId}`, {
    credentials: "include",
  });
  return readJson<OrderView>(response);
}

export type SaleTermsView = {
  groupRateThreshold: number;
  groupRateAmountCents: number;
  serviceFeeCents: number;
};

export async function fetchSaleTerms(): Promise<SaleTermsView> {
  const response = await fetch("/api/sale-terms", {
    credentials: "include",
  });
  return readJson<SaleTermsView>(response);
}
