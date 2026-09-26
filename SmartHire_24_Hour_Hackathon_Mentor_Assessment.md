# SmartHire 24-Hour Hackathon Mentor Assessment

This is a separate mentor playbook for evaluating interns who implemented SmartHire during a 24-hour hackathon. It is not a product README and it is not a second copy of the general viva FAQ. It is designed for short, fair, high-signal conversations where each intern receives a different question track.

The goal is not to punish the use of AI coding tools. The goal is to test whether the intern can:

- Explain the code they submitted.
- Trace data and authorization across browser, API, service, and database.
- Identify where the implementation differs from the requirements.
- Reason about failures, concurrency, security, and testing.
- Make a safe change without blindly generating code.
- Communicate trade-offs appropriate for a 24-hour prototype.

## Mentor Operating Rules

### Recommended format per intern

Use a 25-35 minute session:

1. Two-minute architecture explanation without opening the code.
2. Ten-minute assigned question track.
3. Five-minute live repository trace.
4. Five-minute bug or change scenario.
5. Three-minute engineering-principles discussion.
6. Final score and evidence notes.

For a larger group, assign different tracks and do not ask every intern the same questions. Ask one follow-up whenever an answer sounds memorized.

### The “show me” rule

When an intern makes a claim, ask them to show one of:

- The exact file and method.
- The request path and payload.
- The database table and relevant column.
- The test that proves it.
- The failure behavior.

Do not accept “Spring handles it,” “React does it,” or “the AI generated that” as a complete answer.

### What not to assess

Do not grade interns down for:

- Not memorizing every annotation.
- Using a different but reasonable naming convention.
- Choosing H2 instead of SQLite when the behavior is understood.
- Using an AI assistant, if they can explain, test, and modify the result.
- Not implementing production infrastructure that the hackathon explicitly excluded.

Do grade them on whether they know what is prototype-only and what would be unsafe in production.

## Scoring Model

Score every dimension from 0 to 4.

| Score | Evidence |
|---:|---|
| 0 | Cannot answer or gives a materially false answer |
| 1 | Recognizes terminology but cannot trace implementation |
| 2 | Explains the happy path but misses security/failure details |
| 3 | Explains code, behavior, trade-offs, and at least one test |
| 4 | Explains behavior, code, security, failure modes, concurrency, and improvement path |

Score these dimensions:

| Dimension | Weight |
|---|---:|
| Product and workflow understanding | 10% |
| Personal implementation ownership | 20% |
| Frontend understanding | 15% |
| Backend/API understanding | 15% |
| Data and state modeling | 10% |
| Security and authorization | 15% |
| Testing and engineering judgment | 15% |

Suggested outcomes:

- `85-100`: can independently defend and extend the implementation.
- `70-84`: solid hackathon implementation knowledge; needs normal mentoring.
- `50-69`: understands the demo but has important blind spots.
- `<50`: cannot safely maintain the submitted system without close supervision.

## Repository Facts the Mentor Should Verify

These facts describe the current repository, not only the original use case:

- Frontend: React/Vite single-page app with role-based routes.
- Backend: Spring Boot, Spring Security, JWT, JPA, Flyway, H2 in-memory database.
- Production AI path: Spring AI `ChatClient` through `SpringAiLlmClient`.
- `MockLlmClient` is a plain deterministic test helper, not a Spring bean.
- Two AI operations exist: `resume_match` and `answer_score`.
- The backend seed file contains 3 JDs, 18 questions, 10 candidates, 2 admins, and 2 interviewers.
- The database resets on backend restart because it uses H2 in memory.
- Interview assignment is represented by `interviews.interviewer_id` and `interviews.application_id`.
- Candidate access, interviewer assignment access, and Admin access are different authorization cases.
- Actual `.env` files are ignored. `.env.example` is a placeholder template.
- The current implementation has known prototype gaps; recognizing them is part of the assessment.

## Question Selection Strategy

Give each intern one primary track and select two random questions from a secondary track. Do not reveal the answer key in advance if the document is being used as an assessment artifact.

### Track assignment

| Track | Primary area | Best for testing |
|---|---|---|
| A | End-to-end pipeline | Overall ownership |
| B | React and browser behavior | Frontend contributors |
| C | Spring/API/security | Backend contributors |
| D | Database/state/concurrency | Data and workflow reasoning |
| E | Spring AI and evaluation | AI integration reasoning |
| F | Testing/debugging/operations | Engineering maturity |
| G | General software engineering | Transferable fundamentals |

## Track A: End-to-End Ownership

### A1. Explain a complete interviewer journey

**Prompt:** An Admin assigns application `1` to `interviewer1`. Explain what happens until `interviewer1` sees the row.

**Expected evidence:**

- Admin calls `POST /api/applications/1/assign-interviewer`.
- Payload includes numeric `interviewerId` and an ISO timestamp.
- `@PreAuthorize("hasRole('ADMIN')")` protects the endpoint.
- `InterviewService.assign()` creates or updates an `Interview` row.
- The application status becomes `INTERVIEW_SCHEDULED`.
- An `ASSIGN_INTERVIEWER` audit event is written.
- Interviewer login creates a JWT containing user ID, username, and role.
- `AssignedList` calls `GET /api/interviewer/assignments` in an effect.
- The JWT filter creates the authenticated principal.
- The backend queries `findByInterviewerId(principal.userId())`.
- The response joins interview, application, candidate, and JD data.
- React puts the array into `rows` and maps it to a table.

**Trick follow-up:** Where is the candidate filtered?

**Strong answer:** In the backend query, not in React. Client-side filtering would not be authorization.

### A2. Draw the status journey

**Prompt:** Draw the application states from resume submission to final interview decision and identify where each state is stored.

**Expected evidence:**

```text
APPLIED
  -> RESUME_SCORED
  -> SCREENING or ARCHIVED
  -> QA_IN_PROGRESS
  -> FUSED
  -> INTERVIEW_SCHEDULED
  -> INTERVIEW_DONE
```

The application status is stored on `applications.status`. Interview status and decision are separately stored on `interviews.status` and `interviews.decision`.

**Trick follow-up:** Is `ACCEPTED` an application status?

**Expected answer:** In the current implementation it is an interview decision, not the application status. The application becomes `INTERVIEW_DONE` and next steps describe the decision.

### A3. Explain the “single source of truth” claim

**Expected evidence:**

The persisted application, interview, answer, flag, and audit records form the source of truth. The UI is a projection. AI output is evidence persisted into application/answer fields, not an ephemeral browser-only score.

**Trick follow-up:** What breaks if the browser stores the score and the server stores only status?

**Expected answer:** Evidence becomes untrusted, inconsistent across users, lost on refresh, and impossible to audit reliably.

### A4. Find three requirement/implementation mismatches

Accept any three with repository evidence:

- Requirements mention SQLite; implementation uses H2.
- Requirement says interviewer evidence is on the interviewer screen; current route separation may show decision UI in `Interviewer/Review.jsx` and full evidence in shared `DrillDown.jsx`.
- Requirement mentions configurable Admin thresholds; current implementation may not expose every configuration through UI.
- Seed counts differ between sections of the requirements and actual JSON.
- Requirement says real-time UI; current frontend mainly loads on mount and after actions rather than using a push channel.
- Requirement says invitation action; implementation is a stubbed audit/toast, not email.
- Requirement describes batch scheduling; the default scheduled batch setting is disabled.

**Scoring:** Do not reward vague complaints. Require file, endpoint, or config evidence.

### A5. Explain what happens after a resume scores below threshold

**Expected evidence:**

`ScreeningService.screenJd()` stores resume score/evidence, sets `RESUME_SCORED`, then sets application and candidate state to archived, stores a next-step reason containing the threshold, and includes the candidate in the batch result as archived. It does not enter the Q&A flow.

### A6. What would you demo first in a five-minute presentation?

**Strong answer:** Show one complete path, not every screen: Admin runs JD scoring, candidate completes enough Q&A to show scoring, Admin assigns interviewer, interviewer sees only assignment and records decision, Admin verifies audit. The intern should mention seeded data, credentials, API key, and known limitations before the demo.

## Track B: React and Browser Behavior

### B1. Trace the first render of the interviewer list

**Expected evidence:**

- Route matches `/interviewer`.
- `RoleRoute` reads the role from local storage.
- `Layout` renders the portal shell.
- `AssignedList` starts with `rows=[]`.
- `useEffect` calls `api.myAssignments()` after mount.
- `request()` adds the bearer token.
- Successful response calls `setRows`.
- React rerenders the table.

**Trick follow-up:** What is shown while the request is pending?

**Expected answer:** The current component does not have a dedicated loading state; the empty table/no-assignment state can appear briefly. That is a UX gap worth identifying.

### B2. Why is `useEffect(..., [])` not “run once forever”?

**Expected answer:** It runs after the component mount for that component instance. It runs again if the component is unmounted and mounted again. React Strict Mode development behavior may intentionally exercise effects more than once. It also does not automatically refresh data when another user changes an assignment in another tab.

### B3. How would you prevent stale interviewer assignments?

Possible answers:

- Refetch when returning to the route.
- Add manual refresh.
- Poll on a sensible interval.
- Use server-sent events/WebSocket only if the requirement truly needs realtime.
- Invalidate a query cache after Admin assignment.

**Trick:** Do not accept “put it in global state” as a freshness strategy by itself.

### B4. Why is `localStorage` both convenient and dangerous?

**Expected answer:** It survives refresh and is easy for a small SPA, but JavaScript and injected scripts can read it. A production design would consider secure HTTP-only cookies or short-lived in-memory tokens and explicitly handle CSRF/refresh/revocation.

### B5. Reproduce the Assign 400 bug mentally

**Prompt:** The Admin types `interviewer1` into an input and clicks Assign. Why could the backend receive null?

**Expected evidence:** `Number("interviewer1")` is `NaN`; JSON has no standard NaN value and the request can become `null`; `@NotNull Long interviewerId` fails validation. The fix should validate in the browser but must also validate on the server.

### B6. What is wrong with using array index as a React key?

**Expected answer:** If rows are inserted, removed, or reordered, React may reuse DOM/component state for the wrong item. Use stable database identity such as `interviewId`.

### B7. Explain controlled input behavior in the assignment form

**Expected answer:** `value={assign.interviewerId}` means React owns the displayed value. `onChange` updates the state. The submit handler converts it to a numeric value. The disabled expression prevents missing/non-integer values.

### B8. How would you test React authorization UX?

Expected tests:

- No role redirects to login.
- Candidate navigating to `/interviewer` redirects to candidate portal.
- Stored role alone does not replace backend authorization tests.
- A 401 clears storage.
- A 403 shows an actionable error instead of silently treating it as an empty list.

### B9. Explain a timer race in candidate Q&A

**Expected answer:** The timer can fire while a manual submit is in progress, creating two requests or advancing state twice. Disable submission, use a ref/guard for in-flight state, make the backend idempotent, and test timeout/manual-submit overlap.

### B10. Why is hiding a component not security?

**Expected answer:** A user can call the endpoint directly. Evidence restrictions must exist in backend DTOs and access checks, not only in conditional JSX.

### B11. What does a good error state need?

**Expected answer:** Clear user message, retry path, preserved safe state, no stack trace/secrets, useful correlation/logging on the server, and distinction between network failure, 401, 403, 404, validation failure, and provider failure.

### B12. What frontend change would you make before a real demo?

Accept well-justified answers such as loading states, responsive table behavior, interviewer dropdown, timezone label, disabled/in-flight buttons, explicit empty/error states, or fixing the interviewer evidence route.

## Track C: Spring Boot, API, and Security

### C1. Explain the path of a protected request through Spring Security

**Expected evidence:** HTTP request -> `JwtAuthFilter` -> token parse/verification -> `SecurityContext` authentication -> request authorization and `@PreAuthorize` -> controller -> service -> repository.

### C2. Why are two authorization checks needed for interviewer detail?

One checks role (`INTERVIEWER`). The other checks record-level assignment (`interviews.interviewer_id` equals principal ID). A role alone would let every interviewer access every candidate.

### C3. What would happen if a user edits the role in local storage?

The frontend could render the wrong portal, but the server still uses the signed JWT and method security. If the user also obtains a forged token, signature verification should reject it. Never use local storage role as the backend authority.

### C4. How would you validate Admin assignment correctly?

The service should load `User` by ID, verify it exists, verify `Role.INTERVIEWER`, validate application existence and allowed state, validate scheduled time, then create/update the interview. A `@NotNull` numeric ID is not enough.

### C5. What is wrong with returning JPA entities directly?

It can expose fields unintentionally, trigger lazy-loading issues, couple API shape to persistence, and leak internal data. DTOs provide deliberate projections, especially important for candidate-safe versus full-evidence responses.

### C6. What does `@Transactional` not protect?

It does not roll back OpenAI calls, browser requests, emails, or already-observed external side effects. It also does not automatically solve concurrent duplicate requests or define legal state transitions.

### C7. Should the LLM call be inside a transaction?

Current prototype simplicity may place it in a transactional service path. Production reasoning should question the long transaction around network I/O and propose validate-then-short-persist or a durable async job, with idempotency.

### C8. How should validation errors be returned?

Consistent JSON with status, field errors, and a safe message. The current generic Spring response is enough for a demo but should be improved for a user-facing system. Do not expose stack traces or provider internals.

### C9. What is the difference between `ResponseStatusException` and domain validation?

`ResponseStatusException` is an HTTP-oriented exception used by the current code. Domain validation should be expressed at the service boundary and translated consistently by an exception handler. Avoid scattering HTTP concerns through deep business logic if the application grows.

### C10. Where would you put idempotency for assignment?

At the service/database boundary. Existing lookup by application ID makes assignment an upsert, but a production API may use an idempotency key, version check, or explicit reschedule semantics to avoid duplicate side effects and lost updates.

### C11. Why is stateless JWT authentication a trade-off?

It avoids server session storage, but revocation, rotation, logout, role changes, and token theft are harder. A short expiry plus refresh/revocation strategy is needed in production.

### C12. What should be logged for a failed assignment?

Request correlation ID, actor ID/role, application ID, safe validation reason, and server-side exception context. Never log JWTs, API keys, passwords, or unnecessary candidate PII.

## Track D: Database, State, and Concurrency

### D1. Identify the tables needed to display one interviewer row

**Expected answer:** `interviews`, `applications`, `candidates`, and `job_descriptions`; `users` identifies the interviewer but the current list response derives the candidate/JD display from the other tables.

### D2. Why is assignment not a column on `candidates`?

An application can be tied to a JD and can have an interview assignment. A candidate may apply to multiple jobs or have multiple workflow records. Assignment belongs to the interview/application context, not the person globally.

### D3. What indexes would production need?

At minimum:

- `interviews(interviewer_id, scheduled_at)`
- `interviews(application_id)` and likely unique application assignment if only one active interview is allowed
- `applications(candidate_id, jd_id)` unique index
- `applications(jd_id, status)` for shortlist queries
- `answers(application_id, question_id)` unique index
- audit indexes by entity and created time

### D4. What race exists in duplicate answer submission?

Two requests can both pass the “answer does not exist” read before either insert commits. The unique database constraint is essential; the service check improves the common case but is not enough alone. Handle the constraint violation as a safe duplicate response.

### D5. What race exists in assigning an interviewer twice?

Two Admin requests can overwrite assignment/time in unpredictable order. Decide whether last-write-wins is acceptable; otherwise use optimistic locking/versioning and return a conflict when the record changed.

### D6. What should happen if an application is archived but an Admin assigns an interview?

The service should reject the action unless the product explicitly supports override. Current requirements imply only eligible shortlisted candidates should advance, so a state precondition is appropriate.

### D7. Why are scalar IDs weaker than JPA relationships?

They simplify mapping and avoid some serialization/lazy-loading issues, but JPA does not automatically navigate or enforce referential integrity. Missing foreign-key constraints make invalid IDs possible. The service must validate related records.

### D8. What happens after restart?

H2 memory is lost. Flyway recreates schema and `JsonSeedLoader` reseeds users/JDs/questions/candidates. Runtime applications, answers, interviews, notes, and audit records are gone.

### D9. Why can `next_steps` be a problem as one string?

It overwrites prior workflow information and cannot provide a history of changes. A production design could use a pipeline-event/next-step table while keeping a current summary field for fast display.

### D10. What is wrong with using status strings on `Candidate` but enums on `Application`?

It creates two representations and possible drift. The candidate profile status and application status may disagree. A clearer design defines ownership of each status and avoids duplicating workflow status unless there is a deliberate projection/update policy.

### D11. What transaction isolation would you question?

Concurrent final answers, assignment changes, decision updates, and Admin overrides. Ask whether optimistic locking/version fields are needed and how the UI handles a conflict.

### D12. Design a persistent-data migration plan

Expected points: replace datasource, retain/version Flyway migrations, add foreign keys/indexes, move seed behavior to development profile, preserve secrets through deployment config, add backups and integration tests, and handle existing in-memory data migration as a new deployment concern.

## Track E: Spring AI and Scoring Evaluation

### E1. What is the adapter boundary?

`ScreeningService` and `QaService` depend on `LlmClient`. `SpringAiLlmClient` translates domain inputs to prompts, calls Spring AI, parses/validates records, and raises controlled failures. This avoids putting provider-specific code into workflow services.

### E2. What does switching `OPENAI_MODEL` actually change?

It changes the configured OpenAI chat model. It does not automatically switch from OpenAI to Anthropic or another provider; that needs an appropriate Spring AI starter/configuration and adapter/provider bean.

### E3. Is temperature zero deterministic?

No. It can reduce variation but does not guarantee identical responses. Model versions, provider infrastructure, tokenization, and sampling implementation can still vary.

### E4. What does JSON parsing validate and what does it not?

Parsing checks syntax and mapping to the Java record. It does not guarantee correct score ranges, required semantic meaning, fair justification, rubric correctness, or absence of unexpected properties unless configured. Application validation must supplement parsing.

### E5. Why are score ranges important?

Without range validation, a model could return `score=900`, `confidence=2`, or a negative score and corrupt thresholds, bands, flags, and reports.

### E6. Why should retry be exactly bounded?

To control cost, latency, and outage amplification. The requirements explicitly call for at most one retry for invalid structured output. A retry loop is a bug, not resilience.

### E7. How would you separate invalid output from provider outage?

Classify parsing/validation failures separately from timeout, rate-limit, authentication, and 5xx errors. Apply only the specified correction retry to malformed output; use bounded transport retry/backoff where justified; surface a controlled application failure.

### E8. What if a model returns a markdown code fence?

The adapter may strip a fence defensively, then still parse and validate. Prompts should request JSON only, but prompt instructions are not a substitute for validation.

### E9. What if the answer contains prompt injection text?

Treat candidate content as untrusted data, delimit it, instruct the model to score only the answer, use strict output, and avoid tools. Do not let candidate text redefine system instructions or obtain hidden data.

### E10. What is wrong with sending the whole database to the model?

Privacy, cost, context limits, leakage, and irrelevant evidence. Send only the minimum JD/resume/question/rubric data required for the single operation.

### E11. How would you evaluate whether AI scoring is good?

Create a labeled evaluation set, define rubric agreement metrics, measure false positives/negatives, inspect confidence calibration, compare groups for disparate outcomes, sample justifications for evidence grounding, and track model/prompt versions.

### E12. Why should model and prompt versions be persisted?

Reproducibility and audit. If a candidate is rescored after a model change, an Admin should know which model/prompt generated each score.

### E13. What is a safe fallback when the provider is unavailable?

Do not silently use a lower-quality score or mark a candidate rejected. Record a pending/failed scoring state, explain the error, allow safe retry, and preserve idempotency. A mock fallback should be explicit and never silently mix demo and production results.

### E14. Is the internal MockLLM safe as production fallback?

No. It is deterministic keyword/rubric matching and is not equivalent to a real model. It may be useful in tests or offline demo mode only when clearly labeled.

### E15. What is the difference between confidence and correctness?

Confidence is an additional model-generated signal; it is not proof of correctness. Human review and empirical calibration are needed.

## Track F: Testing, Debugging, and Operations

### F1. A user reports a 400 on Assign. What is your debugging sequence?

Expected sequence:

1. Inspect browser Network request URL, method, payload, and response body.
2. Compare payload to `AssignRequest` validation.
3. Check backend log for binding/validation exception.
4. Reproduce with curl using a known token and payload.
5. Verify numeric interviewer ID exists and has role `INTERVIEWER`.
6. Add frontend validation and backend validation.
7. Add a regression test.

### F2. A candidate sees internal justification. Where do you look?

Inspect candidate API DTOs and access service first, not only JSX. Call the endpoint directly as a candidate. Confirm `/my-result` and `/applications/{id}/detail` are distinct and that backend serialization does not include restricted fields.

### F3. The interviewer list is empty. What possibilities do you investigate?

- No `interviews` row exists.
- Wrong interviewer ID in assignment.
- Token is for another user.
- Backend query filters correctly but seed has no assignments.
- Frontend is pointed at a different backend port/database.
- API returned an error treated incorrectly as empty state.
- CORS or stale local storage issue.

### F4. The build works but startup fails. What do you check?

Configuration shape, duplicate YAML keys, missing API key, incompatible Spring Boot/Spring AI versions, migration errors, port conflicts, profile/environment loading, and bean creation logs.

### F5. How do you test a protected endpoint manually?

Login to receive a token, call the endpoint with `Authorization: Bearer`, test no token, test wrong role, test right role/wrong ownership, then test right role/right ownership. Record status and body for each case.

### F6. What should CI run?

- Backend compile/package.
- Backend unit tests.
- Backend integration/security tests.
- Frontend dependency install and build/lint.
- Secret scan.
- Migration validation.
- No live LLM dependency for normal CI.

### F7. How would you test a provider failure without calling OpenAI?

Mock `ChatClient` or the adapter dependency to throw timeout, 401, 429, malformed response, empty response, and 500-like exceptions. Assert controlled error mapping, retry count, no partial persistence, and useful audit/log behavior.

### F8. What should be monitored in production?

Latency, provider error rate, token/cost usage, malformed output rate, retry rate, scoring completion failures, queue/backlog if asynchronous, authorization failures, database errors, and audit-write failures. Never record raw secrets in metrics or logs.

### F9. How would you roll back a bad prompt change?

Version prompts, deploy a known-good configuration/code version, preserve old score provenance, avoid silently rescoring all candidates, and run the evaluation set before rollout.

### F10. Why is “it works on my laptop” insufficient?

The project depends on Java/Maven/Node versions, environment variables, ports, database behavior, CORS, and model configuration. Reproducible startup, documented setup, automated checks, and a clean repository matter.

## Track G: General Software Engineering Principles

### G1. When should you optimize versus simplify in a 24-hour hackathon?

Prioritize a complete, testable vertical slice and explicit limits. Do not build queues or microservices before proving the core pipeline. Still implement security checks, validation, clear failure behavior, and clean boundaries because those are cheaper to add early than to repair later.

### G2. What makes a good API contract?

Explicit request/response shape, validation, correct HTTP statuses, safe error messages, authorization, idempotency expectations, versioning strategy, and tests. A frontend-only contract is not enough.

### G3. Why are comments sometimes harmful?

Comments that merely restate code add noise and become stale. Comments should explain non-obvious trade-offs, security boundaries, invariants, or requirement decisions.

### G4. What is the difference between correctness and resilience?

Correctness means valid inputs produce the right result and invalid access is denied. Resilience means temporary failures, retries, timeouts, duplicate requests, and partial dependencies are handled without corrupting state. A happy-path demo can be correct but not resilient.

### G5. Why should a service not trust a value from the UI?

The client can be modified or bypassed. Validate type, existence, role, ownership, state, and authorization at the backend boundary.

### G6. What is an invariant in this project?

Examples:

- An interviewer only sees assigned applications.
- A candidate only sees own application-safe data.
- An answer belongs to the application’s JD.
- A question is answered at most once.
- A decision requires an assigned interview.
- Candidate-facing output never includes internal justification.

Ask the intern where each invariant is enforced.

### G7. Why are database constraints valuable if service validation exists?

They protect against races, alternate code paths, scripts, and future bugs. Service checks provide friendly errors; constraints provide a final integrity boundary.

### G8. What does “secure by default” mean here?

Protected endpoints require authentication, sensitive actions require role and record checks, secrets come from environment configuration, candidate DTOs exclude internal fields, and invalid input fails closed.

### G9. What is technical debt versus intentional scope?

Intentional scope is documented simplification, such as H2 memory or stubbed email. Technical debt is a known design that will cause future defects, such as accepting arbitrary interviewer IDs or keeping no score/model version. The intern should distinguish and document both.

### G10. How do you review AI-generated code?

Read it line by line, trace callers and data, run tests, inspect security boundaries, check dependency/version compatibility, question error handling, and make the author explain why each non-trivial piece exists. Generated code is an implementation candidate, not evidence of correctness.

## Live Code-Probe Scenarios

Use one scenario per intern. Give them the repository and five minutes to point to the change, not necessarily implement it.

### Probe 1: Reject invalid interviewer ID

**Scenario:** An Admin posts `{"interviewerId": 9999, ...}`.

**Expected design:** Service loads user, verifies existence and `INTERVIEWER` role, returns a safe 400/404, and writes no assignment. Add controller/service tests.

### Probe 2: Candidate attempts full evidence

**Scenario:** Candidate calls `/api/applications/1/detail` directly.

**Expected design:** backend returns 403; candidate-safe DTO remains available through owner-scoped endpoint.

### Probe 3: Double-click final answer

**Scenario:** Two identical answer requests arrive simultaneously.

**Expected design:** unique constraint/idempotency prevents duplicate rows and duplicate scoring side effects are considered. The frontend disabled state is not enough.

### Probe 4: Interviewer changes application ID in URL

**Scenario:** `interviewer1` changes `/applications/1` to `/applications/2`.

**Expected design:** backend checks assignment to principal and returns 403 for another interviewer’s application.

### Probe 5: OpenAI returns malformed JSON twice

**Scenario:** The first response is prose and the correction response is also prose.

**Expected design:** exactly one retry, then `ControlledLlmException`; no invalid score persisted.

### Probe 6: Provider is down during answer scoring

**Scenario:** Candidate submits an answer and provider times out.

**Expected design:** no successful answer record with a fake score; clear controlled error; retry policy is bounded; candidate can retry safely; transaction/external-call boundary is discussed.

### Probe 7: Admin rescoring after interview assignment

**Scenario:** Admin reruns resume scoring for an already scheduled application.

**Expected design:** define whether this is allowed; prevent destructive status regression or record a new score version; do not silently overwrite audit meaning.

### Probe 8: Timezone mismatch

**Scenario:** Admin in India schedules 10:00 local time and interviewer in another timezone sees the wrong time.

**Expected design:** store an instant/UTC, display with timezone, document `datetime-local` conversion, and test boundary cases.

### Probe 9: API returns 403 but UI says “no assignments”

**Scenario:** A backend authorization failure is rendered as an empty list.

**Expected design:** keep error and empty states distinct; show permission/session error; do not hide security failures.

### Probe 10: Seed data and generated IDs differ

**Scenario:** Code assumes interviewer1 is always ID 13.

**Expected design:** do not hard-code generated IDs in the product; expose an Admin interviewer lookup or query by username/role; document seed order only as a demo detail.

## Rapid-Fire Tricky Questions

Ask these without accepting long rehearsed explanations. Follow with “show me where.”

1. Is a signed JWT encrypted? **No; it is signed, not automatically confidential.**
2. Does HTTPS make localStorage safe from XSS? **No.**
3. Does `@PreAuthorize('hasRole')` verify application ownership? **No.**
4. Does a React redirect protect an API? **No.**
5. Does a database transaction roll back OpenAI? **No.**
6. Is parseable JSON necessarily valid scoring data? **No.**
7. Does temperature zero guarantee reproducibility? **No.**
8. Is an LLM confidence number calibrated probability? **Not without calibration evidence.**
9. Is a client-side timer authoritative? **No; the server must validate state and record the submitted telemetry.**
10. Is a disabled button a concurrency control? **No.**
11. Is an empty interviewer list proof that there are no assignments? **Not until the API response and auth state are checked.**
12. Is `interviewer1` the same as user ID 13? **No; username and generated ID are different fields.**
13. Is H2 in memory persistent? **No.**
14. Does `@Transactional` mean every operation is atomic across services? **No.**
15. Can a unique constraint replace user-friendly validation? **No; use both.**
16. Does a mock producing a score prove the real model integration works? **No.**
17. Does Spring AI automatically make provider switching configuration-only? **Not always; provider starter and adapter differences may remain.**
18. Can a resume be treated as trusted instructions? **No; it is untrusted input.**
19. Does hiding an AI explanation in JSX protect it? **No.**
20. Is audit logging the same as database backup? **No.**
21. Does re-running a batch necessarily preserve prior decisions? **No; status/idempotency policy is required.**
22. Can a candidate’s final score be trusted if one component is missing and treated as zero? **That is a business decision and may be unsafe.**
23. Does a 201 response mean the entire workflow is valid? **No; it only confirms the endpoint accepted the operation.**
24. Can two browser tabs share stale local state? **Yes.**
25. Is a README proof that the feature works? **No; code, tests, and observed behavior matter.**

## Anti-Shallow-Understanding Checks

Use these when an intern gives polished but generic answers.

### Check 1: Change one assumption

Ask: “What if there are two interviews for one application?”

Look for discussion of repository uniqueness, active interview semantics, historical interviews, route selection, and decision ownership.

### Check 2: Remove the UI

Ask: “Assume the frontend is malicious and the caller uses curl. What still protects the data?”

Look for JWT verification, method security, owner/assignment checks, DTO projections, and input validation.

### Check 3: Remove the database

Ask: “Assume the service restarts during a provider call. What is persisted?”

Look for transaction boundaries, pending state, idempotency, and the fact that H2 memory resets.

### Check 4: Remove the model

Ask: “Assume OpenAI is unavailable for 30 minutes. What should candidates see?”

Look for explicit failed/pending behavior rather than fake scores or silent rejection.

### Check 5: Ask for the smallest safe change

Ask: “Add an interviewer dropdown. What is the smallest complete change?”

Look for backend interviewer listing and role validation, not only a hard-coded frontend array.

### Check 6: Ask for a test before code

Ask: “Before modifying assignment authorization, what test would you write?”

Look for an unauthorized interviewer test, candidate full-evidence test, valid Admin assignment test, and invalid user-role test.

## Team-Level Questions

Use these if evaluating the group rather than individuals.

### T1. How did you divide work?

Look for ownership boundaries, shared contracts, integration checkpoints, and a plan for resolving conflicts.

### T2. What did you deliberately not build?

Strong teams mention scope control: real email, production auth, document parsing, queues, agentic flows, and persistent production storage.

### T3. What changed during the last two hours?

Look for explicit risk management, test/demo stabilization, documentation, and avoidance of dangerous last-minute rewrites.

### T4. How did you verify that one role cannot access another role’s data?

A strong answer includes direct API tests, not only clicking through the UI.

### T5. What would you do with one additional day?

Good answers prioritize security/data correctness and tests before cosmetic expansion: interviewer lookup, legal state transitions, persistent storage, provider failure handling, evidence route consistency, and automated authorization tests.

### T6. What did you learn from an integration failure?

Look for concrete diagnosis rather than blame. Examples: invalid JSON field, CORS, port mismatch, missing API key, wrong user ID, stale frontend build, YAML config, or schema constraint.

## Mentor Observation Sheet

Record evidence, not impressions.

| Area | Score 0-4 | Evidence/quote |
|---|---:|---|
| Explains product workflow |  |  |
| Traces frontend request |  |  |
| Traces backend request |  |  |
| Identifies database records |  |  |
| Understands authorization |  |  |
| Understands AI adapter/validation |  |  |
| Handles failure and concurrency |  |  |
| Proposes tests |  |  |
| Identifies implementation gap |  |  |
| Can propose a minimal safe change |  |  |

### Ownership signals

- Can name the file and method without guessing.
- Can explain why a line exists, not only what it does.
- Can distinguish frontend convenience from backend security.
- Notices mismatches between requirements and code.
- Tests a negative path without being prompted.
- Admits uncertainty and describes how they would verify it.
- Can improve the code without breaking the existing contract.

### Concern signals

- Claims “the JWT encrypts everything.”
- Treats role routing as authorization.
- Cannot identify the `interviews` table.
- Cannot explain where `interviewerId` comes from.
- Says the mock and Spring AI are both active without checking bean wiring.
- Assumes all generated IDs are stable.
- Treats AI output as ground truth.
- Has no answer for duplicate requests or provider failure.
- Suggests logging API keys or raw tokens for debugging.
- Says tests are unnecessary because the demo works.

## Minimum Passing Demonstration

An intern should be able to perform or explain this sequence:

1. Start the backend with a configured API key or explain the test-mode alternative.
2. Log in as Admin.
3. Run scoring for a seeded JD.
4. Explain why one candidate is promoted and another archived.
5. Explain where the application and resume evidence are persisted.
6. Assign an interviewer using the actual numeric user ID or propose a safer lookup UI.
7. Log in as the interviewer and explain server-side assignment filtering.
8. Explain the difference between the assignment list and full evidence endpoint.
9. Record a decision and note.
10. Show where the interview, application, note, and audit records changed.
11. Explain what a Candidate can and cannot see.
12. Identify at least two prototype limitations and one practical improvement.

## Final Mentor Guidance

The best evidence of understanding is not a perfect verbal definition. It is the ability to move from requirement to code to data to test:

```text
Requirement
  -> route/API contract
  -> controller authorization
  -> service invariant
  -> repository/table
  -> frontend state/rendering
  -> failure and concurrency behavior
  -> test proving it
```

An intern who can make that chain reliably is ready to learn more. An intern who can only describe the screen or repeat AI-generated architecture terms needs a deeper follow-up, even if the demo looks polished.

## Deep Repository Fact Bank

Use this section when a candidate gives a polished high-level answer and you need to verify that they understand the actual repository rather than a generic architecture.

### R1. Who creates the database tables: JPA, Flyway, or the seed loader?

**Expected answer:** Flyway applies `V1__init.sql`. JPA uses `ddl-auto: validate`, so it checks the entity/schema match and does not create or update tables. `JsonSeedLoader` inserts seed data only. Adding an entity field without a migration should cause startup schema validation failure.

### R2. When does the seed loader run, and how does it avoid duplicate seed data?

**Expected answer:** `JsonSeedLoader` implements `ApplicationRunner` and runs after the Spring context and Flyway migration. It checks `userRepo.count() > 0` and skips when data is already present. With H2 in memory, a restart creates a new empty database and seeds again.

### R3. Why are there two seed JSON locations?

**Expected answer:** The runtime loader reads `backend/src/main/resources/seed/Input_Data.json` through `ClassPathResource`. The root `input_data.json` is a reference/editable copy. Changing only the root file does not change packaged runtime behavior; the copies must be synchronized or the loader should be redesigned to use one source.

### R4. How are string IDs such as `jd-1` linked to generated numeric IDs?

**Expected answer:** The loader saves each JD, captures its generated numeric ID in `jdIdMap`, then uses that mapping when saving questions and candidates. Users are first indexed by username in `usersByName`; candidate rows are saved before the linked user's `candidateRefId` can be updated because the candidate ID does not exist until insertion.

### R5. What happens to the rubric JSON?

**Expected answer:** Jackson binds the untyped seed `rubric` object into generic map/list structures. The loader serializes it back to JSON text because `questions.rubric` is a `TEXT` column. The AI adapter later receives that text as prompt material. It is not a relationally queried rubric structure.

### R6. Where are applications created? Are they seeded?

**Expected answer:** Applications are created lazily by `ScreeningService.screenJd()` using `findByCandidateIdAndJdId(...).orElseGet(...)`. The seed JSON contains candidates but not applications. A fresh boot therefore has no shortlist until Admin runs screening.

### R7. What does “Run screening” actually process?

**Expected answer:** Only candidates whose `applied_jd` matches the selected numeric JD ID. It is not a global resume scan. The batch writes `applications`, mirrors candidate status, and writes an audit event; the JD and question rows are read during that operation.

### R8. What does “Run all” and the scheduler add?

**Expected answer:** `POST /jds/resume-score-all` loops through all JDs and calls the same per-JD service. `SchedulingConfig` can invoke the same flow on an interval, but `APP_BATCH_ENABLED` defaults to false. The scheduler is a trigger, not a new scoring algorithm or pipeline stage.

### R9. How are flags stored?

**Expected answer:** Raw anti-cheat events are persisted in `anticheat_events`, but derived flags such as low confidence, score divergence, and telemetry presence are computed when the Admin reads flags or detail data. They are evidence, not automatic rejection. This means historical derived-flag state is not independently stored.

### R10. What confidence is currently used for low-confidence flags?

**Expected answer:** The current implementation primarily uses resume confidence; the requirement describes average confidence across assessments. Answer confidence is persisted but should be included in a future aggregate confidence calculation if strict requirement compliance is needed.

### R11. Calculate one real fusion example.

**Prompt:** Resume score is `75`, six answer scores are `4, 3, 5, 4, 3, 4`, JD threshold is `70`, and weights are resume `0.6` and Q&A `0.4`. What is the band?

**Expected answer:** Average answer score is `23/6 = 3.833`; normalized Q&A score is about `76.67`; combined score is `0.6*75 + 0.4*76.67 = 75.67`; the result is `PASS`. `QaService.fuse()` computes this in Java. The LLM does not calculate the combined band.

### R12. Where is the HOLD boundary defined?

**Expected answer:** `QaService.determineBand()` uses `combined >= threshold` for PASS and `combined >= threshold * 0.8` for HOLD. The use case requires PASS/HOLD/REJECT but does not specify this exact 80% boundary, so it is an implementation decision that should be documented/configured.

### R13. What happens if the resume score is missing during fusion?

**Expected answer:** `resume0To100()` currently treats null as zero. That is a prototype shortcut and conflates “not scored” with “scored zero.” A production implementation should likely reject fusion or use an explicit pending state until required inputs exist.

### R14. What happens if the question bank changes during a candidate attempt?

**Expected answer:** The service calculates answered and total counts from the current database. Adding/removing questions can change when fusion fires or leave a candidate unable to complete the expected set. A production design should version the question set per application or snapshot question IDs at screening start.

### R15. Why might a 403 appear as 401 in a poorly configured Spring app?

**Expected answer:** An exception can dispatch to `/error`, which may pass through the security chain without the original authentication context and trigger the unauthenticated entry point. `/error` should be permitted and exception handling should preserve the distinction between unauthenticated 401 and authenticated-but-forbidden 403. Verify the current behavior rather than assuming status codes.

### R16. Is `timeTakenSeconds` trustworthy?

**Expected answer:** No. It is client-supplied telemetry. It is useful for evidence and display but can be falsified. The server should enforce workflow ownership and question uniqueness, while treating timing and browser anti-cheat signals as advisory.

### R17. What was historically wrong with a candidate endpoint that accepted a candidate ID from the browser?

**Expected answer:** A caller could substitute another candidate's ID. The corrected pattern derives candidate identity from the JWT and queries `candidateRepo.findByUserId(principal.userId())`. Ownership must be server-derived, not supplied by the browser.

### R18. Why is an interviewer list empty even after successful login?

**Expected answer:** Login proves identity, not assignment. The list query only returns rows where `interviews.interviewer_id` matches the JWT user ID. There may be no assignment, the Admin may have used the wrong numeric ID, the frontend may target another backend port, or the UI may be confusing an API error with an empty result.

### R19. What does the current assignment upsert not handle completely?

**Expected answer:** Reassigning an application updates the existing interview row. Although `InterviewStatus` contains `RESCHEDULED`, `InterviewService.assign()` currently sets `SCHEDULED` every time. The candidate should identify whether this is acceptable scope or a status-transition gap.

### R20. What does the current mock reveal about testing?

**Expected answer:** The mock is deterministic keyword/rubric matching and can produce false positives, false negatives, and overconfident scores. It is useful for unit tests and offline deterministic behavior, but it does not prove the Spring AI/OpenAI integration or real model quality.

## Source Files for Mentor Verification

- Requirements: [`SmartHire_Pipeline_UseCase.md`](SmartHire_Pipeline_UseCase.md)
- Canonical mentor assessment: [`SmartHire_24_Hour_Hackathon_Mentor_Assessment.md`](SmartHire_24_Hour_Hackathon_Mentor_Assessment.md)
- Frontend routes: [`frontend/src/App.jsx`](frontend/src/App.jsx)
- Frontend API client: [`frontend/src/services/api.js`](frontend/src/services/api.js)
- Interviewer list: [`frontend/src/pages/Interviewer/AssignedList.jsx`](frontend/src/pages/Interviewer/AssignedList.jsx)
- Interviewer review: [`frontend/src/pages/Interviewer/Review.jsx`](frontend/src/pages/Interviewer/Review.jsx)
- Admin drill-down: [`frontend/src/pages/Admin/DrillDown.jsx`](frontend/src/pages/Admin/DrillDown.jsx)
- Login service: [`backend/src/main/java/com/smarthire/service/AuthService.java`](backend/src/main/java/com/smarthire/service/AuthService.java)
- JWT filter: [`backend/src/main/java/com/smarthire/security/JwtAuthFilter.java`](backend/src/main/java/com/smarthire/security/JwtAuthFilter.java)
- Interview controller: [`backend/src/main/java/com/smarthire/api/InterviewerController.java`](backend/src/main/java/com/smarthire/api/InterviewerController.java)
- Application controller: [`backend/src/main/java/com/smarthire/api/ApplicationController.java`](backend/src/main/java/com/smarthire/api/ApplicationController.java)
- Interview service: [`backend/src/main/java/com/smarthire/service/InterviewService.java`](backend/src/main/java/com/smarthire/service/InterviewService.java)
- Access service: [`backend/src/main/java/com/smarthire/service/AccessService.java`](backend/src/main/java/com/smarthire/service/AccessService.java)
- Screening service: [`backend/src/main/java/com/smarthire/service/ScreeningService.java`](backend/src/main/java/com/smarthire/service/ScreeningService.java)
- Q&A service: [`backend/src/main/java/com/smarthire/service/QaService.java`](backend/src/main/java/com/smarthire/service/QaService.java)
- Spring AI adapter: [`backend/src/main/java/com/smarthire/ai/SpringAiLlmClient.java`](backend/src/main/java/com/smarthire/ai/SpringAiLlmClient.java)
- Database schema: [`backend/src/main/resources/db/migration/V1__init.sql`](backend/src/main/resources/db/migration/V1__init.sql)
- Seed loader: [`backend/src/main/java/com/smarthire/config/JsonSeedLoader.java`](backend/src/main/java/com/smarthire/config/JsonSeedLoader.java)
