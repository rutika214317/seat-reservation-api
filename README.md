# Seat Reservation API

Spring Boot 3 / Java 17 API for assigned-seat reservations.

Source code is organized by technical layer under
`com.example.seatreservation`: `controller`, `service`, `model`, `dto`,
`repository`, and `config`.

## Run locally

```bash
./mvnw spring-boot:run
```

On Windows, run `mvnw.cmd spring-boot:run`. Local development uses an in-memory H2
database. The API is available at `http://localhost:8080`; Swagger UI is at
`http://localhost:8080/swagger-ui.html`.

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
