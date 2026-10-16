import { useState, type FormEvent } from "react";
import {
  fetchSeatMap,
  holdSeats,
  searchEvents,
  signIn,
  type EventSeatMap,
  type EventSummary,
  type SeatState,
  type SeatView,
  type SignedIn,
} from "./api";

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

function seatById(map: EventSeatMap, seatId: number): SeatView | undefined {
  for (const section of map.sections) {
    const seat = section.seats.find((candidate) => candidate.id === seatId);
    if (seat != null) {
      return seat;
    }
  }
  return undefined;
}

function stateLabel(state: SeatState): string {
  switch (state) {
    case "free":
      return "Free";
    case "held":
      return "Held";
    case "blocked":
      return "Blocked";
    case "ticket":
      return "Ticket";
    default: {
      const unexpected: never = state;
      return unexpected;
    }
  }
}

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
  const [holdError, setHoldError] = useState<string | null>(null);

  async function loadSeatMap(eventId: number) {
    setOpening(true);
    setMapError(null);
    try {
      const map = await fetchSeatMap(eventId);
      setSeatMap(map);
      setSelectedSeatIds((current) => current.filter((id) => seatById(map, id)?.state === "free"));
    } catch {
      setSeatMap(null);
      setMapError("The seat map did not load.");
    } finally {
      setOpening(false);
    }
  }

  async function submitSignIn(event: FormEvent) {
    event.preventDefault();
    setSigningIn(true);
    setSignInError(null);
    try {
      const session = await signIn(email, password);
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
      setEvents(await searchEvents(name));
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
    await loadSeatMap(eventId);
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

  async function submitHold() {
    if (seatMap == null || selectedSeatIds.length === 0) {
      return;
    }
    const eventId = seatMap.id;
    setHolding(true);
    setHoldError(null);
    try {
      await holdSeats(eventId, selectedSeatIds);
      setSelectedSeatIds([]);
    } catch {
      setHoldError("Those seats could not be held.");
    } finally {
      await loadSeatMap(eventId);
      setHolding(false);
    }
  }

  if (signedIn == null) {
    return (
      <main className="page">
        <header className="mast">
          <p className="eyebrow">Harbor Hall</p>
          <h1>Sign in</h1>
        </header>
        <form className="panel" onSubmit={submitSignIn}>
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
          {signInError != null ? <p className="error">{signInError}</p> : null}
          <button type="submit" disabled={signingIn}>
            {signingIn ? "Signing in…" : "Sign in"}
          </button>
        </form>
      </main>
    );
  }

  return (
    <main className="page">
      <header className="mast">
        <p className="eyebrow">Harbor Hall</p>
        <h1>Events</h1>
        <p className="who">Signed in as {signedIn.email}</p>
      </header>
      <form className="panel search" onSubmit={submitSearch}>
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
            <div>
              <strong>{event.name}</strong>
              <span>
                {event.venueName} · {formatWhen(event.startsAt, event.timeZone)}
              </span>
            </div>
            <button type="button" onClick={() => void openEvent(event.id)}>
              Open seat map
            </button>
          </li>
        ))}
      </ul>
      {opening ? <p className="status">Loading the seat map…</p> : null}
      {mapError != null ? <p className="error">{mapError}</p> : null}
      {seatMap != null ? (
        <section className="map">
          <h2>{seatMap.name}</h2>
          <p>
            {seatMap.venueName} · {formatWhen(seatMap.startsAt, seatMap.timeZone)}
          </p>
          <div className="hold-bar">
            <button type="button" onClick={() => void submitHold()} disabled={holding || selectedSeatIds.length === 0}>
              {holding ? "Holding…" : "Hold seats"}
            </button>
            <p>{selectedSeatIds.length === 0 ? "Select free seats to hold." : `${selectedSeatIds.length} selected`}</p>
            {holdError != null ? <p className="error">{holdError}</p> : null}
          </div>
          {seatMap.sections.map((section) => (
            <div key={section.name} className="section">
              <h3>{section.name}</h3>
              <div className="seats">
                {section.seats.map((seat) => {
                  const selected = selectedSeatIds.includes(seat.id);
                  return (
                    <button
                      key={seat.id}
                      type="button"
                      className={`seat seat-${seat.state}${selected ? " seat-selected" : ""}`}
                      disabled={seat.state !== "free" || holding}
                      onClick={() => toggleSeat(seat)}
                    >
                      <span className="label">{seat.label}</span>
                      <span className="price">{formatCents(seat.priceCents)}</span>
                      {seat.earlyBirdPriceCents != null ? (
                        <span className="early">Early-bird {formatCents(seat.earlyBirdPriceCents)}</span>
                      ) : null}
                      <span className="state">{selected ? "Selected" : stateLabel(seat.state)}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </section>
      ) : null}
    </main>
  );
}
