# Seat Reservation API

Spring Boot 3 / Java 17 API. This first step implements show creation and show-state
retrieval; reservation endpoints are the next step.

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
issuance endpoint is provided.

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
