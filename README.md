# Access Requests — backend

Spring Boot 4 + PostgreSQL backend for the Access Requests app. The frontend
lives in the sibling folder `../employee-management`. The HTTP contract is
[`docs/api-contract.md`](docs/api-contract.md).

## Prerequisites

- **JDK 21**
- **Docker** (for the local database *and* for the tests, which use Testcontainers)
- **Node.js** (for the frontend)

## Run locally

```bash
# 1. PostgreSQL 16 (database `access_requests`, user/password `access_requests`)
docker compose up -d

# 2. Backend on http://localhost:8080 (Flyway migrates, demo data is seeded on first start)
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 3. Frontend on http://localhost:5173 (Vite proxies /api to :8080)
cd ../employee-management
npm install
npm run dev
```

If port 5432 is already taken (e.g. by a locally installed PostgreSQL), run the
container on another port and point the backend at it:

```bash
DB_PORT=5433 docker compose up -d
DB_URL=jdbc:postgresql://localhost:5433/access_requests ./mvnw spring-boot:run
```

To start from an empty database: `docker compose down -v`.

### Configuration

| Env var                 | Default                                            |
| ----------------------- | -------------------------------------------------- |
| `DB_URL`                | `jdbc:postgresql://localhost:5433/access_requests` |
| `DB_USER`               | `access_requests`                                  |
| `DB_PASSWORD`           | `access_requests`                                  |
| `SERVER_PORT`           | `8080`                                             |
| `JWT_SECRET`            | dev-only value (required by the `prod` profile, ≥ 32 bytes) |
| `JWT_EXPIRATION`        | `2h`                                               |

In production, run behind HTTPS with `SPRING_PROFILES_ACTIVE=prod` and a strong
`JWT_SECRET`, and serve the frontend from the same origin as `/api`.

## API documentation

- `docs/api-contract.md`: the full contract (types, rules, error codes).
- `docs/access-requests.postman_collection.json`: every endpoint with example
  responses. Import it into Postman, run **Auth → Login as …** (the token is
  saved automatically), then call any endpoint. Change `baseUrl` if the backend
  isn't on `http://localhost:8080`.

## Tests

```bash
./mvnw verify
```

Docker must be running: the integration tests start a `postgres:16-alpine`
container through Testcontainers. The suite contains:

- `AcceptanceScenariosTest`: the 15 acceptance scenarios from the contract, over real HTTP with a bearer token;
- `SecurityAndErrorHandlingTest`: token issue/validation, logout revocation, rate limiting, 415/413/404/405, error format, list ordering and visibility;
- `DashboardSummaryTest`: dashboard counts with no requests, and a user whose team has requests but who has none;
- `DecisionConcurrencyTest`: two reviewers deciding the same step in parallel, where exactly one wins and the other gets 409;
- `RequestServiceTest`: the contract's validation order and messages, transitions and visibility, against the database;
- `RequestRulesTest`: pure unit tests of the state machine, visibility and `canAct`.

## Demo accounts

Every password is `password123`.

| Username  | Role    | Manager |
| --------- | ------- | ------- |
| `admin`   | ADMIN   | —       |
| `maria`   | MANAGER | —       |
| `mark`    | MANAGER | —       |
| `alice`   | USER    | maria   |
| `bob`     | USER    | maria   |
| `charlie` | USER    | mark    |

## Design notes

- **Authentication:** stateless JWT (HS256). `POST /api/auth/login` returns an
  access token; clients send it as `Authorization: Bearer <token>`. No server
  session, no cookies. The token only carries the user id; the user is reloaded
  from the database on every request, so role changes apply immediately.
- **Logout:** revokes the token's `jti` in an in-memory denylist until it expires.
  Single-instance only, and a restart forgets revocations, so keep
  `JWT_EXPIRATION` short.
- **CSRF:** no token needed. Browsers never attach the `Authorization` header on
  their own; CORS stays disabled. See the comment in `SecurityConfig`.
- **Concurrency:** a decision inserts the approval (`UNIQUE (request_id, step)`)
  and updates the request (`@Version`) in one transaction. The loser of a race gets
  `409 ALREADY_FINALIZED`. A partial unique index also prevents two active
  requests for the same user and access.
- **Additions to the contract** (protocol-level cases the contract doesn't list):
  `405 METHOD_NOT_ALLOWED`, `413 PAYLOAD_TOO_LARGE` (bodies over 10 kB) and
  `429 RATE_LIMITED` (5 failed logins per username + IP within 15 minutes).
  They all use the same error body.
