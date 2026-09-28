# Access Requests — Backend

REST API for an internal **access request and approval** system. Employees
request access to company tools, and every request goes through a two-step
approval: **Manager → Admin**.

The frontend (React + TypeScript) lives in the sibling folder
[`../employee-management`](../employee-management). The HTTP contract shared by
both is [`docs/api-contract.md`](docs/api-contract.md).

---

## 1. Project overview

### What it does

- **Users** request access to a tool (VPN, GitHub/GitLab, Figma, Jira) with a reason,
  and track the status of their own requests.
- **Managers** see their team's requests and approve or reject them. That's step 1.
- **Admins** see requests a manager has approved and give the final decision. That's step 2.
- A **dashboard** shows request counts by status, scoped by role: the whole system
  for admins, the team for managers, and the user's own requests for users.

### Request lifecycle

```
            submit                 manager approves               admin approves
  USER ──────────────▶ IN_PROGRESS ─────────────────▶ IN_PROGRESS ───────────────▶ APPROVED
                       (step MANAGER)                 (step ADMIN)
                             │                              │
                             │ manager rejects              │ admin rejects
                             ▼                              ▼
                          REJECTED                       REJECTED
```

- A rejection needs a comment and is final. The user may request the same access again.
- A user can't have two pending or approved requests for the same access (`409 DUPLICATE_REQUEST`).
- If two reviewers decide the same step at once, exactly one wins. The other gets `409`.

### Endpoints

| Method | Path                          | Roles          | Purpose |
| ------ | ----------------------------- | -------------- | ------- |
| POST   | `/api/auth/login`             | public         | Log in, get a JWT access token |
| POST   | `/api/auth/logout`            | public         | Revoke the access token |
| GET    | `/api/auth/me`                | any logged-in  | Current user |
| GET    | `/api/access-types`           | any logged-in  | Accesses that can be requested |
| GET    | `/api/requests/mine`          | USER           | My requests |
| POST   | `/api/requests`               | USER           | Submit a request |
| GET    | `/api/approvals`              | MANAGER, ADMIN | Requests to review |
| POST   | `/api/requests/{id}/decision` | MANAGER, ADMIN | Approve or reject |
| GET    | `/api/dashboard/summary`      | any logged-in  | Request counts by status |

Full details: [`docs/api-contract.md`](docs/api-contract.md). The Postman
collection [`docs/access-requests.postman_collection.json`](docs/access-requests.postman_collection.json)
has every endpoint with example responses. Import it, run **Auth → Login as …**
(the token is saved automatically), then call any endpoint.

### Project structure

Each feature package is split by layer. Services are an interface plus an
implementation in `service/impl/`.

```
src/main/java/org/emb/accessrequests/
├── accesstype/   controller · dto · entity · repository · service
├── auth/         controller · dto · security · service      (login, JWT, rate limiting)
├── dashboard/    controller · dto · repository · service    (summary counts)
├── request/      controller · dto · entity · enums · repository · service
├── user/         dto · entity · enums · repository
├── config/       SecurityConfig, JwtConfig, request body guard, Jackson
├── error/        ApiException, error codes, global JSON error handling
└── seed/         DataSeeder (demo users and access types)
src/main/resources/db/migration/   Flyway migrations
```

---

## 2. Technology stack

| Area            | Technology |
| --------------- | ---------- |
| Language        | Java 21 |
| Framework       | Spring Boot 4.1 (Spring MVC, Spring Data JPA, Validation) |
| Security        | Spring Security 7, OAuth2 Resource Server (JWT, HS256 via Nimbus JOSE), BCrypt |
| Database        | PostgreSQL 16 |
| ORM             | Hibernate 7 (JPA) |
| Migrations      | Flyway |
| JSON            | Jackson 3 |
| Build           | Maven (wrapper included: `./mvnw`) |
| Testing         | JUnit 5, AssertJ, Spring Boot Test, Testcontainers (PostgreSQL) |
| Local infra     | Docker Compose |
| API docs        | Markdown contract + Postman collection |

---

## 3. How to run the application

### Prerequisites

- **JDK 21**
- **Docker**, for the local database and for the tests (Testcontainers)
- **Node.js**, only if you also run the frontend

### Start it

```bash
# 1. PostgreSQL 16 on localhost:5433 (database/user/password: access_requests)
docker compose up -d

# 2. Backend on http://localhost:8080
#    Flyway creates the schema; demo users and access types are seeded on first start.
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 3. (Optional) Frontend on http://localhost:5173. Vite proxies /api to :8080.
cd ../employee-management
npm install
npm run dev
```

Check it's running:

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}'
```

To start again from an empty database: `docker compose down -v`.

### Demo accounts

Every password is `password123`.

| Username  | Role    | Manager |
| --------- | ------- | ------- |
| `admin`   | ADMIN   | —       |
| `maria`   | MANAGER | —       |
| `mark`    | MANAGER | —       |
| `alice`   | USER    | maria   |
| `bob`     | USER    | maria   |
| `charlie` | USER    | mark    |

### Configuration

| Env var          | Default                                            | Notes |
| ---------------- | -------------------------------------------------- | ----- |
| `DB_URL`         | `jdbc:postgresql://localhost:5433/access_requests` | |
| `DB_USER`        | `access_requests`                                  | |
| `DB_PASSWORD`    | `access_requests`                                  | |
| `DB_PORT`        | `5433`                                             | Host port used by `docker compose` |
| `SERVER_PORT`    | `8080`                                             | |
| `JWT_SECRET`     | dev-only value                                     | **Required** with the `prod` profile, at least 32 bytes |
| `JWT_EXPIRATION` | `2h`                                               | Access token lifetime |

### Production

```bash
SPRING_PROFILES_ACTIVE=prod \
JWT_SECRET="$(openssl rand -base64 48)" \
DB_URL=... DB_USER=... DB_PASSWORD=... \
java -jar target/employee-management-backend-0.0.1-SNAPSHOT.jar
```

Build the jar with `./mvnw package`. Run it behind HTTPS and serve the frontend
from the same origin as `/api`. The `prod` profile refuses to start without
`JWT_SECRET`.

### Tests

```bash
./mvnw verify
```

Docker must be running: the integration tests start a `postgres:16-alpine`
container through Testcontainers.

| Test class                     | Covers |
| ------------------------------ | ------ |
| `AcceptanceScenariosTest`      | The 15 acceptance scenarios from the contract, over real HTTP with a bearer token |
| `SecurityAndErrorHandlingTest` | Token issue and validation, logout revocation, rate limiting, 415/413/404/405, error format, list ordering and visibility |
| `DashboardSummaryTest`         | Dashboard counts on an empty database, and a user whose team has requests but who has none |
| `DecisionConcurrencyTest`      | Two reviewers deciding the same step in parallel: exactly one wins, the other gets 409 |
| `RequestServiceTest`           | Validation order and messages, transitions and visibility, against the database |
| `RequestRulesTest`             | Unit tests of the state machine, visibility and `canAct` |
| `JwtServiceTest`               | Token signing, verification, revocation and the minimum secret length |

---

## 4. Security aspects

### Authentication: stateless JWT

- `POST /api/auth/login` checks the password against a **BCrypt** hash and returns
  a signed **JWT (HS256)** access token. The client sends it as
  `Authorization: Bearer <token>` on every call.
- **No server session and no cookies** (`SessionCreationPolicy.STATELESS`).
- The token carries only the user id (`sub`), a unique id (`jti`) and its
  expiry (`exp`, default 2 hours). Role and manager are **reloaded from the
  database on every request**, so a role change or a deleted user takes effect
  immediately, even for tokens already issued.
- Invalid signature, malformed token, expired token or unknown user → `401 UNAUTHENTICATED`.
- The signing secret comes from `JWT_SECRET`. The app refuses to start if it is
  shorter than 32 bytes, and the `prod` profile has no default for it.
- **Logout** puts the token's `jti` on a denylist until the token expires, so
  it stops working at once. Login and logout ignore the `Authorization` header,
  so a stale token never blocks them.

### Authorization

- **Per route, by role**, in `SecurityConfig`. For example, only `USER` may
  submit requests, and only `MANAGER`/`ADMIN` may decide. Wrong role → `403 FORBIDDEN`.
- **Per record, in the service layer**:
  - a manager only sees and decides requests from their own team;
  - an admin only sees requests a manager has already approved;
  - a reviewer can only act at their own step (`409 NOT_YOUR_STEP`).
- A request the caller may not see returns `404 NOT_FOUND`, the same as a
  missing one, so its existence isn't revealed.
- The dashboard scope comes only from the caller's role. It accepts no `scope`
  or `userId` parameter a caller could tamper with.

### Protection against common attacks

| Threat | Mitigation |
| ------ | ---------- |
| Brute-force login | 5 failed attempts per username + client IP within 15 minutes → `429 RATE_LIMITED` |
| User enumeration | Unknown user and wrong password return the same `401` message |
| Password leaks | BCrypt hashes. The hash is erased from memory after login, and no response ever contains it |
| CSRF | No cookies are used, and browsers never attach the `Authorization` header on their own. CORS stays disabled (same-origin frontend) |
| Oversized or unexpected bodies | Bodies over 10 kB → `413`. Any non-JSON content type → `415`, checked before Spring Security runs |
| Type-confusion input | Strict JSON coercion: `{"username": 42}` is a `400`, not silently turned into a string |
| SQL injection | Every query goes through JPA with bound parameters. No string-built SQL |
| Information leakage | One JSON error format, with no stack traces, SQL or exception messages in responses |
| Race conditions | Unique constraint on `(request_id, step)` plus optimistic locking (`@Version`): two simultaneous decisions can't both succeed |
| Data integrity | Database constraints back up the service checks: status/step consistency, one active request per user and access, text length limits |

### Known limitations

- The token denylist and the login rate limiter are **in memory**, so they work on
  a single instance only, and a restart clears them. A logged-out token could be
  used again after a restart until it expires, so keep `JWT_EXPIRATION` short. For
  several instances, move both to a shared store such as Redis.
- There are no refresh tokens: when the access token expires, the user logs in again.
- The demo accounts are seeded with a known password. Don't seed them in a real
  deployment.
