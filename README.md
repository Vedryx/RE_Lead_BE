# AI Voice Agent — Dashboard

This is the engine behind the voice-agent dashboard.

An AI agent phones people who enquired about a property. This system remembers **who to call**,
hands the work to the agent, **writes down what happened**, **decides when to try again**,
keeps the **recording** so you can listen back, and **adds up the numbers** the dashboard shows.

You do not need to write any code to try it. There is a web page (Swagger) where you can click
buttons and see exactly what the system does, and a Postman collection if you prefer that.
Skip to [Try it yourself](#try-it-yourself).

> Field names are **camelCase** everywhere — `actionType`, `callbackAt`, `pipelineStatus` —
> in request bodies, in responses and in query parameters alike.

---

## The story of one lead

This is the whole system in one picture. "Lead" just means *a person who might buy*.

```
   Someone enquires
          │
          ▼
   ┌─────────────┐
   │  NEW LEAD   │  waiting to be called
   └──────┬──────┘
          │  the agent picks them up
          ▼
   ┌─────────────┐
   │   CALLING   │
   └──────┬──────┘
          │  the call ends — one of two things happened
          │
   ┌──────┴───────────────────────────┐
   │                                  │
   ▼                                  ▼
Nobody picked up                They picked up
(busy / no answer /             and said something
 they hung up)                          │
   │                                    │
   │  wait a while,             ┌───────┼────────┬─────────────┬──────────────┐
   │  then try again            ▼       ▼        ▼             ▼              ▼
   │                        "Yes, I'll  "Call   "Send me    "Not         "Never call
   │  tried 4 times          visit"     me on    details"   interested"   me again"
   │  and still nothing       │         Tuesday"    │            │             │
   ▼                          ▼          ▼          ▼            ▼             ▼
┌──────────────┐        ┌─────────┐ ┌──────────┐ ┌────────┐ ┌────────┐  ┌────────────┐
│ UNREACHABLE  │        │  WON    │ │ CALL BACK│ │  SENT  │ │  LOST  │  │ BLOCKED    │
│ (we stop)    │        │ visit   │ │ Tuesday  │ │ details│ │        │  │ (we never  │
└──────────────┘        │ booked  │ └────┬─────┘ └────────┘ └────────┘  │  call again)│
                        └─────────┘      │                              └────────────┘
                                         │ on Tuesday, it calls them again
                                         └──────────────► back to CALLING
```

Two ideas make the whole thing work, and they are worth understanding:

**1. "Did the phone connect?" and "What did they say?" are two different questions.**

*No answer* is a phone problem — try again later. *Not interested* is an answer — stop calling.
The system records both separately, which is why it never confuses "we couldn't reach them" with
"they said no".

**2. Somebody who asks for a callback always gets it.**

There is a limit on how many times we chase someone who never picks up (4 by default). But if a
person actually answers and says "call me Tuesday at 6", that limit doesn't apply. They asked, so
they get the call. Pestering people who ignore us is rude; not calling back someone who asked is
just losing the sale.

---

## Try it yourself

**You need:** Java 17 and MongoDB running on your machine. That's it.

### 1. Start it

Open a terminal in this folder and run:

```
mvn spring-boot:run
```

Wait for the line that says `Started VoiceAgentApplication`. Leave that window open.

**Nothing to configure first.** `application.yml` already holds working local settings, and on
first start the application creates the company, the admin login, the AI agent's API key and 11
sample leads waiting to be called, so there is something to look at straight away.

### 2. Open the test page

Go to **<http://localhost:8080/swagger-ui.html>** in your browser.

You'll see a list of everything the system can do, grouped into four numbered sections.

### 3. Log in

1. Open the green **`POST /api/v1/auth/login`** row.
2. Click **Try it out**, then **Execute** — the example is already filled in with:
   ```json
   { "email": "admin@vedryxtech.com", "password": "Admin@12345" }
   ```
3. In the response, find `"accessToken"` and copy the long string between the quotes.
4. Scroll to the top and click the **Authorize** button (the padlock).
5. Paste the token under **bearerAuth**, click **Authorize**, then **Close**.

Every other button on the page now works. The login lasts 12 hours.

> A "token" is just a temporary pass. It proves who you are, so the system knows the request is
> from a real person who is allowed to be there.

### 4. Have a look around

Try these in order — it walks you through the whole flow:

| Try this | What you'll see |
|---|---|
| **2. Leads** → `GET /api/v1/leads` | The list of people to call |
| **8. Call Queue** → `POST /api/v1/call-queue/claims` | Takes a batch of people who are due a call now |
| **7. Calls** → `POST /api/v1/calls/{callLogId}/outcome` | Record what happened (see below) |
| **6. Lead Calls** → `GET /api/v1/leads/{leadId}/calls` | Everything ever tried with one person |
| **4. Dashboard** → `GET /api/v1/dashboard/summary` | Every number the dashboard shows |
| **2. Leads** → `POST /api/v1/leads` | Add a fresh lead — only a name and a number needed |

**To see the flow in action:** run `call-queue/claims` first and copy a `callLogId` from the response.
Paste it into `outcome` and send one of these:

*Nobody picked up:*
```json
{ "outcome": "noAnswer", "ringSeconds": 25 }
```

*They picked up and booked a visit.* The `lead_*` fields write back what the call taught us, so
the lead record fills itself in:
```json
{
  "outcome": "answered",
  "disposition": "siteVisitBooked",
  "talkSeconds": 214,
  "siteVisitAt": "2026-09-06T11:00:00+05:30",
  "summary": "Wants the 3 BHK, visiting Saturday.",

  "leadName": "Shrikant Giri",
  "leadProject": "My Home Sanctuary",
  "leadQuery": "Can 2 BHK and 3 BHK be combined?"
}
```

*They picked up and asked to be called later:*
```json
{
  "outcome": "answered",
  "disposition": "callbackRequested",
  "talkSeconds": 41,
  "requestedCallbackAt": "2026-09-02T18:30:00+05:30",
  "summary": "Busy, asked for Tuesday evening."
}
```

Then look the lead up again (`GET /api/v1/leads/{id}`) and see it filled in, and check the
dashboard to watch the numbers move.

---

## How the AI agent connects

People log in with an email and password. The AI voice-agent application doesn't have a person
behind it, so it uses an **API key** instead — one long secret string it sends with every
request.

The key is created for you on first start:

```
vdx_local_dev_key_2f8a41c6b09d47e5ab3c
```

The agent sends it as a header on every call:

```
X-API-Key: vdx_local_dev_key_2f8a41c6b09d47e5ab3c
```

That's the whole flow — no login step, no expiry. With it, the agent can read leads, add new
ones, take work from the queue and record what happened on each call. It **cannot** manage user
accounts or change company settings; those need a real admin login.

To try it in Swagger, paste the key under **apiKeyAuth** in the same Authorize dialog instead of
the token.

### Managing the key

| | |
|---|---|
| `POST /api/v1/api-keys/current` | Create a new key. **Shown once** — copy it |
| `GET /api/v1/api-keys/current` | See which key is in use (prefix only) |
| `DELETE /api/v1/api-keys/current` | Revoke it — the agent loses access immediately |

Two things worth knowing:

- **The key is stored scrambled, not as text.** Nobody can read it back out of the database, not
  even an administrator. If it's lost, create a new one.
- **Creating a new key replaces the old one instantly.** Update the agent at the same time, or it
  will stop working.

---

## What the system remembers

Four lists, that's all:

| | What's in it |
|---|---|
| **Company** | Your organisation. Owns the calling rules and the AI agent's API key. |
| **Users** | The people who log in. Created under the company. |
| **Leads** | The people to call. One record per phone number. Shows where things stand **right now**. |
| **Call log** | One record per attempted call, kept forever. Shows **everything that ever happened**. |

The last two are separate on purpose:

- A **lead** answers *"where does this person stand today?"*
- The **call log** answers *"what did we try, and what did they say each time?"*

So the lead stays short and easy to read, while nothing is ever lost.

A lead has **one identifier**: the `id` returned when you create it. There is no separate lead
number or action number to keep track of.

One lead is kept per phone number. Adding the same number twice is refused; use *Add-or-update*
if you want the newer information to win.

### A lead starts almost empty

When a new enquiry arrives, all you send is:

```json
{ "name": "Shrikant", "phone": "+919876543210", "project": "My Home Sanctuary" }
```

That is a complete lead. It is saved with the status **`new`** and is immediately due to be
called. Nothing else is filled in, because nothing else is known yet - what they want, when to
visit, whether to call back are all answers that only exist **after** somebody has spoken to them.

When the agent reports the call, that same record fills in: the name it heard, the project they
asked about, their question, what was agreed, and when. And the status changes - to booked, to
call-back-Tuesday, to try-again-later, or to closed.

So the shape of a lead tells you where it is: an almost-empty record is one nobody has reached
yet; a full one has been spoken to.

---

## What the statuses mean

**Where a lead is right now:**

| Status | In plain words |
|---|---|
| `new` | Just added. Nobody has called yet. |
| `queued` | Ready to be called. |
| `dialing` | Being called this moment. |
| `retryScheduled` | Didn't reach them. Will try again at a set time. |
| `callbackScheduled` | They asked for a specific time. Booked. |
| `completed` | Finished. Look at the final result for what happened. |
| `exhausted` | Tried the maximum number of times, never reached them. |
| `suppressed` | They asked never to be called. We won't. |
| `failed` | Something technical went wrong. Needs a person to look. |

**How the phone call itself went:**

| Result | Meaning |
|---|---|
| `answered` | They picked up |
| `noAnswer` | Rang out |
| `busy` | Line was busy |
| `rejected` | They declined the call |
| `voicemail` | Went to voicemail |
| `invalidNumber` | The number doesn't exist — we stop, no point retrying |
| `failed` | A technical fault on our side |

**What the person actually said (only if they picked up):**

| They said | What happens next |
|---|---|
| `siteVisitBooked` | Visit booked. Lead is a win. |
| `callbackRequested` | Call them back at their chosen time. |
| `rescheduled` | Moving an existing appointment. |
| `detailsRequested` | Send project details on WhatsApp. |
| `interested` | Keen, but nothing booked yet. |
| `notInterested` | Closed. We stop. |
| `doNotCall` | Blocked permanently. |
| `wrongNumber` | Not the right person. Closed. |
| `languageBarrier` | Couldn't communicate. Try again, maybe another agent. |
| `unqualified` | Not a real buyer. Closed. |
| `noDecision` | Talked, but decided nothing. Try again later. |

**How the lead finally ended:** `siteVisitBooked`, `siteVisitDone`, `interested`,
`notInterested`, `unreachable`, `doNotCall`, `wrongNumber`, `unqualified`, `duplicate`.

---

## The rules for calling back

These are **settings, not code** — an admin can change them without a developer, in
`PUT /api/v1/organizations/current/call-policy`.

| Setting | Default | What it does |
|---|---|---|
| Maximum attempts | 4 | After this many failures to reach someone, we stop |
| Attempts per day | 2 | Never call the same person more than twice a day |
| Calling hours | 9:00 am – 8:00 pm | Never ring outside these hours, in your local time |
| Wait after "busy" | 30 minutes | |
| Wait after "no answer" | 2 hours | |
| Wait after "voicemail" | 4 hours | |
| Wait after "they declined" | 24 hours | A rejection is a soft no — give them a day |

Sensible things this gets right:

- If the wait lands at 11:30 pm, the call is moved to **9:00 am the next morning** instead.
- Someone who **asked** for a callback gets it even if we'd already used up their attempts.
- A **wrong number** is closed at once — no point trying it four times.
- **"Never call me"** is permanent. Nothing puts that lead back in the queue.
- If a call gets stuck halfway (say the agent crashes), the lead is automatically returned to the
  queue instead of being lost.
- Two callers running at once can never be handed the same person.

---

## The dashboard numbers

One request — `GET /api/v1/dashboard/summary` — returns everything the dashboard draws, for
whichever period you pick.

### Choosing the period

| Add this | You get |
|---|---|
| `?range=today` | Since midnight |
| `?range=week` | The last 7 days |
| `?range=fifteenDays` | The last 15 days |
| `?range=month` | The last 30 days (this is the default) |
| `?range=quarter` | The last 90 days |
| `?range=all` | Everything ever recorded |
| `?range=custom&from=2026-08-01T00:00:00Z&to=2026-08-31T23:59:59Z` | Exactly those dates |

The period applies to the whole page at once: leads are counted by **when they arrived**, calls
by **when they were placed**. So the boxes and the charts always describe the same stretch of
time, and the response repeats the window back to you so there is no guessing.

### Where each number comes from

Two different questions, two different sources:

- **"Where do things stand?"** — the boxes and the status breakdowns — is read from the
  **lead** records. A lead has one current status, so it is counted once.
- **"What actually happened?"** — calls made, pick-up rate, talk time, what people said, the
  daily chart — is read from the **call log**, which keeps every attempt forever.

That is why a person you missed once and reached on the second try shows up as **one** lead
(closed) but **two** calls (one missed, one answered). Both numbers are right; they are just
answering different questions.


**The headline boxes**

| | |
|---|---|
| Total leads | Everyone in the system |
| Pending | Still waiting for a call |
| In progress | Being called right now |
| Completed | Finished, one way or another |
| Converted | Booked a site visit — the number that matters |
| Exhausted | Gave up after too many failed attempts |
| Do not call | Asked never to be contacted |
| Due now | Ready to be called this minute |

**Call quality**

How many calls were made, how many were answered, the **pick-up rate**, total and average
talking time, how many recordings are ready, and today's figures on their own.

**The charts**

A breakdown by every status above, each with a count and a percentage, plus a day-by-day trend
of calls made and answered. Days with no calls are included as zero, so the chart line doesn't
jump about.

---

## Call recordings

When the agent reports what happened on a call, it can attach a link to the recording. From then
on the call is listed as playable:

- `GET /api/v1/calls/recordings` — every call you can listen to, newest first
- `GET /api/v1/calls/{callLogId}/recording` — the audio link for one call

Each result includes `playable: true` when the audio is ready. If it's `false`, that call either
wasn't recorded or the link hasn't arrived yet.

> The connection to the calling platform that produces these recordings automatically is not
> built yet — it is planned as a later step. Until then the link is supplied by whatever places
> the call.

---

## Who can do what

| Role | Can do |
|---|---|
| **Org admin** | Everything: add and remove users, change calling rules, manage the API key, delete leads |
| **Manager** | See everything, delete individual leads, list the team |
| **Agent** | Work leads, make calls, record what happened |
| **Viewer** | Look only — dashboard, leads and recordings. Cannot change anything |
| **AI agent (API key)** | Read and store leads, take work, record call outcomes. No user or settings access |

If someone tries something their role doesn't allow, they get a clear "you don't have access to
this" instead of a confusing error.

---

## Settings

Everything lives in **`src/main/resources/application.yml`**, already filled in with working
local values. The ones worth knowing:

| Setting | Default | |
|---|---|---|
| `spring.data.mongodb.uri` | local database | Where the data is stored |
| `app.security.jwt-secret` | a development key | **Change before real use** |
| `app.security.bootstrap.admin-email` | `admin@vedryxtech.com` | The first login created |
| `app.security.bootstrap.admin-password` | `Admin@12345` | **Change before real use** |
| `app.security.bootstrap.api-key` | `vdx_local_dev_key_...` | **Change before real use** |
| `app.security.bootstrap.seed-leads` | `true` | Loads the 14 sample leads. Set `false` for your own data |
| `app.dialer.scheduler-enabled` | `false` | Background sweep for stuck calls. Off: nothing runs on a timer |

Values under `bootstrap` only apply the first time, when the database is empty. Changing them
later does not overwrite anything that already exists — to change the API key afterwards, use the
rotate endpoint.

---

## Testing with Postman

Two files in the **`postman/`** folder:

| File | |
|---|---|
| `AI-Voice-Agent-Dashboard.postman_collection.json` | Every endpoint, in folders |
| `AI-Voice-Agent-Local.postman_environment.json` | Points at `http://localhost:8080` |

**To use it:** open Postman → *Import* → drop both files in → pick *AI Voice Agent - Local*
from the environment dropdown at the top right.

Then run **1. Login and accounts → Login**. It saves the token for you, and every other
request in the collection uses it automatically — no copying and pasting.

The folders follow the order you would actually use them:

| Folder | |
|---|---|
| 1. Login and accounts | Get a token, manage teammates |
| 2. API key for the AI agent | Create, view and revoke the agent key |
| 3. Leads | Add and search the people to call |
| 4. Calls and history | Take work, report outcomes, read the history |
| 5. Dashboard | One request per date range |
| 6. AI agent (API key) | The same work, authenticated the way the agent does it |
| 7. Health | Is it running |

Requests that create something save the id for the next one, so you can run *Create a fresh
lead* → *Take work to call* → *Report what happened* straight through without editing
anything. Several requests also carry checks, so **Run collection** tells you pass or fail
rather than leaving you to read the JSON yourself.

Requests that are *meant* to fail say so in their name — *Duplicate number -> 409*,
*Wrong key -> 401* — and their checks expect that failure.

---

## If something goes wrong

| What you see | What it means |
|---|---|
| `401 Unauthorized` | Not logged in, token expired, or the API key is wrong. Log in again and re-Authorize. |
| `403 Forbidden` | You're identified, but your role isn't allowed to do this. |
| `404 Not Found` | No such record. |
| `409 Conflict` | Usually this phone number is already a lead. Use *Add-or-update* instead. |
| `422` | Something's missing for what you asked, e.g. booking a callback with no time. The message says exactly what. |
| Won't start: "Port 8080 already in use" | It's already running in another window. Close it first. |
| Won't start: database error | MongoDB isn't running. |

Every error comes back the same way, with a plain sentence explaining the problem.

---

## For developers

- **[docs/TECHNICAL.md](docs/TECHNICAL.md)** — data model, indexes, the state machine, security
  design, configuration
- **`postman/`** — a Postman collection covering every endpoint, with the token captured
  for you. See above.
- **`api.http`** — the same requests, ready to run from IntelliJ
- **`mvn test`** — the test suite

Built with Java 17, Spring Boot 3.5 and MongoDB.
