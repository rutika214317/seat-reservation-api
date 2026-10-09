# Seat Reservation API

Spring Boot 3 / Java 17 API for assigned-seat reservations.

Source code is organized by technical layer under
`com.example.seatreservation`: `controller`, `service`, `model`, `dto`,
`repository`, and `config`.

## Health, metrics, and logs

- `GET /actuator/health/liveness` reports whether the process is alive.
- `GET /actuator/health/readiness` includes the database health check and is
  `DOWN` if the database cannot be reached. Health responses do not expose
  component details.
- `GET /actuator/prometheus` exposes Prometheus metrics without authentication
  for scraper integration. Other Actuator endpoints require authentication.
- `seat_reservations_confirmed_total` counts newly committed reservations.
- `seat_reservations_declined_total{reason="seat-taken"}` and
  `seat_reservations_declined_total{reason="per-user-limit"}` count domain
  declines. Successful idempotency replays are reported separately by
  `seat_reservations_idempotent_replays_total`; they do not create another
  reservation or increase the confirmed count.
- `seat_reservation_seats_available{show_id="..."}` is read from the database
  and reflects the current available-seat count for each show.

HTTP access logs are emitted as JSON to standard output. Each request carries an
`X-Request-Id` response header; valid UUID request IDs supplied with the
`X-Request-Id` request header are echoed and included in the structured log, and
invalid IDs are replaced with a generated UUID. Metrics and health are public
for platform scraping/probing; configure platform-level access controls if the
deployment requires these endpoints to be private.

The repository-root [prometheus.yml](./prometheus.yml) scrapes the local API
every 15 seconds when Prometheus runs in Docker Desktop and the API runs on the
host. Start Prometheus with:

```bash
docker run --rm -p 9090:9090 \
  -v "${PWD}/prometheus.yml:/etc/prometheus/prometheus.yml" \
  prom/prometheus
```

On Windows PowerShell, use `${PWD}` as above with a recent Docker Desktop, or
replace it with the absolute path to this repository. Open
`http://localhost:9090` and query `seat_reservations_confirmed_total`.

## Run locally

```bash
./mvnw spring-boot:run
```

On Windows, run `mvnw.cmd spring-boot:run`. Local development uses an in-memory H2
database. The API is available at `http://localhost:8080`; Swagger UI is at
`http://localhost:8080/swagger-ui.html`.

## Run with Docker Compose

Docker Compose starts the API, PostgreSQL, and Prometheus:

```bash
docker compose up --build
```

The API is at `http://localhost:8080`, Prometheus is at
`http://localhost:9090`, and the database data is kept in a named volume. The
Compose defaults are for local development only. Set `ADMIN_PASSWORD` and
`POSTGRES_PASSWORD` to private values for any shared environment. The
`APP_SECURITY_RESERVATIONUSERS` default configures eight burst-test buyers, one
limit-test user, and one idempotency-test user; do not use these sample
credentials outside local development.

The runtime image uses Java 17, runs as a non-root account, and checks readiness
through `/actuator/health/readiness`. Readiness includes the database check, so
the API is marked unhealthy when PostgreSQL is unavailable.

## Run the burst test

With the Compose stack running, execute the repository-root script from Bash
(for example, Git Bash, WSL, macOS, or Linux):

```bash
bash burst.sh http://localhost:8080
```

It creates a fresh show, defaults to 20,000 concurrent requests for one hot
seat, concurrently tries ten other seats as one user to test the four-seat
limit, retries the same idempotency key, and checks the final seat-count
reconciliation. The script prints successful reservations, declines by reason,
5xx and transport errors, then exits nonzero if an invariant fails.
`BURST_REQUESTS` and `BURST_WORKERS` can adjust request count and parallelism.
For another deployment, set `ADMIN_USERNAME`, `ADMIN_PASSWORD`, and
`BURST_USERS` to accounts configured in that service before creating the show.
`LIMIT_USER` and `RETRY_USER` must also name configured accounts. Credentials
must be comma-separated `username:password` pairs in `BURST_USERS`; the script
requires `curl`, `bash`, and standard Unix utilities.

## Show endpoints

- `POST /shows` creates a show and all its seats in the `available` state.
- `GET /shows/{id}` returns each seat's state and availability counts.

Example create body:

```json
{
  "name": "friday-night",
  "seats": ["A1", "A2", "A3"],
  "price_paise": 25000
}
```

Seat labels must be unique within the request. Prices are non-negative integer
paise. Creating a show requires HTTP Basic credentials for the configured admin;
retrieving a show is public. In Swagger UI, use **Authorize** and enter the admin
username and password.

The local admin defaults are `admin` / `change-this-local-password`. Before
deployment, set `ADMIN_USERNAME` and `ADMIN_PASSWORD` to private credentials.
Passwords are stored in memory as BCrypt hashes. Basic authentication must only
be used over HTTPS outside local development. No user-registration or credential
issuance endpoint is provided. Reservation users are configured as a
comma-separated `username:password` list in `RESERVATION_USERS`; the local default
is `user:change-this-local-password`. Each authenticated username is the user
identity. A `user_id` field in a request is ignored.

## Reservation endpoints

- `POST /shows/{id}/reserve` reserves seats. Supply `Idempotency-Key` or
  `idempotency_key` in the JSON body. The key is scoped to the authenticated user
  and show.
- `POST /reservations/{id}/cancel` cancels a reservation; only its owner may do
  so. Cancellation releases the seats for rebooking.

Reservation requests are all-or-nothing: if any requested seat is missing or no
longer available, none are reserved and the response is `409 Conflict`. A user
may reserve at most four seats for a show. Prices and amounts are integer paise.

Each show creates a persistent quota-lock row for every configured account. A
reservation transaction locks only that user's show quota row and the requested
seat rows (in sorted label order) before checking the idempotency key, seat
states, and per-user count. It then conditionally updates only `available` seat
rows and persists the reservation in the same transaction. This serializes quota
decisions for one user without serializing unrelated buyers; sorted seat locks
avoid deadlocks for overlapping multi-seat requests. The conditional update and
database uniqueness constraints protect against stale reads or duplicate
writes. Idempotency records are unique on show, user, and key; the request hash
is computed from sorted seat labels, so a retry returns the original reservation
while reusing the key for different seats returns `409`.

Example reserve request:

```http
POST /shows/{id}/reserve
Authorization: Basic <reservation-user-credentials>
Idempotency-Key: concert-order-123
Content-Type: application/json

{"seats":["A12"],"user_id":"ignored"}
```

## Database configuration

The default database is in-memory H2. To use PostgreSQL, set `DATABASE_URL`,
`DATABASE_USERNAME`, and `DATABASE_PASSWORD`. For example:

```text
DATABASE_URL=jdbc:postgresql://localhost:5432/seat_reservation
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=<your-password>
```

JPA schema updates are enabled by default for local development and can be
overridden with `JPA_DDL_AUTO`.
