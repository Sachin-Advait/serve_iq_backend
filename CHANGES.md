# ServeIQ (queue-backend) — what changed and why

Branch: `hardening-and-optimization`

My write-up of the work done on this service. I've tried to explain the reasoning
rather than just list the changes, because several of them fix things that were
failing silently, and the authentication work changes what the frontend has to
send.

The meeting backend has its own `CHANGES.md` covering the same ground for that
service. The two share a fair amount of copy-pasted code, so several problems
appear in both.

**Read first if you are short on time:**

- §1 — every endpoint was open. There is now authentication, and the frontend has
  to send a token. See §8 for what the UI team needs.
- §3 — three things were broken and failing quietly: push notifications have
  never worked, two customers could be given the same token number, and a quiz
  could lock out everyone it was assigned to.
- §4 — schema management changed. Flyway owns it now, `ddl-auto` is `validate`.

---

## 1. Authentication

`SecurityConfig` had Spring Security on the classpath, which makes this look
protected at a glance, but the chain was:

```java
.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
```

with CSRF, form login and basic auth all disabled. And `AuthController.login`
verified the bcrypt hash correctly but then just returned a `UserResponseDTO` —
no token, no session. So there was nothing to authenticate *with* and nothing
checking anyway. Every endpoint was reachable anonymously.

Concretely, without any credentials you could:

- `GET /serveiq/api/users` — every user, including their FCM device tokens
- `DELETE /serveiq/api/users/{id}` — delete any account
- `GET /serveiq/api/reports/...` and the quiz results — staff PII
- `POST /serveiq/api/whatsapp/...` — send WhatsApp messages through our Twilio
  account, at our cost, from our number

### What I did

Stateless JWT, same approach as the meeting backend: token issued at login,
`JwtAuthenticationFilter` validates it and populates the security context,
`SecurityConfig` denies by default.

**Some endpoints stay public on purpose**, and I want to be explicit about which,
because getting this wrong breaks the lobby:

| Endpoint | Why it stays open |
|---|---|
| `POST /tokens/generate` | A walk-in customer takes a ticket at a kiosk. They have no account and never will. |
| `POST /feedback` | Rating the service afterwards, same reason. |
| `GET /tv-display/**` | The lobby screen has no login. |

Everything else needs a token. If the kiosks turn out to be on a network where
anonymous ticket generation is a problem, the cleanest fix is a device account
per kiosk rather than opening it up again — but that is a product decision, so I
left the current behaviour intact.

The signing key comes from `JWT_SECRET` and the app refuses to start if it is
under 32 characters. HS256 needs 256 bits; a short key weakens the signature
without any visible symptom, which is exactly how it ends up in production.

CSRF stays off deliberately — bearer tokens have to be attached by our own
JavaScript, unlike cookies which browsers send automatically, so there is nothing
to forge.

### Registration was a privilege escalation

`RegisterRequest` has a settable `role`, and `registerUser` trusted it:

```java
user.setRole(dto.getRole());
```

`/auth/register` was public. So `POST {"email":"...","password":"...","role":"ADMIN"}`
gave you an admin account with no prior access at all.

Registration is admin-only now, and still defaults to `USER` when no role is
given rather than trusting whatever arrives.

### Update was an account takeover

`UserController.updateUser` bound the raw `User` entity, and `updateUser` applied
both `role` and `password` from it:

```java
if (userDetails.getPassword() != null) user.setPassword(passwordEncoder.encode(...));
if (userDetails.getRole() != null) user.setRole(userDetails.getRole());
```

on **any** user id, unauthenticated. So `PUT /users/<an-admin-id>` with
`{"password":"whatever"}` reset that admin's password and you logged in as them.

Both removed from `updateUser`. Role moved to `PUT /users/{id}/role` (admin
only), password to `POST /users/me/password`, which is scoped to the caller and
requires the current password.

### Login was an account enumeration oracle

```java
.orElseThrow(() -> new RuntimeException("User not found"));
if (!passwordEncoder.matches(...)) throw new RuntimeException("Invalid password");
```

Two different errors, so you could tell whether an email had an account by the
message. Both paths now return the same 401 with no body.

### Other exposure

`fcmToken` was serialised in `UserResponseDTO`, so `GET /users` handed out every
user's device push token — enough to push notifications to someone else's phone.
Now write-only.

CORS was `allowedOriginPatterns("*")` with `allowCredentials(true)`, on both HTTP
and the websocket handshake. That pattern reflects back whatever `Origin` the
caller sends, which is effectively no CORS at all. Now an explicit list from
`CORS_ALLOWED_ORIGINS`.

---

## 2. Secrets were committed

`application.properties` had the Postgres password and the live Twilio SID and
auth token. `CloudinaryConfig.java` had the Cloudinary API secret hardcoded in
the `@Bean` method. `docker-compose.yml` had DB credentials.

There was already `dotenv-java` wired up in `ServelqApplication` for the Twilio
keys — but the properties file still pinned the literal values, so the real
credentials were what actually bound and the dotenv path was effectively dead.

Everything comes from the environment now with no fallback, so a missing required
variable stops the app at startup instead of letting it come up pointed at the
wrong database. `.env.example` documents every variable, `README-CONFIG.md`
covers local, Docker, compose and Kubernetes.

The optional integrations — Twilio, Cloudinary, Firebase — degrade instead of
crashing when their credentials are absent. They log a warning and skip. That
means a developer can run this locally without being handed production API keys,
which is the point.

I also generalised the dotenv loading: it copies the whole `.env` into system
properties now, not just three Twilio keys, and never overwrites a real
environment variable, so deployed environments are unaffected.

---

## 3. Three things that were quietly broken

These are the ones I'd want to know about first, because none of them announce
themselves.

### Push notifications have never worked

`FCMService` calls `FirebaseMessaging.getInstance()`. That throws
`IllegalStateException` unless a `FirebaseApp` has been initialised — and nothing
in this codebase ever initialised one. No `FirebaseOptions`, no `initializeApp`,
no credentials loading, anywhere.

So every call threw, and the bare `catch (Exception e)` swallowed it into a log
line. Every training assignment and quiz notification since this was written has
silently failed to send.

Added `FirebaseConfig`, which loads the service account from
`FIREBASE_CREDENTIALS_PATH`. If it isn't configured, the service now skips with a
warning and says so clearly, rather than throwing once per notification.

While in there: both notify methods looped the user ids doing one `findById` and
one blocking `send` each — assigning to 50 people meant 50 selects and 50 serial
network calls, on the caller's thread. Now one query for the users and one
multicast per 500 tokens (500 is FCM's cap).

### Two customers could be given the same token number

The sequence comes from `MAX(token_seq) + 1` and is then inserted. That is a
read-modify-write with nothing serialising it, so two kiosks issuing at the same
instant both read the same maximum and both compute the same next number.

There *was* a retry meant to handle this:

```java
} catch (DataIntegrityViolationException e) {
    if (++attempts >= maxAttempts) throw ...;
```

It could never fire, for two independent reasons:

1. **No unique constraint existed.** `@Table(name = "tokens")` declared none, and
   `ddl-auto=update` never adds one. So the duplicate insert simply succeeded —
   nothing threw, and two people were handed the same number and both called to a
   counter.
2. **Even with a constraint it would not have worked.** `createTokenOnce` was
   `private` and called as `this.createTokenOnce(...)`. Spring's `@Transactional`
   works through a proxy, and a self-invocation never goes through the proxy, so
   that annotation did nothing. All three "attempts" ran inside the *same*
   transaction opened by `generateToken` — which, after the first constraint
   violation, is already marked rollback-only. The retry would have failed with
   `UnexpectedRollbackException`.

Fixed properly:

- Added a unique constraint on `(branch_id, priority, token_seq, token_date)`,
  plus a `token_date` column, because the sequence restarts daily so uniqueness
  has to be scoped to a day.
- Moved the single attempt into `TokenIssuer`, a separate bean, with
  `REQUIRES_NEW`. Being a separate bean means the proxy is involved, and
  `REQUIRES_NEW` means each attempt genuinely is a fresh transaction.
- `generateToken` is no longer `@Transactional`, so a failed attempt does not
  poison the next one.

The V1 migration backfills `token_date` and de-duplicates existing rows before
applying the constraint — any existing duplicates get renumbered above the
current maximum for their day, keeping the earliest row on its original number.

### A quiz could lock out everyone it was assigned to

```java
if (existing.size() >= quiz.getMaxRetake()) throw ...;   // per-user count
...
quiz.setMaxRetake(quiz.getMaxRetake() - 1);              // shared column
quizSurveyRepo.save(quiz);
```

`existing` is this user's own attempts, so the check is per-user — but
`maxRetake` is a single column on the shared quiz row, and it was decremented on
every submission by anybody.

So a quiz with `maxRetake = 3` assigned to 100 staff: after any three
submissions in total, `maxRetake` hits 0, and from then on `existing.size() >= 0`
is true for everyone. The remaining 97 people are told "Max quiz attempts
exceeded" without ever having opened it. The stored value then keeps counting
down into negatives and is served to clients as the displayed limit.

The per-user check was always the actual rule, so I deleted the decrement. That
also removes a write on the hot submission path — `save(quiz)` rewrote the whole
row including both large `jsonb` columns (`definitionJson` and `answerKey`) just
to change one integer.

There's a regression test for this that asserts one user's submissions do not
consume another's allowance, and that the quiz row is not written at all during a
submission.

---

## 4. Schema management changed

`spring.jpa.hibernate.ddl-auto` was `update`, meaning Hibernate altered the live
schema at every boot. That is convenient early on and a problem later:

- it only adds, never renames or removes, so mistakes accumulate
- it never adds the unique constraints or indexes the domain needs
- **it does not backfill defaults.** A Java field default like
  `private Boolean isTransfer = false` is not written to the column definition,
  so when `ddl-auto` adds that column to a table with existing rows, those rows
  get `NULL`. `ReportService` then does `.filter(Token::getIsTransfer)`, which
  unboxes `Boolean` null and throws NPE — the transferred-tokens report is broken
  for any branch with data predating that column.

Flyway owns the schema now, and Hibernate is set to `validate` so it checks the
mapping matches and refuses to start if it drifts.

`baseline-on-migrate=true` is set, so pointing this at the existing database is
safe — Flyway records the current state as the baseline and only applies
migrations above it. `V1__baseline_indexes_and_token_uniqueness.sql` adds the
token uniqueness described above, backfills `is_transfer`, and creates the
indexes in §5.

For a throwaway local database you can still set `JPA_DDL_AUTO=update`. Not
anywhere shared.

---

## 5. Performance

### The `tokens` table had no indexes

None at all, beyond the primary key on a random UUID which is useless for every
query we actually run. Meanwhile everything filters on
`branch_id` / `status` / `priority` / `created_at`, and the table accumulates
every token ever issued with no archival.

Worst case is `findNextToken`, which runs on every agent "call next": a native
query that scans for `status = 'WAITING'`, sorts by priority, and takes one row —
while holding a `PESSIMISTIC_WRITE` lock. So the busiest endpoint in the system
was doing a full scan and sort under a lock, degrading continuously as history
built up.

V1 adds composite indexes for the common filters, plus a partial index
(`WHERE status = 'WAITING'`) matching `findNextToken`'s exact ordering so it can
walk the index and stop at the first row instead of scanning and sorting.

I've also indexed `counters`, `services`, `feedback` and `responses.submitted_at`.

One thing I did **not** fix, and want to flag: `counter_ids` is stored as a
comma-joined string and matched with `LIKE '%,id,%'`. A leading wildcard cannot
use a b-tree index, so those predicates will always scan. Fixing it properly
means normalising to a child table or a Postgres array with a GIN index, which is
a schema and data migration plus changes to the transfer logic — more than I
wanted to bundle in here. Worth doing as its own piece of work. The indexes above
still help those queries via the status/priority ordering.

### The TV snapshot was nine round trips, on every event

`getTVDisplayData` is rebuilt and broadcast on *every* token event — created,
called, serving, completed, transferred, held, no-show. It was doing:

- the branch lookup
- `findLatestCalledTokens`, unbounded, then `.limit(4)` in Java
- three `findByBranchIdAndStatus...` calls, unbounded, then `.limit(10)` in Java
- four separate `countByBranchIdAndStatus` queries

So Postgres shipped and Hibernate hydrated the entire queue in order to display
ten rows of it, four times over for counts, many times per minute.

Now the limits are pushed into the query with `Pageable`, and the four counts are
one `GROUP BY status`. Statuses with no rows are absent from a `GROUP BY`, so the
caller defaults them to zero — worth noting because that is an easy way to
introduce a subtle bug when collapsing counts.

### Analytics loaded whole tables

`getLowScoringUsers` called `responseRepo.findAll()` — every response row for
every quiz ever recorded — then filtered by date in Java. Each of those rows
drags its `jsonb answers` blob through Hibernate, and since it is an eager basic
attribute it gets deserialised into a `Map` for every row, despite this report
never looking at it.

Now the date filter and null guards are in the query, and only
`userId`, `score`, `maxScore` come back.

I deliberately kept the averaging in Java so the arithmetic does not change: it
is the mean of per-attempt percentages, not total score over total max, and those
give different answers. Doing it as a SQL aggregate would have been faster still
but risked quietly changing the numbers people report on. I also added a guard
for `maxScore = 0`, which previously produced `Infinity` and silently dropped the
user out of the comparison.

`getFeedbackSummary` loaded the whole feedback table and walked it three times to
produce three counts — one `GROUP BY` now. The feedback listing returned the
entire table with no paging and is now paged, newest first.

### Blocking calls moved off the request path

The Twilio WhatsApp send happened inside the token-creation transaction, before
commit. So a pooled database connection was held open for the whole HTTP round
trip to Twilio — that is how connection pools get exhausted under load — and the
customer's response waited on it.

It is now `@Async` and runs after the token is committed. That is also a
correctness improvement: previously a rollback after the send still left the
customer holding a message about a token that did not exist.

I kept this as a separate method (`sendTokenNotificationAsync`) rather than
annotating the shared `sendMessage`, because `WhatsAppController` calls that one
and returns its result — adding `@Async` there would have made it start returning
`null` and quietly broken that endpoint.

`@EnableAsync` was missing entirely, which is worth calling out: `FCMService`
already had `@Async` on two methods and they were running synchronously on the
request thread the whole time. Adding it fixes those as a side effect.

---

## 6. Dependencies

Parent was Spring Boot 3.5.7. Trivy reported **4 critical and 28 high** CVEs —
including a Spring Security policy bypass, Tomcat authentication bypass, a large
cluster of Netty request-smuggling issues coming in via Firebase and gRPC, and a
Postgres driver MITM downgrade.

Moved to 3.5.16 and pinned the transitives Firebase and Cloudinary drag in:
`guava 32.1.3`, `protobuf 3.25.5`, `commons-io 2.14.0`, `org.json 20231013`,
`grpc-netty-shaded 1.75.0`, `netty 4.1.136`, `postgresql 42.7.12`.

Result: **0 critical, 0 high.**

Also cleaned up the leftover Spring Initializr scaffold while in there — the
description still said "Demo project for Spring Boot", groupId was `com.example`,
and there were empty `<licenses>`, `<developers>` and `<scm>` blocks.

---

## 7. Logging and error responses

The exception handler had a catch-all:

```java
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<...> handleRuntimeException(RuntimeException ex) {
    return ResponseEntity.badRequest().body(new ApiResponseDTO<>(false, ex.getMessage(), null));
}
```

`RuntimeException` is more specific than `Exception`, so Spring picks it first
and this shadowed the 500 handler completely — every internal failure came back
as `400 Bad Request` with the raw exception message in the body. That both
misleads the client about whose fault it is and leaks internals.

Removed; those now fall through to the generic handler. Added 401 and 403
handlers now that authentication exists.

Prod logging dropped from `DEBUG` to `INFO` — `com.gis.servelq` and
`org.springframework.web` were both at `DEBUG`, which on a service handling this
much websocket traffic is a lot of output for no benefit. Also removed a leftover
`System.out.println("MATERIAL->>>>>>>>...")` in `TrainingService`.

---

## 8. What the UI team needs to do

**This is the section that needs frontend work.** Nothing else here changes the
API shape, but authentication does, and the app returns 401 for everything until
the frontend sends a token.

### What changed on the wire

`POST /serveiq/api/auth/login` used to return a `UserResponseDTO` directly. It
now returns:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 43200,
  "user": {
    "id": "...", "name": "...", "email": "...", "role": "ADMIN",
    "branchId": "...", "counterId": "..."
  }
}
```

Note the user object is nested under `user` now — that is a breaking change to
the login response shape, so wherever you currently read `response.data.name`
it becomes `response.data.user.name`.

Every other request must carry:

```
Authorization: Bearer <token>
```

Without it: **401**. Valid token, wrong role: **403**.

Failed logins return a bare **401** with no body — deliberately, so it cannot be
used to work out which emails have accounts. Show a generic "invalid email or
password" for it.

### Minimum change

```js
// on login
const { data } = await api.post('/serveiq/api/auth/login', { email, password, fcmToken });
localStorage.setItem('token', data.token);
setCurrentUser(data.user);          // note: nested now

// once, at setup
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

> On storage: `localStorage` is simplest and matches how the app works today, but
> any XSS becomes token theft. If you want to tighten that later, the options are
> an in-memory token with a refresh on reload, or httpOnly cookies — the latter
> means we re-enable CSRF protection server side, so let's discuss before going
> that way.

### The kiosk and TV clients do NOT need a token

Worth being explicit, because it's easy to add auth everywhere and break the
lobby:

- `POST /tokens/generate` — no token needed
- `POST /feedback` — no token needed
- `GET /tv-display/**` — no token needed

Everything else does.

### Endpoints that moved

| Was | Now | Why |
|---|---|---|
| `PUT /users/{id}` with `{"role": ...}` | `PUT /users/{id}/role`, admin only | role was mass-assignable |
| `PUT /users/{id}` with `{"password": ...}` | `POST /users/me/password` with `currentPassword` + `newPassword` | anyone could reset any password |
| `POST /auth/register` (public) | same URL, admin token required | it accepted `role`, so it minted admins |
| `GET /feedback` returning an array | same URL, now a `Page` — `{content: [...], totalElements, ...}` | it returned the entire table |

That last one is a response-shape change: `response.data` becomes
`response.data.content`, and you get `totalElements` / `totalPages` for paging.
It takes `?page=` and `?size=` (default 50, newest first).

### Websockets

The STOMP endpoint is unchanged (`/serveiq/ws`) but no longer accepts any origin.
Only origins in `CORS_ALLOWED_ORIGINS` can connect — so give us **every** origin
the UI is served from, including the kiosk and TV display URLs. Those are the
easy ones to forget and they will simply fail to connect with a CORS error.

### Roles

`ADMIN`, `MANAGER`, `USER`, `RECEPTIONIST`, `DISPLAY`, `KIOSK`. The token carries
`role` and `branchId`, so the UI can hide actions that would 403 — but treat that
as cosmetic. The server enforces it; hiding a button is convenience, not a
control.

### Long-running displays

TV and kiosk clients don't need tokens, so they are unaffected by expiry. Agent
and counter screens do, and a 12-hour token means a screen left logged in
overnight starts getting 401s in the morning. The interceptor above handles it by
bouncing to login — just make sure that is what you want on a counter terminal
rather than a silent failure. Worth testing by shortening
`JWT_EXPIRATION_MINUTES` in a test environment rather than waiting 12 hours.

### Suggested order

1. Point at a backend running this branch — everything except the kiosk paths
   will 401, which confirms auth is live.
2. Update the login response handling (nested `user`) and add the interceptor.
3. Update the feedback listing for the paged shape.
4. Walk the role-gated screens with each role.
5. Check kiosk, TV and counter screens specifically.

---

## 9. Deploy checklist

Order matters.

1. **Set the environment variables.** The app will not start without the required
   ones; that is intentional. `README-CONFIG.md` has the list.
2. **Generate a `JWT_SECRET`** — `openssl rand -base64 48`, different per
   environment.
3. **Set `CORS_ALLOWED_ORIGINS`** to every real frontend origin, kiosks and TV
   displays included.
4. **Back up the database before first start.** Flyway will run V1, which
   backfills `token_date` and `is_transfer` and de-duplicates token sequence
   numbers before applying the unique constraint. It is written to be safe and
   re-runnable, but it does modify existing rows, so take the backup.
5. **Set `JPA_DDL_AUTO=validate`.** If it fails to start complaining about schema
   mismatch, that is Hibernate telling you the migration has not been applied —
   check the `flyway_schema_history` table rather than reaching for `update`.
6. **Deploy backend and frontend together.** The token change is not backwards
   compatible.
7. **Confirm** `/actuator/health/readiness` returns 200, take a ticket at a
   kiosk, call it from a counter, and leave feedback — that exercises the three
   public paths plus an authenticated one.

For rollback: the V1 migration is additive (a new column, backfilled values, new
indexes and a constraint) so the previous version of the application will still
run against the migrated schema. The one thing to know is that the unique
constraint stays behind, so the old code — which had no working retry — will
start throwing on a token sequence collision instead of silently issuing a
duplicate. Arguably still better than the old behaviour, but be aware of it.
