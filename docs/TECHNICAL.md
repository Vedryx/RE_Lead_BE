# Technical reference

**For developers.** For the plain-language guide see [../README.md](../README.md).

Spring Boot 3.5 + Spring Data MongoDB backend for the voice-agent dashboard: lead CRUD, an
outbound call state machine with retries, call recordings, and the aggregations the dashboard
renders.

- Java 17, Maven, package `com.vedryxtech.voiceagent`
- Two credentials: JWT login for people, API key for the AI agent
- **camelCase on the wire**, in bodies, query parameters and enum values alike
- Services follow an interface + `*Impl` pattern

## Run

```bash
mvn spring-boot:run
```

No environment variables required — `application.yml` carries real local values. On first boot
`DataInitializer` creates the organization, its admin, the API key and (optionally) the sample
leads. It is idempotent: restarting never overwrites what already exists.

| | |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Admin login | `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` from `.env` |
| Agent API key | `BOOTSTRAP_API_KEY` from `.env` |

## Collections

| Collection | Holds |
|---|---|
| `organization` | One row. Calling policy plus the hashed API key. |
| `app_user` | Logins. Unique email, BCrypt hash, roles, `organization_id`. |
| `lead` | One document per lead — **current** state only. Mongo `_id` is its only identifier |
| `leads_log` | One document per call attempt — the **history**, append-only |

The split is deliberate: `lead` answers "where does this stand", `leads_log` answers "how did it
get here". Every pipeline transition writes both, in the same service call.

### Keys and indexes

- `lead`: unique on `calling_phone` (one lead per number). The dialler's claim query is covered
  by `(pipeline_status, next_attempt_at)`. There is no lead id or action id - the agent does
  not supply identifiers, so the Mongo `_id` is the only one.
- `leads_log.lead_id` is the lead's `ObjectId`, not a string key.
- `leads_log`: unique sparse on `idempotency_key`; indexed on `created_at`,
  `(lead_id, attempt_number)`, outcome, disposition and recording status.
- `organization`: unique sparse on `api_key_hash` — the lookup on every agent request.

## Authentication

Two filters, both stateless, neither aware of the other:

1. **`ApiKeyAuthenticationFilter`** runs first. No `X-API-Key` header → passes straight through.
   A valid key authenticates as `ROLE_API_CLIENT`. An unknown key is rejected with `401` rather
   than falling through into a confusing "missing token" error later in the chain.
2. **Spring Security's resource server** handles `Authorization: Bearer <jwt>`. Roles travel in
   the `roles` claim and become `ROLE_*` authorities.

`CurrentUser` is the single accessor for "who is calling". Every method returns `Optional`
because an API-key caller has no user behind it — `CurrentUser.actor()` collapses that to a
label (`ai_agent` or a user id) for call-log attribution.

API keys are generated as `vdx_` + 32 random bytes (URL-safe base64), stored as SHA-256 only.
The plaintext is returned exactly once, at creation. Rotation replaces the hash, so the previous
key stops working on the next request.

`@PreAuthorize` guards user administration, API-key management, call-policy changes and lead
deletion. `GlobalExceptionHandler` maps `AccessDeniedException` explicitly — without that, the
catch-all handler turns every denied `@PreAuthorize` into a 500, because method security throws
from inside the controller, after the filter chain's own handler is out of the picture.

## Statuses (all enums)

| Enum | Values |
|---|---|
| `LeadPipelineStatus` | `new`, `queued`, `dialing`, `inProgress`, `retryScheduled`, `callbackScheduled`, `completed`, `exhausted`, `suppressed`, `failed` |
| `CallOutcome` (telephony) | `answered`, `noAnswer`, `busy`, `rejected`, `voicemail`, `invalidNumber`, `failed`, `cancelled` |
| `CallDisposition` (what was agreed) | `siteVisitBooked`, `callbackRequested`, `rescheduled`, `detailsRequested`, `interested`, `notInterested`, `doNotCall`, `wrongNumber`, `languageBarrier`, `unqualified`, `noDecision` |
| `LeadFinalStatus` | `siteVisitBooked`, `siteVisitDone`, `interested`, `notInterested`, `unreachable`, `doNotCall`, `wrongNumber`, `unqualified`, `duplicate` |
| `ActionType` | `teamCallback`, `siteVisit`, `followUpCall`, `whatsappProjectDetails` |
| `LeadStatus` (action) | `requested`, `scheduled`, `rescheduled`, `completed`, `cancelled`, `noShow`, `failed` |
| `CallEventType` | `dialStarted`, `answered`, `hangup`, `retryScheduled`, `callbackRequested`, `retriesExhausted`, `statusChanged`, `recordingReady`, … |
| `RecordingStatus` | `notRequested`, `starting`, `recording`, `processing`, `available`, `failed`, `expired` |
| `UserRole` | `superAdmin`, `orgAdmin`, `manager`, `agent`, `viewer` |

Keeping **outcome** (did the phone connect) separate from **disposition** (what the person said)
is what makes the retry rules and the funnel numbers both correct — "no answer" is a telephony
fact, "not interested" is a business fact, and they need different handling.

Every enum implements `WireValue`, and `MongoConfig` registers one `ConverterFactory` for the
interface, so a new enum is persisted as its camelCase wire value with no extra registration.

## The lead lifecycle

A lead is created **fresh**: name, phone, optionally project/source/campaign. `LeadServiceImpl`
defaults `pipelineStatus` to `new` and `nextAttemptAt` to now, so it is immediately claimable.

`actionType`, `status`, `callbackAt`, `scheduledFor`, `query` and `whatsappPhone` are all
nullable and stay empty until `recordOutcome` runs. Validation reflects that: only `phone` is
always required, and the per-action rules (`teamCallback` needs `callbackAt`, and so on) apply
**only when an `actionType` is present**, which lets an already-worked lead be imported through
the same endpoint.

`CallOutcomeRequest` carries optional `leadName`, `leadProject`, `leadQuery` and
`whatsappPhone` so the agent writes back what it learned; `applyLeadDetailsFromCall` copies them
onto the lead in the same transaction that moves its status.

## The call state machine

```
claim / start                recordOutcome
─────────────►  DIALING  ──────────────────►  answered?
                                                 │
    ┌────────────────────────────────────────────┴─────────────────────────┐
    │ no                                                                yes│
    ▼                                                                      ▼
retryable && attempts < max ?                              disposition decides
    │ yes                │ no                              ├─ siteVisitBooked   → COMPLETED / siteVisitBooked
    ▼                    ▼                                 ├─ callbackRequested  → CALLBACK_SCHEDULED (no attempt spent)
RETRY_SCHEDULED     EXHAUSTED                              ├─ notInterested      → COMPLETED / notInterested
(+backoff, clamped  (final:                                ├─ doNotCall         → SUPPRESSED / doNotCall
 to calling window)  unreachable)                          ├─ wrongNumber        → COMPLETED / wrongNumber
                                                           └─ noDecision         → RETRY_SCHEDULED
invalidNumber → COMPLETED / wrongNumber (never retried)
```

Rules worth knowing:

- **Retry budget** (`maxAttempts`, default 4) applies only to the unanswered path. Someone who
  picked up and asked for Tuesday gets Tuesday, however many times we missed them before.
- **Backoff is per outcome**: busy 30 min, no answer 2 h, voicemail 4 h, rejected 24 h.
- **Calling window** (default 09:00–20:00 in the org's timezone): any computed retry time outside
  it is pushed to the next opening.
- **Daily cap** (`maxAttemptsPerDay`, default 2) defers a lead to tomorrow's window.
- **`doNotCall`** sets `SUPPRESSED` and is never cleared by the dialler; dialling a suppressed
  lead returns `409`.
- **Claiming is atomic** — `findAndModify` flips one due lead to `dialing`, so two workers
  polling at once never take the same lead. `POST /call-queue/recoveries` (and the scheduler)
  frees leads left dialling by a worker that died.
- **Idempotency** — pass `idempotency_key` on start and a retried request returns the attempt
  already open instead of opening a second one.

`CallOrchestrationService` owns every write to `pipelineStatus`, `finalStatus` and
`leads_log`. CRUD never moves a lead through the pipeline, which is what guarantees the history
always explains the state.

## Endpoints

### Auth — `/api/v1/auth`
| | | |
|---|---|---|
| `POST` | `/login` | public |
| `GET` | `/me` | current user |

### Organizations — `/api/v1/organizations`
| | | |
|---|---|---|
| `POST` | `/` | public signup |
| `GET` | `/current` | current organization |
| `PUT` | `/current/call-policy` | `ORG_ADMIN` |

### API Keys — `/api/v1/api-keys`
| | | |
|---|---|---|
| `POST`/`GET`/`DELETE` | `/current` | create (returns the key once) / show prefix / revoke |

### Users — `/api/v1/users`
| | | |
|---|---|---|
| `POST`/`GET` | `/` | `ORG_ADMIN` creates, `ORG_ADMIN`/`MANAGER` lists |
| `PATCH` | `/{id}` | `ORG_ADMIN` |

### Leads — `/api/v1/leads`
`POST /` · `PUT /` · `GET /` (filters below) · `GET /{id}` · `PUT /{id}` · `PATCH /{id}`

Filters: `project`, `actionType`, `status`, `pipelineStatus`, `finalStatus`, `disposition`,
`phone`, `name`, `assignedTo`, `confirmedByLead`, `hasRecording`, `createdFrom/to`,
`scheduledFrom/to`, `page`, `size`, `sortBy`, `sortDir`.

### Lead Calls
| | | |
|---|---|---|
| `POST` | `/api/v1/leads/{leadId}/calls` | "call now" for one lead |
| `GET` | `/api/v1/leads/{leadId}/calls` | every attempt, newest first |
| `POST` | `/api/v1/leads/{leadId}/call-reschedules` | books a callback without spending an attempt |

### Calls — `/api/v1/calls`
| | | |
|---|---|---|
| `POST` | `/{callLogId}/outcome` | hangup — the only status writer |
| `GET` | `/` | searchable call log |
| `GET` | `/recordings` · `/{callLogId}/recording` | recordings list / playback URL |

### Call Queue — `/api/v1/call-queue`
| | | |
|---|---|---|
| `GET` | `/` | how many leads are due now |
| `POST` | `/claims?limit=10` | dialler takes due work |
| `POST` | `/recoveries` | frees leads abandoned in `dialing` |


### Dashboard — `GET /api/v1/dashboard/summary`

| Parameter | |
|---|---|
| `range` | `today`, `week` (7d), `fifteenDays`, `month` (30d, default), `quarter` (90d), `all`, `custom` |
| `from` / `to` | ISO-8601. Required for `custom`; `to` defaults to now |

**Two sources, one rule.** Current status is read from `lead`, filtered on `created_at`.
History is read from `leads_log`, filtered on `dial_started_at`. The same window is applied to
both, so the tiles and the charts always describe the same period.

| From `lead` (current state) | From `leads_log` (what happened) |
|---|---|
| `totals` | `calls` (attempts, connect rate, talk time, recordings) |
| `byPipelineStatus` | `byOutcome` |
| `byFinalStatus` | `byDisposition` |
| `byActionType` | `dailyTrend` |

A lead that was missed once and then answered contributes **one** row to the lead-side counts
and **two** to the log-side ones. That is the point of the split: the lead knows where things
stand, the log knows every attempt it took to get there.

Bounded ranges are whole days in the organization's timezone, so `week` means the last seven
calendar days there, not a rolling 168 hours. The trend is gap-filled to one point per day.
## Recordings

There is no telephony-platform integration in this build. `recording_url`, `recordingStatus`
and `recording_duration_seconds` live on `leads_log`, and a URL supplied on the outcome payload
marks the attempt `available` and mirrors onto `lead.last_recording_url`. Wiring a platform
(LiveKit or otherwise) means adding a webhook that sets those same fields — no schema change.

## Swagger / OpenAPI

`OpenApiConfig` declares both security schemes — `bearerAuth` (HTTP bearer) and `apiKeyAuth`
(`X-API-Key` header) — as global requirements, so either opens a protected endpoint. `/login`
and `POST /api/v1/organizations` opt out with `@SecurityRequirements`. Controllers are grouped by
numbered `@Tag`, and request DTOs carry `@Schema(example = ...)` so *Try it out* is pre-filled
with a payload that works as-is.

## Tests

```bash
mvn test
```

`CallPolicyTest` covers the retry rules, `PhoneNumbersTest` the phone canonicalisation,
`LeadControllerTest` the HTTP contract (MockMvc slice, no MongoDB needed).

## Removed, and how to put it back

**Multi-tenancy.** `lead` and `leads_log` no longer carry `organization_id`, and no query filters
on it. Restoring it means re-adding the field, the compound `(organization_id, calling_phone)`
unique index, and an organization filter in the four services — the `Organization` entity and the
`organization_id` on `app_user` are still in place.

**LiveKit.** The client, token minting, egress webhook and config were removed on request. The
recording fields on `leads_log` remain, so re-adding the integration is a webhook plus a config
block, not a redesign.
