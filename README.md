# Ticket Booking

A live-event ticket booking system built with **Java 17 (Spring Boot 3.5)**, **React 19 (TypeScript + Vite)**, and **PostgreSQL 16**.

## Prerequisites

- **Java 17** and **Maven** (`/opt/homebrew/opt/openjdk@17` and `/opt/homebrew/bin/mvn` on macOS Homebrew)
- **PostgreSQL 16** listening on `localhost:5432`
- **Node.js 22+** and **npm 10+**

On macOS with Homebrew, export Java 17 and PostgreSQL 16 on `PATH` in the shells used to run the database setup, the API, and the tests:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/postgresql@16/bin:$PATH"
```

## 1. PostgreSQL Setup

Start PostgreSQL:

```bash
brew services start postgresql@16
```

Create the application database (`ticket_booking`) and the integration test database (`ticket_booking_test`) if they do not already exist:

```bash
psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'ticket_booking'" | grep -q 1 || createdb ticket_booking
psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'ticket_booking_test'" | grep -q 1 || createdb ticket_booking_test
```

`src/main/resources/application.yml` and `src/test/resources/application-test.yml` connect to `localhost:5432` with username `krishna` and no password. To run under a different local PostgreSQL user without editing those files, export `SPRING_DATASOURCE_USERNAME` (and `SPRING_DATASOURCE_PASSWORD` if your server requires one):

```bash
export SPRING_DATASOURCE_USERNAME="$(whoami)"
```

Flyway owns the schema (`src/main/resources/db/migration/V1__account.sql` through `V19__waitlist_reentry_index.sql`) and applies all 19 migrations automatically when the API or test suite starts. Hibernate runs with `ddl-auto: validate`, so it checks the schema and never creates or alters tables itself.

## 2. Run the API

From the repository root:

```bash
/opt/homebrew/bin/mvn spring-boot:run
```

The API listens on `http://localhost:8080`. (Maven may print a harmless `401` warning while checking Flyway community metadata on GitHub Packages; the build and server startup continue normally.)

## 3. Run the Customer Site

In a second terminal, from `frontend/`:

```bash
cd frontend
npm install
npm run dev
```

Vite serves the customer site at `http://localhost:5173/` and proxies `/api` requests to `http://localhost:8080`.

## 4. Starter Accounts and Multi-Tab Testing

The customer site requires sign-in. `StarterData` seeds these accounts on startup (the password for each is `password`):

- **Customers:** `ada@example.com` and `grace@example.com`
- **Admin:** `admin@example.com`

A wrong password returns `401 Unauthorized` and leaves you signed out. Starter data also seeds the venue **Harbor Hall** (Orchestra `A1`–`A12` at the Standard tier, Balcony `B1`–`B8` at the Balcony tier), the event **Harbor Lights**, and the default sale terms (group threshold of 4 tickets for $5.00 off, plus a $1.00 flat service fee per order).

Each browser tab stores its own sign-in bearer token in `sessionStorage`, so you can open `http://localhost:5173/` in two tabs and stay signed in as `ada@example.com` in one tab and `grace@example.com` in the other at the same time.

## 5. Run Admin Actions (CLI Helper)

Admin setup and overrides are authenticated API endpoints (`/api/admin/**`). You can call them directly or use `scripts/admin.mjs` with the JSON payloads in `scripts/admin/` while the API is running on port 8080:

```bash
node scripts/admin.mjs sales
node scripts/admin.mjs audit
node scripts/admin.mjs event
node scripts/admin.mjs price-tier
node scripts/admin.mjs promo
node scripts/admin.mjs block
node scripts/admin.mjs release
```

Destructive overrides (`cancellation`, `reschedule`, and `exceptional-refund`) require `"confirm": true` in their JSON file in `scripts/admin/`. You can also run `node scripts/demo-block-release.mjs` for an interactive step-by-step block and release walkthrough.

## 6. Run the Tests

### PostgreSQL HTTP & Concurrency Tests (Backend)

From the repository root, with `JAVA_HOME` and `PATH` set as above:

```bash
/opt/homebrew/bin/mvn test
```

The suite runs **146 HTTP integration tests** across 11 test classes against the real `ticket_booking_test` PostgreSQL database, including concurrent multi-session tests for double-sell prevention on the same seat, same-customer cap locking across different seats, hold-versus-block races, and promo-code limit contention.

### Customer Site Tests (Frontend)

From `frontend/`:

```bash
cd frontend
npm test
```

Vitest runs **63 tests** across 8 test files covering sign-in, per-tab session tokens, event search, seat-map selection, holds and cap messages, order review and promo codes, scripted checkout and reconciliation, partial/full ticket cancellation, and the sold-out waitlist flow.
