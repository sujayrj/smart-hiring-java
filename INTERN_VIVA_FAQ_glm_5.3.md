# SmartHire Intern Viva — Exhaustive FAQ & Testing Guide `_glm_5.3`

> **Purpose:** question bank for quizzing interns who built the SmartHire hackathon project —
> including teams that used agentic/AI-assisted coding. If they shipped code they didn't read,
> these questions will expose it. Every answer is verifiable against the actual code in
> `smarthire-java-sb/` and the requirement doc `SmartHire_Pipeline_UseCase.md`.
>
> **How to use:** ask the question, listen, then use the *follow-up probe* to test depth.
> A correct headline answer with a failed probe = partial understanding.
>
> **Scoring suggestion:** 🟢 complete + probe passed · 🟡 headline correct, probe failed ·
> 🔴 wrong or "the AI wrote it" · ⚫ cannot locate the file/function.

---

## Table of Contents
- [A. Architecture & Big Picture](#a-architecture--big-picture)
- [B. Startup, Seeding & Jackson](#b-startup-seeding--jackson)
- [C. Security, JWT & Authorization](#c-security-jwt--authorization)
- [D. Résumé Screening Pipeline](#d-résumé-screening-pipeline)
- [E. LLM Abstraction & Guardrails](#e-llm-abstraction--guardrails)
- [F. Candidate Q&A Flow](#f-candidate-qa-flow)
- [G. Scoring, Fusion & Bands](#g-scoring-fusion--bands)
- [H. Flags & Anti-Cheat](#h-flags--anti-cheat)
- [I. Interviews & Role Isolation](#i-interviews--role-isolation)
- [J. Frontend / React](#j-frontend--react)
- [K. Trap Questions (counter-intuitive)](#k-trap-questions)
- [L. Debug Scenarios ("the app does X — why?")](#l-debug-scenarios)
- [M. Requirements vs Implementation](#m-requirements-vs-implementation)

---

## A. Architecture & Big Picture

**A1. In one minute, walk me through the entire pipeline from JD creation to final decision.**
Expected: Admin creates JD → Run triggers per-candidate `resume_match` AI scoring (0–100) →
threshold gate: above → `SCREENING`, below → `ARCHIVED` with reason → candidate takes timed
Q&A, each answer scored 0–5 by `answer_score` → after last answer, fusion weights résumé+Q&A
→ PASS/HOLD/REJECT band → Admin assigns interviewer + date → Interviewer (assigned only)
reviews evidence → decision (ACCEPTED/REJECTED/ON_HOLD/NO_SHOW) + notes. Every step audited.
🔍 *Probe: "Which of those steps involve the LLM, and which are pure application code?"*
(Only 2 calls: resume_match, answer_score. Fusion, bands, flags = application logic.)

**A2. Why a monolith? What did the requirements say about microservices/queues?**
Expected: UseCase lines 48–49 — out of scope; single monolith, no Kafka/S3/queues (guardrails,
line 234). One repo, one Docker container or local run.
🔍 *Probe: "Where would the first split be if you had to scale?"* (LLM calls → async worker.)

**A3. Which tech stack did the requirement mandate, and what did you use?**
Expected: doc allows FastAPI **or** Node/Express **or Java+Spring Boot** (line 152) — we used
Java 17 + Spring Boot 3.2 + Spring Data JPA + H2 + Flyway + Spring Security (JWT), React 18 +
Vite. Storage requirement says in-memory SQLite — we used **H2 in-memory** as the Java-ecosystem
equivalent. ⚫ Ask them to name the file that swaps it (`application.yml` datasource).

**A4. Where does "the browser talks only to the backend" show up in the code?**
Expected: SPA calls only `/api/**` on the backend; backend is the only caller of the LLM API.
Browser never sees API keys (line 162: key in `.env`, never in source).
🔍 *Probe: "Could a candidate call OpenAI directly with your key from devtools?"*
(No — the key never reaches the browser; all LLM calls are server-side.)

**A5. What happens to all data when the backend restarts?**
Expected: H2 is in-memory → everything evaporates → Flyway replays `V1__init.sql` (schema only)
→ `JsonSeedLoader` re-seeds from `seed/Input_Data.json` → identical pristine state. Survives
browser refresh *while running* (line 75), dies on restart (line 235).

---

## B. Startup, Seeding & Jackson

**B1. Who creates the database tables — JPA, Flyway, or the seeder?**
Expected: **Flyway** (`V1__init.sql`). JPA is `ddl-auto: validate` — it only *checks* the
schema matches entities, it never creates tables. The seeder only inserts data.
🔍 *Probe: "What happens if you add a field to an entity but forget the column?"*
(Startup fails: `Schema-validation: missing column` — we hit this live with `resume_gaps`.)

**B2. Which class seeds data, when does it run, and what stops it from double-seeding?**
Expected: `config/JsonSeedLoader implements ApplicationRunner` — runs after the full context
(and after Flyway). Guard: `if (userRepo.count() > 0) return;` (idempotency, line 113).

**B3. Why does the loader read `seed/Input_Data.json` from the classpath instead of the
project-root `input_data.json`?**
Expected: `ClassPathResource` only sees `src/main/resources/**` (packaged into
`target/classes`). Two copies exist; root = canonical editable, resources = runtime. They must
be kept in sync (`cp input_data.json backend/src/main/resources/seed/Input_Data.json`).

**B4. The JSON uses ids like `"jd-1"`, `"candidate1"`. The DB uses `1, 2, 3`. Where does the
translation happen?**
Expected: `JsonSeedLoader` two-pass linking — `jdIdMap` (`"jd-1" → 1L`, captured after save
because `GenerationType.IDENTITY` assigns ids at insert) and `usersByName` (username → User
entity). Candidates resolve `applied_id` via `jdIdMap` and `user` via `usersByName`; the
reverse link `user.candidateRefId` is set in a *second save* after the candidate row exists.
🔍 *Probe: "Why can't you set candidateRefId before saving the candidate?"*
(The candidate's numeric id doesn't exist until INSERT.)

**B5. What does Jackson produce for `public Object rubric;` when the JSON is an object with
arrays?**
Expected: `LinkedHashMap<String, ArrayList<String>>` — untyped binding: `{`→LinkedHashMap
(order-preserving), `[`→ArrayList, scalars → String/Integer/Long/Double/Boolean/null.
🔴 Trap: "It converts to my own Rubric class" — no; that requires a typed field.

**B6. What does `mapper.writeValueAsString(q.rubric)` do and why is it needed?**
Expected: serializes the map back to a JSON **String** so it fits the `questions.rubric TEXT`
column. The rubric is AI-prompt material, never queried relationally. Later
`MockLlmClient.tryBandedRubric` does `readTree()` to parse it again.

**B7. Why do all seed DTO classes have `@JsonIgnoreProperties(ignoreUnknown = true)`?**
Expected: Jackson's default is **fail on unknown properties**. Without it, adding any new key
to the JSON breaks startup with `UnrecognizedPropertyException`. With it, forward-compatible.

**B8. JSON has `"pass_threshold": 70` (integer) but the field is `double`. What happens?**
Expected: automatic coercion — 70 → 70.0. Also missing keys → field defaults (`null` for
objects, `0.0` for primitives). 🔴 Trap: "Jackson throws a type error."

**B9. Field names are `job_descriptions`, `must_have` — snake_case. Why does binding work
without `@JsonProperty`?**
Expected: property matching is **exact name**; the DTO fields deliberately mirror JSON keys.
CamelCase fields would need `@JsonProperty`.

**B10. What Spring Data query finds candidates for a JD, and what SQL does it generate?**
Expected: `candidateRepo.findByAppliedJd(jdId)` → `SELECT * FROM candidates WHERE applied_jd = ?`.
Derived-query naming — no `@Query` needed.

---

## C. Security, JWT & Authorization

**C1. Walk the journey of one authenticated request through the backend.**
Expected: `JwtAuthFilter (OncePerRequestFilter)` → parse Bearer token via `JwtService`
(HS256 signature + expiry) → build `Authentication` with `ROLE_*` → `SecurityContextHolder`
→ URL rule `anyRequest().authenticated()` → `@PreAuthorize` role check → service-layer
*ownership* check. Two separate layers: role gate + ownership gate.

**C2. What exactly is inside the JWT, and where is the secret?**
Expected: `sub`=userId, `username`, `role`, `iat`, `exp` (480 min). HS256 signed with
`JWT_SECRET` from environment (`.env`, never committed — guardrail line 232).

**C3. What's the difference between the 401 and 403 paths in this app?**
Expected: 401 = no/invalid token (entry point fires); 403 = authenticated but role denied
(`@PreAuthorize`) or ownership failed (`ResponseStatusException(FORBIDDEN)` in services).
🔍 *Probe: "Candidate calls GET /api/audit — which code, and who raises it?"* (403, method
security/`@PreAuthorize`.)

**C4. There's a famous bug class here: every 403/404 was surfacing as 401. Explain it.**
Expected: Spring Boot **error dispatch** — an exception renders via `/error`, which passes
through the security filter chain *again* without authentication state → entry point → 401.
Fix: `requestMatchers("/error").permitAll()` in `SecurityConfig`. Teams that never saw this
likely never tested cross-role denials.

**C5. Why is authorization enforced twice (role + ownership)? Give a concrete failure it prevents.**
Expected: role says *what kind* of user; ownership says *whose data*. Candidate passing
`@PreAuthorize("hasRole('CANDIDATE')")` on `GET /applications/2` must still be blocked if app 2
belongs to someone else — `AccessService.assertCanViewApplication` compares
`candidate.userId == JWT userId`. Frontend hiding links is explicitly **not** security
(doc line 203).

**C6. Interviewer isolation — show me the exact enforcement point.**
Expected: `interviewRepo.findByInterviewerId(interviewerId)` — the *query* only returns
assigned rows (InterviewService). Plus `assertCanAccess` on decision, plus
`assertCanViewApplication` INTERVIEWER branch on detail. Not UI filtering.

**C7. Passwords: what algorithm, where validated, and why did the first seed login fail in
development?**
Expected: BCrypt (`PasswordEncoder.matches`). The first `V2__seed.sql` used a copied hash for
the *wrong* plaintext ("password" hash for "admin123") → 401. Now passwords are seeded as
plaintext in JSON and BCrypt-encoded at load time in `JsonSeedLoader`.

**C8. Why is CSRF disabled and sessions stateless? Is that safe?**
Expected: no cookies — bearer token in `Authorization` header, so CSRF (cookie-based attack)
doesn't apply; `SessionCreationPolicy.STATELESS` because JWT carries identity per request.
Doc line 154 sanctions this demo-grade design.

**C9. CORS: the SPA is on 5173, backend on 8081. Why don't requests get blocked, and what is
the preflight?**
Expected: `CorsConfig` allows `http://localhost:5173`; browsers send an OPTIONS preflight for
non-simple requests (Authorization header makes it non-simple) before the real call.

---

## D. Résumé Screening Pipeline

**D1. Click Run ▶ on JD 1. List every table that gets written.**
Expected: `applications` (created/updated with resume_score, confidence, matched/gaps/summary,
status), `candidates` (status mirror), `audit_log` (RUN_RESUME_SCREENING). `job_descriptions`
and `questions` are read-only.

**D2. Where do the `applications` rows come from? Is there an applications section in
`input_data.json`?**
Expected: **No** — applications are created *lazily* by `ScreeningService.screenJd` via
`findByCandidateIdAndJdId(...).orElseGet(create)`. That's why a fresh boot shows an empty
shortlist until someone runs screening, and why the Candidate homepage needs a "waiting" state.

**D3. Which candidates does Run process — all of them?**
Expected: only `findByAppliedJd(jdId)` — candidates whose `applied_jd` matches. Never a global
pool. 🔴 Trap: "It scores every résumé in the system."

**D4. What is the ordering rule between saving scores and changing status, and which
requirement line demands it?**
Expected: persist score/evidence **first**, then set status — same `@Transactional` method
(docx §13 "persist successful scoring before updating status"; UseCase line 220 area).
Partial-failure semantics: per-candidate `ControlledLlmException` is caught → that row is
FAILED, batch continues.

**D5. What does `@Transactional` on `screenJd` actually guarantee here — and what doesn't it
cover?**
Expected: all writes (apps, candidates, audit) commit atomically at method end. It does NOT
make the LLM call transactional or idempotent; and per-candidate failures are *caught*, so the
transaction still commits with a FAILED row.

**D6. Run Run again immediately. What happens?**
Expected: `findByCandidateIdAndJdId` finds existing applications → scores/evidence **updated**,
status recomputed. DB constraint `UNIQUE(candidate_id, jd_id)` prevents duplicates. Same for
candidates re-set to SCREENING/ARCHIVED.

**D7. How does "Run all (batch)" differ from Run?**
Expected: `POST /jds/resume-score-all` loops `jdRepo.findAll()` calling the same `screenJd` —
plus a scheduled variant (`SchedulingConfig`, `@Scheduled`) exists behind
`APP_BATCH_ENABLED` (default **off**), interval `APP_BATCH_INTERVAL_MS`. Scheduler runs with no
SecurityContext → audit actor = `SYSTEM`.

**D8. The screening text sent to the AI is a single string. Construct it.**
Expected: `title + " | must-have: " + mustHave + " | nice-to-have: " + niceToHave + " | " +
summary` — and note `MockLlmClient.extractMustHaveSkills` parses the must-have section back
out by string markers. Fragile coupling worth acknowledging.

**D9. Aarav scores 75 on JD-1 (threshold 70). Neha scores 38. What rows change, exactly?**
Expected: Aarav `applications.status=SCREENING`, `next_steps="Complete the timed Q&A screening"`,
`candidates.status=SCREENING`. Neha `ARCHIVED`, `next_steps="Résumé score below threshold 70.0"`
(**the reason is recorded** — requirement line 102/204), `candidates.status=ARCHIVED`.

---

## E. LLM Abstraction & Guardrails

**E1. Which two prompts exist in the whole system and what are their exact output contracts?**
Expected: `resume_match` → `{score 0–100, matched_skills[], gaps[], summary, confidence 0–1}`;
`answer_score` → `{score 0–5, justification, confidence 0–1, rubric_hits[]}`. Only these two —
doc lines 130, 160 ("no agents, no multi-turn").

**E2. How does the Java code know the LLM response is a `ResumeMatchResult`?**
Expected: the LLM doesn't "know" Java types. Real flow: prompt instructs JSON-only + schema
(structured output/function-calling) → provider returns JSON text → **Jackson**
`readValue(raw, ResumeMatchResult.class)` → application-side range validation (0–100, 0–1) →
else one retry → `ControlledLlmException`. Note `@JsonProperty("matched_skills")` needed for
snake_case. 🔴 Trap: "Spring/annotation magic converts it automatically."

**E3. Why is `MockLlmClient` marked `@Primary`, and what's the danger when adding a real
client?**
Expected: two `LlmClient` beans would be ambiguous → boot failure or wrong injection. Correct
pattern: `@ConditionalOnProperty(name="llm.provider", havingValue="mock", matchIfMissing=true)`
on mock, `havingValue="openai"` on the real one. And **no silent fallback** to mock when the
real provider fails — that would fake evidence.

**E4. The requirement says "retry at most once on invalid JSON." Where is that enforced and
how would you unit-test it?**
Expected: `LlmClient.validateOrRetryOnce` — parse → fail → retry with correction instruction →
parse → else `ControlledLlmException`. Tests: WireMock/MockRestServiceServer with (a) valid,
(b) malformed, (c) valid-but-out-of-range, (d) fails twice → exception. Doc lines 134, 221, 230.

**E5. MockLlmClient scores résumés by keyword matching. Name two failure modes it has, and the
mitigation built in.**
Expected: (1) can't read negation — "No SQLAlchemy experience" would match `sqlalchemy`;
mitigated by `positiveStatements()` dropping negated sentences. (2) substring false positives —
`sql` matches inside `sqlalchemy`; and it's optimistic (confidence 0.85 constant). Real LLM
understands semantics — that's why Sameer (contradictory over-claim) fools the mock but not a
real evaluator.

**E6. Why does the mock return confidence 0.85 constantly, and why is that a problem for the
Flags demo?**
Expected: flags trigger at confidence < 0.6 — the mock rarely produces that for résumés, so
`LOW_CONFIDENCE` flags mostly won't fire with mock data; the dataset's "low-confidence" edge
case (Ananya) shows up as archived-by-score instead. Honest limitation to state.

**E7. Switching to Spring AI / a real OpenAI client — what must change and what must not?**
Expected: add starter + `ChatClient` adapter implementing `LlmClient` behind
`@ConditionalOnProperty`; add `@JsonProperty` snake_case mapping, temperature 0, structured
output, validation + single retry. **Must not change:** `ScreeningService`, `QaService`,
controllers. Also Boot/Spring AI version compatibility check.

---

## F. Candidate Q&A Flow

**F1. "One question at a time" — where is it enforced, client or server? Show the code.**
Expected: server — `QaService.getQuestions` returns
`questions.skip(answerRepo.findByApplicationId(id).size())`: the *next unanswered* question
onward. Client merely renders `qs[0]`. Plus DB `UNIQUE(application_id, question_id)` blocks
duplicate answers even if the client retries.

**F2. What stops a candidate from answering question 5 first, or answering the same question
twice?**
Expected: skip()-sequencing (only unanswered, in `order_index` order, are returned) +
`uq_answer` unique constraint + explicit duplicate check that throws
`IllegalStateException`. Also `assertCanViewApplication` ownership before any of it.

**F3. Timer auto-submit — what does the client send at 0:00 and how is it distinguished from a
manual submit?**
Expected: same endpoint, `timeout: true` + `timeTakenSeconds`. Stored as `answers.timeout_submit`
— visible in Admin drill-down and candidate breakdown ("auto-submitted (timeout)").
🔍 *Probe: "Can a candidate fake timeTakenSeconds?"* (Yes — client-supplied; it's demo
telemetry, not trusted security data. Server records but doesn't verify wall-clock.)

**F4. What happens in the backend the moment the LAST question is answered?**
Expected: `submitAnswer` detects `answered >= total` → calls `fuse(applicationId)` → normalizes
Q&A, applies JD weights, sets `combinedScore`, `band`, `next_steps`, status → `FUSED`, audit
`SCORING_COMPLETE`. 🔴 Probe: "Can fusion run twice?" (Guarded: only when answered count
crosses total; duplicate answers blocked by unique constraint.)

**F5. Anti-cheat: list the three captured signals, where they're captured, where they're
stored, who sees them, and what they can NEVER do.**
Expected: `visibilitychange`→TAB_SWITCH, `paste`→PASTE, `copy`→COPY_QUESTION — captured in
`Qa.jsx` client-side, POSTed to `/api/anticheat` (CANDIDATE role), stored in
`anticheat_events`, surfaced aggregated to **Admin only** in Flags/DrillDown. They **never**
auto-reject or alter scores (lines 51, 84, 172, 217–218).

**F6. Why does the Q&A page fetch questions only once on mount, and how does "Continue Q&A"
work after a refresh mid-test?**
Expected: server `skip(answeredCount)` makes it stateless — refresh → fetch again → first
*unanswered* question returns; `answeredCount` previously-submitted are skipped. Progress =
`answered/total` client-side.

**F7. Candidate completes all 6 questions of JD-1 but only 2 existed at fuse time in an old
run — what breaks? How do totals work?**
Expected: `total = questionRepo.findByJdIdOrderByOrderIndex(jdId).size()` — currently 6 for
jd-1. Fusion fires when `answered >= total`. If the question bank changes mid-flight, counts
shift — acknowledged demo fragility.

---

## G. Scoring, Fusion & Bands

**G1. A candidate's answers are 4,3,5,4,3,4. Walk me to the final band for JD-1 (threshold 70,
weights 0.6/0.4, résumé 75).**
Expected: avg = 23/6 ≈ 3.833 → ×20 = 76.67 → combined = 0.6×75 + 0.4×76.67 = 45 + 30.67 =
75.67 → ≥70 → **PASS**. 🔴 Probe: "Who computes this — the LLM?" (No — `QaService.fuse`, pure
Java.)

**G2. Where exactly is the HOLD boundary, and is that number in the requirements?**
Expected: `combined >= 0.8 × threshold` → HOLD (in `QaService.determineBand`), i.e., 56 for
threshold 70. **The UseCase never defines the HOLD boundary** — it's an implementation
interpretation. Teams should be able to say that openly.

**G3. List every score field on Application and the scale of each. Who computes combined?**
Expected: `resumeScore` 0–100 (LLM), `resumeConfidence` 0–1 (LLM), `qaScore` 0–100 (app —
normalized avg of answers), `combinedScore` 0–100 (app — weighted), `band` (app rule).
Confidence is the AI's self-assessed reliability — NOT a candidate score.

**G4. Where does the answer confidence feed into anything today? What does the requirement ask
for instead?**
Expected: currently **nowhere in fusion/flags** — only `resumeConfidence` feeds
`LOW_CONFIDENCE` (FlagService). Requirement (line 107/218) says **average confidence < 0.6**
across assessments. Known gap — the correct fix averages answer confidences (+ résumé).

**G5. An Admin overrides HOLD → PASS. Which fields change, which don't, and what proves it
happened?**
Expected: `applications.band` changes (status unchanged), `next_steps` optionally; audit row
`OVERRIDE_BAND` with "HOLD → PASS". Nothing recomputes scores. Endpoint
`POST /applications/{id}/override-band`, ADMIN only — implements requirement line 224
("Admin can review the LLM result before the final call").

**G6. Why is fusion done in Java and not asked from the LLM?**
Expected: deterministic, configurable (weights/threshold are JD columns editable in UI, line
223), auditable, cheap. LLM outputs are validated inputs; business math stays in code.

---

## H. Flags & Anti-Cheat

**H1. Are flags stored in a table? Explain the design.**
Expected: **No — computed on read.** `FlagService.computeFlags()/flagsFor(app)` derive flags
from current data: `resumeConfidence < 0.6` → LOW_CONFIDENCE; `|resumeScore − qaScore| > 30` →
SCORE_DIVERGENCE; any `anticheat_events` rows → ANTICHEAT_TELEMETRY. Only the raw telemetry is
persisted. Consequence: recomputes on every Flags page load.

**H2. Which requirement lines authorize this page, and who can call its API?**
Expected: lines 104/107/123/171–172/217–218. `GET /api/flags` is
`@PreAuthorize("hasRole('ADMIN')")` — interviewer/candidate get 403.

**H3. Name a scenario where a candidate is flagged but should still be hired — and one where
no flag exists but the Admin should worry.**
Expected: flagged divergence (great résumé, bad test day/interview nerves) — human may still
proceed after review. No flag: perfectly consistent weak candidate — flags measure *anomaly*,
not quality. This is the whole "evidence not verdict" design.

**H4. The Flags page shows "3 suspicious event(s) recorded". What's missing versus the
requirement, and where is the raw data?**
Expected: requirement (line 116/217) implies the breakdown (tab-switch count, paste count,
copy-question count). Raw rows are in `anticheat_events` with `event_type` — the aggregation
just doesn't split by type in `FlagView`. Improvement: group-by event_type.

---

## I. Interviews & Role Isolation

**I1. Who schedules the interview, from which screen, hitting which endpoint — and which two
tables change?**
Expected: Admin, from DrillDown (`/applications/{id}`) actions panel →
`POST /api/applications/{id}/assign-interviewer` → `InterviewService.assign` inserts/updates
`interviews` (interviewer_id, scheduled_at, SCHEDULED) and sets `applications.status =
INTERVIEW_SCHEDULED`, `next_steps`, audit `ASSIGN_INTERVIEWER`. Upsert semantics:
re-assigning updates the same interview row.

**I2. How does the scheduled interview reach the candidate's homepage without the candidate
doing anything?**
Expected: it doesn't "reach" — homepage fetches `GET /api/me/applications` on mount; `toView`
joins `interviews` + `users` to add `interviewerName` + `interviewAt`. Visible on next
load/refresh. Requirement lines 112, 196, 211.

**I3. Record a NO_SHOW decision — what changes across tables?**
Expected: `interviews.decision=NO_SHOW`, `status=COMPLETED`; `applications.status=
INTERVIEW_DONE`, `next_steps="Candidate did not attend the interview"`; audit
`RECORD_DECISION`. Gate: `assertCanAccess` — assigned interviewer or Admin only.

**I4. Why is the decision an enum (ACCEPTED/REJECTED/ON_HOLD/NO_SHOW) and not free text?**
Expected: requirement line 124 demands structured toggle decisions; free text is the *notes*
(line 125), which are timestamped `Interview.Note` embeddables with author role.

**I5. Can Admin assign an interview for a candidate still mid-Q&A? Is that a bug?**
Expected: yes it's allowed — `assign()` doesn't check band/status. Arguably intentional
flexibility (admin judgment), but worth flagging as a validation gap if strictness is wanted.

---

## J. Frontend / React

**J1. After login, how does the app decide to land on /admin vs /candidate vs /interviewer?**
Expected: `Login.jsx` → `setSession(token, role, username)` →
`navigate(`/${res.role.toLowerCase()}`)` — role string becomes the URL. No per-role if/else in
login. Then `RoleRoute` re-verifies from localStorage.

**J2. What is `children` in `<RoleRoute role="ADMIN"><Layout>…</Layout></RoleRoute>`?**
Expected: everything nested between the tags is the `children` prop. RoleRoute returns
`children` if authorized, else `<Navigate>`; `Layout` renders `children` inside `<main>`.
JSX nesting = prop passing.

**J3. What does `navigate()` actually do — call App.jsx?**
Expected: no — it changes browser history URL; React Router observes, re-runs route matching,
and App re-renders the matched route's element. Event model, not call model.

**J4. Where does the sidebar decide which menu items exist, and how does the active
highlight work?**
Expected: `Layout.jsx` `NAV` map keyed by role; `NavLink` with `className={({isActive})=>…}`
+ `end` prop for exact matching. Candidate's sidebar literally has no admin links (UX layer;
real enforcement is backend).

**J5. CandidateHome used to call `api.listCandidates()` and `api.getApplication(1)`. Why was
that wrong and what replaced it?**
Expected: listCandidates is Admin-only (403 noise); getApplication(1) hardcoded another
person's potential id. Replaced by `GET /api/me/applications` — backend derives identity from
JWT (`candidateRepo.findByUserId`), returns candidate-safe views. Ownership computed
server-side, never client-supplied.

**J6. How does the Q&A page know which question to show after a page refresh mid-test?**
Expected: it refetches `/questions`; server `skip(answeredCount)` returns only unanswered
ones — stateless continuation.

**J7. What are the three ways this app changes screens?**
Expected: `<NavLink>/<Link>` (user click), `useNavigate(...)` (programmatic — login, logout,
Q&A completion), `<Navigate>` (declarative redirect — RoleRoute, catch-all `*`).

**J8. Where does Tailwind actually get enabled, and what breaks if it isn't?**
Expected: `@tailwindcss/vite` plugin in `vite.config.js` + `@import "tailwindcss";` in
`index.css`. Without it: classes are inert → "completely broken" unstyled pages (we hit this).

---

## K. Trap Questions

**K1. "The AI rejected the candidate." — correct this sentence.**
Expected: AI never rejects. It produces résumé/answer scores; the application fuses them into
a band; and only `ARCHIVED` (below résumé threshold) is automatic. Interview outcomes are
human. Flags are evidence only (lines 51, 172).

**K2. "Confidence 0.85 means the candidate scored 85%." — correct it.**
Expected: confidence = AI's self-assessed reliability of its own evaluation (0–1). Candidate
performance is `score` fields. 0.85 confidence on a 40/100 score is possible.

**K3. "The candidate's PASS band means they're hired." — correct it.**
Expected: band is a screening outcome pre-interview; the human interview decision
(ACCEPTED/…) is final. Also Admin can override bands (line 224).

**K4. "The LLM computes the combined score with weights." — correct it.**
Expected: LLM produces only the two raw assessments; `QaService.fuse()` does deterministic
weighted math in Java using JD-configured weights.

**K5. "We store flags in a flags table so admins can see history." — correct it.**
Expected: flags are computed on read; only `anticheat_events` are persisted. Historical flags
aren't tracked — if band/status later change, yesterday's flag state isn't recorded (audit log
captures events, not flag states).

**K6. "RoleRoute + hidden sidebar means candidates can't access admin data." — correct it.**
Expected: frontend is UX only. Enforcement = JWT filter + `@PreAuthorize` + ownership checks
+ restricted DTOs server-side (line 203). Test by curl with a candidate JWT.

**K7. "Jackson maps `matched_skills` to `matchedSkills` automatically." — correct it.**
Expected: exact-name matching by default; needs `@JsonProperty("matched_skills")` or a global
SNAKE_CASE strategy. (Mock returns objects directly so it never surfaces — real adapters must
handle it.)

**K8. "The scheduler makes Run unnecessary." — correct it.**
Expected: scheduler is optional (`APP_BATCH_ENABLED=false` default) and duplicates the same
`screenJd` call; manual Run is the demo path; both are per-JD scoring triggers, not a new
pipeline stage.

**K9. "H2 was chosen because the requirement says H2." — correct it.**
Expected: requirement says in-memory SQLite (line 153/235); Java ecosystem equivalent chosen =
H2 in-memory; swap is datasource config only; restart-resets semantics preserved.

**K10. "GET /api/me/applications?candidateId=2 lets admins check candidate 2." — correct it.**
Expected: that endpoint ignores query params entirely; identity comes from the JWT
(`candidateRepo.findByUserId(principal.userId())`). No client-supplied owner id is honored —
that's the security property.

---

## L. Debug Scenarios

**L1. "After I add a column to an entity, the app won't start."**
Expected: `ddl-auto: validate` + stale `target/classes` (old Flyway script) → schema mismatch.
Fix: update `V1__init.sql`, `rm -rf target/classes`, restart. (Happened live with
`resume_gaps`.)

**L2. "All 403s show as 401 in Postman."**
Expected: `/error` dispatch re-enters security chain unauthenticated → entry point 401.
Fix: permit `/error`. Distinguish 401 (unauthenticated) vs 403 (denied).

**L3. "Login with admin/admin123 returns 401 but the user exists."**
Expected: seeded BCrypt hash didn't match the advertised plaintext (was hash of "password").
Fix: generate correct hash via BCryptPasswordEncoder or encode at seed-load (current design).

**L4. "Flags page is empty even though a candidate clearly has low answer confidence."**
Expected: flag uses **résumé** confidence only (gap vs "average confidence" requirement), and
apps in APPLIED/RESUME_SCORED are skipped; answer confidences aren't averaged yet.

**L5. "Candidate2 logs into the candidate portal and sees Aarav's application." — what code
smell caused this historically, and what's the fix?**
Expected: hardcoded `api.getApplication(1)` + frontend-only "ownership". Fix:
`GET /api/me/applications` resolving owner from JWT + `AccessService` checks; verified:
candidate2 now 403 on app 1 and sees own ARCHIVED row.

**L6. "Run twice in a row doubles the applications."**
Expected: it doesn't — `UNIQUE(candidate_id, jd_id)` + find-or-create. If it did duplicate,
the team doesn't understand their own constraint.

**L7. "The screening batch stopped entirely when one résumé's LLM call failed."**
Expected: shouldn't — `ControlledLlmException` is caught per candidate, row marked FAILED,
loop continues; response reports `failed` count.

**L8. "Candidate finished all 6 questions but band is null."**
Expected: fusion runs when `answered >= total`; if questions were added after answers began
(total moved), or answers reference an old JD question set, `qaComplete` may lag. Inspect
`answers` vs `questions` counts. (Edge-case awareness.)

**L9. "npm pages are unstyled after cloning on a new machine."**
Expected: Tailwind plugin/config present but `npm install` not run, or `index.css` import
missing. Build output CSS ~20 KB is the tell (unstyled ≈ 1–2 KB).

**L10. "Interviewer1 says they see no candidates."**
Expected: nothing is assigned to them — assignments come from
`findByInterviewerId(interviewerId)`; also note user ids shifted with the 14-user seed
(interviewer1 = 13). Empty state is correct behavior.

---

## M. Requirements vs Implementation

**M1. Which requirement items are fully implemented, partially, or not at all? Be specific.**
Expected highlights:
- ✅ auth (hard-coded, JWT), JD CRUD, threshold gating, Q&A UX (timer/auto-submit/one-at-a-time),
  rubric scoring, weighted fusion, band to candidate (aggregate+breakdown), assign+date,
  4-way decision + notes, audit, admin-only flags, pluggable client, seed JSON, stub invite,
  band override, batch endpoint + optional scheduler, shortlist sort/filter, drill-down,
  interviewer name to candidate, per-question timings.
- 🟡 Low-confidence flag uses résumé confidence only (requirement: *average* confidence).
- 🟡 "Realtime" UI = refresh/polling; 2–3 s LLM SLA unverified against real provider.
- 🟡 Interview "Score 3" not numeric — human decision instead (doc ambiguity, line 62).
- 🟡 Invitation stub = toast + audit only (as required — no email).
- ❌ Auto-invite push (candidate self-launches from portal), PDF parsing (explicitly out of scope).
- ⚠️ `/detail` candidate-leak fixed by `assertCanViewFullEvidence` — ask them to show it.

**M2. The doc says "2 Admins, 3 Candidates" in one place and 14 users in another. Which did
you implement and why?**
Expected: 14 users / 10 candidates / 3 JDs / 18 questions — dataset section (lines 245–269) is
authoritative; documented in `SmartHire_Simplified_Requirements_glm_5.3.md` §12.

**M3. "Invoked when Admin opens a JD" vs "batch job at intervals" — contradiction. How did you
resolve it?**
Expected: both supported — manual Run ▶ / Run all + optional scheduler behind one flag; same
`screenJd` core.

**M4. Where must AI evidence NEVER appear, and which DTO guarantees it?**
Expected: Candidate-facing endpoints. `ApplicationView` (home) and `MyResultDto` (result)
physically omit score/justification/rubric fields; `/detail` is gated to
Admin+assigned-interviewer by `assertCanViewFullEvidence`. Also `/my-result` restricted to the
owning candidate.

**M5. Which requirement were you unable to verify end-to-end, and how would you verify it?**
Expected honesty check: 2–3 s LLM latency with a real provider (needs real key + timing log);
"realtime" updates (would need polling demo); average-confidence flag (needs implementation
first). Interns who can name their own unverified claims score highest.

---

## Appendix: one-line answers interns must know cold

| Question | Answer |
|---|---|
| Who computes the band? | `QaService.fuse()` — application code, not LLM |
| Who computes flags? | `FlagService` — computed on read, Admin-only |
| Who assigns interviews? | Admin (DrillDown) → `InterviewService.assign` |
| Who sees AI justifications? | Admin + assigned Interviewer only (`/detail`) |
| Who can call `/api/me/applications`? | The CANDIDATE — returns only their own apps |
| What resets the DB? | Backend restart (in-memory H2) — Flyway + JsonSeedLoader replay |
| What retries LLM calls? | `validateOrRetryOnce` — exactly once, then `ControlledLlmException` |
| What tables does Run write? | `applications`, `candidates`, `audit_log` |
| What enforces one-answer-per-question? | `UNIQUE(application_id, question_id)` + skip() sequencing |
| What enforces interviewer isolation? | `findByInterviewerId` query + `assertCanAccess` |

---

*Question bank version `_glm_5.3` · grounded in the as-built code and
`SmartHire_Pipeline_UseCase.md` · use the follow-up probes, not just the headline answers.*
