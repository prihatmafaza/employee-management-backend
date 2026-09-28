# API Contract — Access Requests

This is the HTTP contract between the frontend (`src/`) and the backend. It
matches the `Api` interface in [`src/api/index.ts`](../src/api/index.ts) and
the rules already implemented by the in-browser mock in
[`src/api/mockApi.ts`](../src/api/mockApi.ts). When the two disagree, the
behaviour described here is the target.

- [Conventions](#conventions)
- [Data types](#data-types)
- [Endpoints](#endpoints)
- [Business rules](#business-rules)
- [Seed data](#seed-data)
- [Acceptance scenarios](#acceptance-scenarios)

---

## Conventions

| Topic          | Rule |
| -------------- | ---- |
| Base path      | All endpoints are under `/api`. In development the Vite dev server proxies `/api` to the backend, so browser and API share an origin. |
| Format         | Request and response bodies are JSON (`Content-Type: application/json`). Empty responses use `204 No Content`. A `POST` that has a body with any other content type is rejected with `415`. |
| Authentication | JWT access token returned by `POST /api/auth/login`. The frontend sends it on every call as `Authorization: Bearer <token>`. No cookies, no server session. |
| CSRF           | No CSRF token needed: browsers never attach the `Authorization` header automatically. CORS stays **disabled** (the frontend is same-origin through the proxy). |
| IDs            | Positive integers. |
| Timestamps     | ISO 8601 strings in UTC, e.g. `"2026-09-27T08:15:30.123Z"`. |
| Sorting        | Every list of requests is **newest first** (by `id` descending). |
| Strings        | Text inputs (`username`, `reason`, `comment`) are trimmed before validation and storage. |

### Error format

Every non-2xx response has this body:

```json
{
  "error": {
    "code": "DUPLICATE_REQUEST",
    "message": "You already have a pending request for VPN Access."
  }
}
```

`message` is shown to the user as-is, so it must be a readable sentence.
`code` is stable and meant for code to branch on.

| HTTP | `code`                | When |
| ---- | --------------------- | ---- |
| 400  | `VALIDATION_ERROR`    | Missing or invalid input. |
| 401  | `UNAUTHENTICATED`     | No valid session, or wrong username/password on login. |
| 403  | `FORBIDDEN`           | Logged in, but the role may not use this endpoint. |
| 404  | `NOT_FOUND`           | The resource doesn't exist **or isn't visible to the caller**. Don't reveal that it exists. |
| 409  | `DUPLICATE_REQUEST`   | The user already has a pending or approved request for this access. |
| 409  | `ALREADY_FINALIZED`   | The request is already `APPROVED` or `REJECTED`. |
| 409  | `NOT_YOUR_STEP`       | The request is visible to the caller but is waiting on a different approval step. |
| 415  | `UNSUPPORTED_MEDIA_TYPE` | A body was sent with a content type other than `application/json`. |
| 500  | `INTERNAL_ERROR`      | Unexpected failure. Don't leak stack traces or SQL. |

On `401` the frontend drops its session and shows the login page.

---

## Data types

TypeScript notation, same names as [`src/types.ts`](../src/types.ts).

```ts
type Role = 'USER' | 'MANAGER' | 'ADMIN'
type RequestStatus = 'IN_PROGRESS' | 'APPROVED' | 'REJECTED'
type ApprovalStep = 'MANAGER' | 'ADMIN'
type Decision = 'APPROVED' | 'REJECTED'

interface User {
  id: number
  username: string
  fullName: string
  role: Role
  managerId: number | null   // who approves this user's requests at the MANAGER step
}

interface AccessType {
  id: number
  name: string
  description: string
}

interface Approval {
  step: ApprovalStep
  decision: Decision
  approver: { id: number; fullName: string }
  comment: string | null
  decidedAt: string           // ISO 8601
}

interface AccessRequest {
  id: number
  accessType: AccessType
  requester: { id: number; fullName: string }
  reason: string
  status: RequestStatus
  currentStep: ApprovalStep | null   // null exactly when status !== 'IN_PROGRESS'
  approvals: Approval[]              // ordered MANAGER, then ADMIN; 0–2 entries
  createdAt: string
  updatedAt: string
}

interface ReviewItem extends AccessRequest {
  canAct: boolean   // true if the caller may approve/reject it right now
}

type SummaryScope = 'SYSTEM' | 'TEAM' | 'MINE'

interface DashboardSummary {
  scope: SummaryScope
  total: number
  inProgress: number       // = waitingManager + waitingAdmin
  waitingManager: number   // status IN_PROGRESS, currentStep MANAGER
  waitingAdmin: number     // status IN_PROGRESS, currentStep ADMIN
  approved: number
  rejected: number
  generatedAt: string      // ISO 8601, when the counts were computed
}
```

`User` never contains a password or password hash.

### `status` and `currentStep` combinations

| Situation                          | `status`      | `currentStep` | `approvals`                          |
| ---------------------------------- | ------------- | ------------- | ------------------------------------ |
| Just submitted                     | `IN_PROGRESS` | `MANAGER`     | `[]`                                 |
| Manager approved                   | `IN_PROGRESS` | `ADMIN`       | `[MANAGER:APPROVED]`                 |
| Manager rejected                   | `REJECTED`    | `null`        | `[MANAGER:REJECTED]`                 |
| Admin approved                     | `APPROVED`    | `null`        | `[MANAGER:APPROVED, ADMIN:APPROVED]` |
| Admin rejected                     | `REJECTED`    | `null`        | `[MANAGER:APPROVED, ADMIN:REJECTED]` |

No other combination may exist.

---

## Endpoints

| Method | Path                           | Roles          | Purpose |
| ------ | ------------------------------ | -------------- | ------- |
| POST   | `/api/auth/login`              | public         | Log in, get an access token |
| POST   | `/api/auth/logout`             | public         | Revoke the access token |
| GET    | `/api/auth/me`                 | any logged-in  | Current user |
| GET    | `/api/access-types`            | any logged-in  | Accesses that can be requested |
| GET    | `/api/requests/mine`           | USER           | My Requests page |
| POST   | `/api/requests`                | USER           | Request Access page |
| GET    | `/api/approvals`               | MANAGER, ADMIN | Approve Request page |
| POST   | `/api/requests/{id}/decision`  | MANAGER, ADMIN | Approve or reject |
| GET    | `/api/dashboard/summary`       | any logged-in  | Dashboard page: request counts by status |

Unless noted, every endpoint except login returns `401 UNAUTHENTICATED` without
a valid token (missing, malformed, expired or revoked), and `403 FORBIDDEN` when the caller's role isn't listed.

### POST `/api/auth/login`

Request:

```json
{ "username": "alice", "password": "password123" }
```

- `username` is trimmed and matched case-insensitively.

Responses:

- `200` →

  ```json
  { "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 7200, "user": User }
  ```

  `expiresIn` is in seconds. Any `Authorization` header sent to login is ignored,
  so a stale token never blocks logging in.
- `400 VALIDATION_ERROR` if `username` or `password` is missing or not a string.
- `401 UNAUTHENTICATED`, message `"Invalid username or password."`. Use the
  same response for an unknown user and a wrong password.

### POST `/api/auth/logout`

- `204`. Revokes the bearer token sent with the request; the frontend drops it too.
- Returns `204` even without a token, or with an expired/invalid one, so logging out is always safe.

### GET `/api/auth/me`

- `200` → `User`.
- `401 UNAUTHENTICATED` if not logged in. The frontend treats this as "no valid token".

### GET `/api/access-types`

- `200` → `AccessType[]`, ordered by `id`.

### GET `/api/requests/mine`  (USER)

- `200` → `AccessRequest[]`: requests where the requester is the caller, newest first.

### POST `/api/requests`  (USER)

Request:

```json
{ "accessTypeId": 1, "reason": "Need to work from home" }
```

Checks, in this order:

| # | Check | Response |
|---|-------|----------|
| 1 | `reason` is empty after trimming | `400 VALIDATION_ERROR` "Please enter a reason for the request." |
| 2 | `reason` longer than 500 characters | `400 VALIDATION_ERROR` "Reason must be at most 500 characters." |
| 3 | `accessTypeId` isn't an existing access type | `400 VALIDATION_ERROR` "Please select a valid access." |
| 4 | Caller has no `managerId` | `400 VALIDATION_ERROR` "You have no manager assigned, so your request cannot be approved." |
| 5 | Caller has an `APPROVED` request for this access | `409 DUPLICATE_REQUEST` "You already have {access name}." |
| 6 | Caller has an `IN_PROGRESS` request for this access | `409 DUPLICATE_REQUEST` "You already have a pending request for {access name}." |

On success it returns `201` → `AccessRequest` with `status: "IN_PROGRESS"`,
`currentStep: "MANAGER"` and `approvals: []`. A user may request an access
again after an earlier request for it was `REJECTED`.

### GET `/api/approvals`  (MANAGER, ADMIN)

`200` → `ReviewItem[]`, newest first, containing every request **visible** to
the caller in any status (see [Visibility](#visibility)).

`canAct` is `true` only when:

- MANAGER: `currentStep === 'MANAGER'`
- ADMIN: `currentStep === 'ADMIN'`

(The visibility rule already ensures the request belongs to the manager's team.)

The UI's "Waiting for me" tab shows items with `canAct: true`. The "All
requests" tab shows everything returned.

### POST `/api/requests/{id}/decision`  (MANAGER, ADMIN)

Request:

```json
{ "decision": "REJECTED", "comment": "Use the shared Figma seat instead." }
```

- `decision` must be `"APPROVED"` or `"REJECTED"`.
- `comment` is optional (string, max 500 chars) when approving and
  **required** (non-empty after trimming) when rejecting. An empty comment is
  stored as `null`.

Checks, in this order:

| # | Check | Response |
|---|-------|----------|
| 1 | `id` isn't an integer, `decision` is invalid, or `comment` is too long | `400 VALIDATION_ERROR` |
| 2 | Request doesn't exist or isn't visible to the caller | `404 NOT_FOUND` "Request not found." |
| 3 | Request isn't `IN_PROGRESS` | `409 ALREADY_FINALIZED` "This request has already been finalized." |
| 4 | `currentStep` doesn't match the caller's role | `409 NOT_YOUR_STEP` "This request is waiting for admin approval." (or "…still waiting for manager approval.") |
| 5 | `decision` is `REJECTED` and `comment` is empty | `400 VALIDATION_ERROR` "Please give a reason for the rejection." |

On success it returns `200` → the updated `AccessRequest`. Transitions:

| `currentStep` | `decision` | New `status`  | New `currentStep` |
| ------------- | ---------- | ------------- | ----------------- |
| `MANAGER`     | `APPROVED` | `IN_PROGRESS` | `ADMIN`           |
| `MANAGER`     | `REJECTED` | `REJECTED`    | `null`            |
| `ADMIN`       | `APPROVED` | `APPROVED`    | `null`            |
| `ADMIN`       | `REJECTED` | `REJECTED`    | `null`            |

The approval record is created and the request updated in **one transaction**.
If two reviewers decide at the same moment, exactly one succeeds and the other
gets `409` (use a conditional update such as
`UPDATE … WHERE id = ? AND status = 'IN_PROGRESS' AND current_step = ?`, plus a
unique constraint on `(request_id, step)`).

### GET `/api/dashboard/summary`  (any logged-in)

Request counts for the Dashboard page, by status and approval step. The
frontend polls it every 10 seconds while the page is open, so it must be cheap:
a single aggregate query, not loading requests into memory.

The requests counted depend on the caller's role:

| Role    | `scope`  | Counts |
| ------- | -------- | ------ |
| ADMIN   | `SYSTEM` | Every request in the system, in any status. This includes requests still waiting for a manager, which admins can't open. Only counts are exposed, never the requests. |
| MANAGER | `TEAM`   | Requests whose requester has `managerId` = the caller. |
| USER    | `MINE`   | The caller's own requests. |

`200` →

```json
{
  "scope": "SYSTEM",
  "total": 125,
  "inProgress": 32,
  "waitingManager": 20,
  "waitingAdmin": 12,
  "approved": 78,
  "rejected": 15,
  "generatedAt": "2026-09-28T09:15:30.123Z"
}
```

All counts come from **one consistent snapshot** (one query or one read
transaction), so `total = inProgress + approved + rejected` and
`inProgress = waitingManager + waitingAdmin` always hold. Every count is `0`
when there are no requests. For example, with PostgreSQL:

```sql
SELECT count(*)                                              AS total,
       count(*) FILTER (WHERE status = 'IN_PROGRESS')         AS in_progress,
       count(*) FILTER (WHERE current_step = 'MANAGER')       AS waiting_manager,
       count(*) FILTER (WHERE current_step = 'ADMIN')         AS waiting_admin,
       count(*) FILTER (WHERE status = 'APPROVED')            AS approved,
       count(*) FILTER (WHERE status = 'REJECTED')            AS rejected
FROM access_requests r
JOIN users u ON u.id = r.user_id
WHERE :scope = 'SYSTEM'
   OR (:scope = 'TEAM' AND u.manager_id = :callerId)
   OR (:scope = 'MINE' AND r.user_id   = :callerId)
```

---

## Business rules

### Roles

| Role    | Can do |
| ------- | ------ |
| USER    | Request access; see their own requests. |
| MANAGER | See and decide the MANAGER step for their team's requests. Cannot submit requests. |
| ADMIN   | See and decide the ADMIN step for requests a manager has approved. Cannot submit requests. |

Roles are checked on the server for every call. The frontend hides menu items
too, but that isn't a security boundary.

### Visibility

A request is visible to:

- **its requester** (through `/api/requests/mine` only);
- **a MANAGER** if `requester.managerId === manager.id`, in any status;
- **an ADMIN** if the request has a `MANAGER` approval with decision
  `APPROVED`, whether it's now waiting for the admin, `APPROVED` or
  `REJECTED` by an admin. Requests still waiting for the manager or rejected
  by the manager are **not** visible to admins.

Anything not visible must behave as if it doesn't exist (`404`).

### Other rules

- A request becomes `APPROVED` only after both the MANAGER and the ADMIN approve it.
- A decision can never be changed. Once a step is decided, it's final.
- Passwords are stored as a slow salted hash (bcrypt, scrypt or argon2), never in plain text.
- There is no registration endpoint. Users come from seed data.

---

## Seed data

Every account's password is `password123`.

| id | username  | fullName       | role    | manager |
| -- | --------- | -------------- | ------- | ------- |
| 1  | `admin`   | Adam Admin     | ADMIN   | —       |
| 2  | `maria`   | Maria Manager  | MANAGER | —       |
| 3  | `mark`    | Mark Manager   | MANAGER | —       |
| 4  | `alice`   | Alice Anderson | USER    | maria   |
| 5  | `bob`     | Bob Brown      | USER    | maria   |
| 6  | `charlie` | Charlie Clark  | USER    | mark    |

| id | name                   | description |
| -- | ---------------------- | ----------- |
| 1  | VPN Access             | Remote connection to the internal company network. |
| 2  | GitHub / GitLab Access | Access to the company source code repositories. |
| 3  | Figma Access           | Editor seat in the company design workspace. |
| 4  | Jira Access            | Create and manage issues in the project tracker. |

No access requests are seeded.

---

## Acceptance scenarios

A backend meets this contract when all of the following pass (ideally as
automated tests):

1. Every endpoint except login returns `401` without a session; login with a wrong password returns `401`.
2. `alice` logs in → `GET /api/auth/me` returns her `User` with no password field.
3. `alice` submits VPN → `201`, `IN_PROGRESS` / `MANAGER`.
4. `alice` submits VPN again → `409 DUPLICATE_REQUEST`.
5. `alice` calls `GET /api/approvals` → `403`.
6. `admin` calls `GET /api/approvals` → the request is **not** listed; deciding it → `404`.
7. `mark` (a different team) → not listed; deciding it → `404`.
8. `maria` rejects with an empty comment → `400`.
9. `maria` approves → `IN_PROGRESS` / `ADMIN`; approving again → `409 NOT_YOUR_STEP`.
10. `admin` now sees it with `canAct: true`, approves → `APPROVED`, `approvals` has MANAGER and ADMIN entries with approver names.
11. Deciding it again → `409 ALREADY_FINALIZED`.
12. `alice` submits Figma, `maria` rejects with a comment → `REJECTED`; `admin` never sees it; `alice` can submit Figma again → `201`.
13. `alice` submits VPN again after it was approved → `409` "You already have VPN Access."
14. `maria` submitting a request → `403`.
15. Dashboard summary. Given alice's VPN `APPROVED`, alice's Figma `REJECTED`,
    alice's Jira waiting for the MANAGER, and charlie's GitHub waiting for the ADMIN:
    - `admin` → `SYSTEM`, total 4, inProgress 2, waitingManager 1, waitingAdmin 1, approved 1, rejected 1.
    - `maria` → `TEAM`, total 3, inProgress 1, waitingManager 1, waitingAdmin 0, approved 1, rejected 1.
    - `mark` → `TEAM`, total 1, inProgress 1, waitingManager 0, waitingAdmin 1.
    - `charlie` → `MINE`, total 1. Without a token → `401`.
