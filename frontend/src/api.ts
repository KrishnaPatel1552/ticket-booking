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

export async function holdSeats(eventId: number, seatIds: number[]): Promise<void> {
  const response = await fetch(`/api/events/${eventId}/holds`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ seatIds }),
  });
  if (!response.ok) {
    throw new Error(String(response.status));
  }
}
