# Ticket Booking

A Spring Boot API and a React customer site, with PostgreSQL as the database. The commands below are the ones that ran on this machine.

## Java

Java 17 is required. Maven on this machine is `/opt/homebrew/bin/mvn`.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/postgresql@16/bin:$PATH"
```

Leave those exports in the shell you use for the database commands, the API, and the tests. `java -version` then prints OpenJDK 17.0.18. `/opt/homebrew/bin/mvn -version` prints Apache Maven 3.10.0 on that same Java.

## PostgreSQL

PostgreSQL 16 is installed with Homebrew. It listens on `localhost:5432`. The local user is `krishna`, with no password.

```bash
brew services start postgresql@16
```

On this machine that command reported the service already started and exited 0.

`src/main/resources/application.yml` and `src/test/resources/application-test.yml` hardcode the username `krishna` and set no password. A different local user does not work unless you edit those two files.

Create `ticket_booking` for the app and `ticket_booking_test` for the tests. These commands create each database when it is missing, and they leave an existing database in place.

```bash
psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'ticket_booking'" | grep -q 1 || createdb ticket_booking
psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'ticket_booking_test'" | grep -q 1 || createdb ticket_booking_test
```

Flyway builds the schema when the app or the tests start. `ddl-auto` is `validate`, so Hibernate checks the schema and does not create or alter it. Starting the API applied the migrations through version 17.

## API

From the repository root:

```bash
/opt/homebrew/bin/mvn spring-boot:run
```

The API listens on port 8080. Maven may print a 401 while looking up Flyway community metadata. The process continued, and Tomcat started on port 8080.

## Site

The React site is `frontend/`. Node on this machine is v22.17.1 and npm is 10.9.2, both already on `PATH`.

```bash
cd frontend
npm install
npm run dev
```

Vite prints `http://localhost:5173/`. It proxies `/api` to `http://localhost:8080`. With the API running, signing in through `http://localhost:5173/api/sign-in` returned the same session as signing in on port 8080.

## Tests

From the repository root, with the same `JAVA_HOME` and `PATH`:

```bash
/opt/homebrew/bin/mvn test
```

The tests use `ticket_booking_test`. They are HTTP tests on real PostgreSQL, including the concurrent cases, which open two sessions. `mvn test` finished with 135 tests, 0 failures, and `BUILD SUCCESS`. The same Flyway metadata 401 can appear in the log. It did not fail the build.

## Starter sign-in

The site cannot be used until you sign in. Starter data loads these local accounts. The password for each is `password`.

- Customers: `ada@example.com` and `grace@example.com`
- Admin: `admin@example.com`

A wrong password returns 401 and leaves you signed out. Starter data also loads the venue Harbor Hall and the event Harbor Lights, so a signed-in customer has an event to open.
