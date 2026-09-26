# SmartHire Intern FAQ and Evaluation Guide

This document is an interviewer-facing assessment guide for the SmartHire hackathon project. It is designed to test whether an intern understands the product, architecture, implementation, security boundaries, data flow, AI integration, and trade-offs behind the application.

It is deliberately more demanding than a demo checklist. An intern may have used an AI coding assistant or agentic coding workflow, but they should still be able to explain the decisions, trace a request through the system, identify implementation gaps, and safely change the code.

## How to Use This Guide

Ask questions progressively:

1. Start with the product and workflow questions.
2. Ask the candidate to trace one real user journey end to end.
3. Probe frontend and backend implementation details.
4. Use the tricky questions to distinguish understanding from memorized terminology.
5. Give one change request and ask where they would modify the code.
6. Ask them to identify at least one gap between the use case and the implementation.

Do not require an intern to know every Spring or React API by memory. Do require them to understand the responsibilities, data ownership, security checks, failure modes, and consequences of their choices.

## Suggested Scoring

Score each area from 0 to 4:

| Score | Meaning |
|---:|---|
| 0 | Cannot explain or gives an unrelated answer |
| 1 | Uses correct words but does not understand the flow |
| 2 | Understands the happy path but misses important edge cases |
| 3 | Explains the implementation and relevant trade-offs |
| 4 | Explains implementation, security, failure modes, tests, and a sensible improvement |

Suggested interpretation:

- `80%+`: strong understanding; can defend and extend the implementation.
- `60%-79%`: understands the main system; needs guidance on details.
- `40%-59%`: partial understanding; likely relied heavily on generated code.
- `<40%`: cannot reliably maintain or explain the project.

## Quick Project Facts

The candidate should know these facts before deep questioning:

- The project is a single monolithic Spring Boot backend and React/Vite frontend.
- The backend uses Java, Spring Boot, Spring Security, JWT, Spring Data JPA, Flyway, and H2 in-memory storage.
- The frontend is a role-aware React single-page application.
- There are three roles: Admin, Candidate, and Interviewer.
- The pipeline is JD -> resume score -> screening -> Q&A -> combined band -> interview -> human decision.
- There are two AI operations: `resume_match` and `answer_score`.
- Spring AI `ChatClient` is used by the production adapter.
- `MockLlmClient` is not a Spring bean and is only a deterministic test helper.
- Candidate-facing views hide internal AI justification and anti-cheat evidence.
- Interviewer assignment is enforced by the backend using `interviews.interviewer_id`.
- The database is H2 in memory and resets on backend restart.
- Actual `.env` files are ignored; `.env.example` contains placeholders only.

## Section 1: Product and Requirements

### Q1. What business problem does SmartHire solve?

**Expected answer:**

It unifies resume screening, conceptual screening, interview coordination, decisions, evidence, and audit history. It reduces manual resume filtering, gives candidates a consistent Q&A assessment, removes interviewer assignment from email threads, and provides a single live pipeline status.

**Follow-ups:**

- What is the “single source of truth” in this system?
- Which users need which evidence?
- Why should the candidate not see all AI evidence?

**Strong answer mentions:** application status, persisted evidence, audit events, role-based access, human decision-making.

### Q2. Describe the complete pipeline.

**Expected answer:**

```text
Admin creates/selects JD
  -> candidate resumes are scored
  -> candidates above threshold enter SCREENING
  -> candidates below threshold are ARCHIVED
  -> candidate answers timed Q&A questions
  -> each answer is AI scored against a rubric
  -> Q&A score and resume score are weighted
  -> application receives PASS/HOLD/REJECT band
  -> Admin assigns interviewer and time
  -> interviewer reviews evidence
  -> interviewer records Accepted/Rejected/On-Hold/No-Show
  -> notes and transitions are audited
```

### Q3. What is the difference between an application status and a band?

**Expected answer:**

The status represents the workflow stage, such as `SCREENING`, `QA_IN_PROGRESS`, `FUSED`, `INTERVIEW_SCHEDULED`, or `INTERVIEW_DONE`. The band is the score-based outcome `PASS`, `HOLD`, or `REJECT`. They are related but not interchangeable.

**Trick:** An interviewer decision is stored on the `interviews` record as `decision`; the application status becomes `INTERVIEW_DONE`. The decision is not simply copied into the application status.

### Q4. Which decisions can an interviewer record?

**Expected answer:**

`ACCEPTED`, `REJECTED`, `ON_HOLD`, and `NO_SHOW`.

The interview itself transitions from `SCHEDULED` to `COMPLETED`, and the application transitions to `INTERVIEW_DONE`.

### Q5. Which requirements are deliberately out of scope?

**Expected answer:**

- Production OAuth/SSO or an identity provider
- Real email or SMS
- PDF/DOCX parsing
- Agentic or multi-turn AI flows
- Kafka, external queues, microservices, or S3
- Automatic rejection from anti-cheat signals
- Production-grade persistent deployment

### Q6. What does “AI scores; humans decide” mean technically?

**Expected answer:**

AI produces evidence and recommendations: resume score, matched skills, gaps, answer score, confidence, rubric hits, and a combined band. The Admin may override the band, and the interviewer makes the interview decision. Low confidence and score disagreement produce evidence flags, not automatic rejection.

### Q7. What is the difference between a requirement and an implementation detail?

**Expected answer:**

A requirement describes behavior or an outcome, such as “an interviewer sees only assigned candidates.” An implementation detail describes how this repository achieves it, such as a Spring Data method `findByInterviewerId` and a JWT principal. A candidate should be able to explain both and identify if the implementation does not fully satisfy the requirement.

## Section 2: End-to-End Request Tracing

### Q8. Trace `interviewer1` logging in from the browser to the database.

**Expected answer:**

1. `Login.jsx` holds username and password in React state.
2. Submit calls `api.login()`.
3. `api.js` sends `POST /api/login`.
4. `AuthController` delegates to `AuthService`.
5. `AuthService` finds the user and verifies the BCrypt password.
6. `JwtService` creates a signed JWT containing user ID, username, and role.
7. The frontend stores token, role, and username in `localStorage`.
8. The frontend navigates to `/interviewer`.
9. `RoleRoute` checks the saved role.
10. `AssignedList` runs a `useEffect` and calls `GET /api/interviewer/assignments`.
11. `api.js` adds `Authorization: Bearer <token>`.
12. `JwtAuthFilter` validates the token and creates a Spring Security principal.
13. `InterviewerController` extracts the principal user ID.
14. `InterviewService` calls `findByInterviewerId(userId)`.
15. The controller enriches each interview with application, candidate, and JD data.
16. React stores the response in `rows` and renders the table.

### Q9. Does React decide which candidates belong to an interviewer?

**Expected answer:**

No. React only renders the response. The backend filters using the authenticated user's ID. This is critical because client-side filtering alone is not an authorization boundary.

### Q10. What happens if the user manually changes the URL to another application?

**Expected answer:**

The backend must still enforce access. `AccessService.assertCanViewFullEvidence()` checks that an interviewer has an `interviews` row for the application whose `interviewer_id` equals the authenticated user's ID. A manual URL change must result in `403`, not data exposure.

### Q11. What happens when the frontend receives HTTP 401?

**Expected answer:**

The shared `request()` function clears the saved session and redirects to `/login`, except for the login request itself. A `403` is different: it means the user is authenticated but lacks permission.

### Q12. What is the difference between `401` and `403` here?

**Expected answer:**

- `401 Unauthorized`: no valid authentication, missing token, expired token, or invalid token.
- `403 Forbidden`: valid identity but the role or ownership/assignment check denies access.

### Q13. What happens when `interviewer1` has no assignment?

**Expected answer:**

The API returns an empty list and the frontend renders “No interviews assigned yet.” The seed loader creates users, JDs, questions, and candidates, but it does not create interview assignments. An Admin must first create an `interviews` row.

## Section 3: React and Frontend Understanding

### Q14. What is React state in `AssignedList`?

**Expected answer:**

```jsx
const [rows, setRows] = useState([]);
```

`rows` is the current assignment list. `setRows` schedules a rerender with the API response. React rerenders the table by mapping over `rows`.

### Q15. Why does `AssignedList` use `useEffect(..., [])`?

**Expected answer:**

It performs the initial API request after the component mounts. The empty dependency array means the effect is not rerun for every render. An intern should also mention that in development React Strict Mode may expose effect problems by mounting effects more than once depending on the React version/configuration.

### Q16. What is the purpose of `RoleRoute`?

**Expected answer:**

It protects frontend navigation by checking the saved role and redirecting unauthenticated or wrong-role users. It is not sufficient security by itself; backend authorization must still protect every API endpoint.

### Q17. Where is the token stored, and what is the security concern?

**Expected answer:**

The demo stores the JWT in `localStorage`. Any successful XSS can read it. A production design would consider an HTTP-only secure cookie or in-memory access token strategy, plus CSRF protection where appropriate. This repository intentionally uses simple demo authentication.

### Q18. Why does `api.js` add the Authorization header centrally?

**Expected answer:**

It avoids duplicating token logic in every component and gives one place to handle 401 responses, session clearing, JSON parsing, and common errors.

### Q19. Why is `key={r.interviewId}` used in the assignment table?

**Expected answer:**

React uses keys to identify stable list items between renders. The interview ID is a database identity and is more appropriate than an array index.

### Q20. What is the difference between controlled and uncontrolled inputs in this project?

**Expected answer:**

The login and assignment inputs are controlled: their `value` comes from React state and `onChange` updates it. This lets the UI validate and submit the current state explicitly.

### Q21. Why did the Assign button previously return 400 when someone entered `interviewer1`?

**Expected answer:**

The backend contract expects a numeric `Long interviewerId`. The frontend called `Number("interviewer1")`, which produced `NaN`; JSON serialization sent it as `null`. Spring validation rejected the `@NotNull` field with HTTP 400. The field is now numeric and invalid values disable the button.

### Q22. What is the difference between `useState`, `useEffect`, `useRef`, and `useCallback` in the Q&A screen?

**Expected answer:**

- `useState`: answer text, current timer/display state, loading and error state.
- `useEffect`: fetch questions, start/cleanup timer, subscribe to browser events, submit on timeout when needed.
- `useRef`: hold mutable timer or telemetry values without rerendering on every change.
- `useCallback`: stabilize a function used by an effect or dependency-sensitive callback.

The answer should not claim that `useCallback` automatically makes every React component faster.

### Q23. What happens if a candidate double-clicks Submit?

**Expected answer:**

The frontend should disable submission while busy, but the backend must remain authoritative. The answer table has a unique `(application_id, question_id)` constraint and the service checks for an existing answer, preventing duplicate scoring/persistence. A robust implementation should also consider concurrent requests and transaction isolation.

### Q24. What is a stale closure in the timer code?

**Expected answer:**

A timer callback may capture an old state value. Using functional state updates, refs, or carefully scoped effect dependencies prevents the callback from submitting an old answer or resetting the wrong question. The intern should be able to inspect the timer code rather than only define the term.

### Q25. Why should the frontend not be trusted for anti-cheat data?

**Expected answer:**

Browser telemetry can be bypassed or manipulated. It is useful as evidence for human review, but it must not be treated as proof or used as an automatic rejection rule. The server should validate application ownership and accept the telemetry as advisory evidence.

## Section 4: Backend and Spring Understanding

### Q26. What are the responsibilities of Controller, Service, Repository, and Entity layers?

**Expected answer:**

- Controller: HTTP route, request validation, authorization annotation, response DTO.
- Service: business workflow, state transitions, transaction boundaries, authorization rules that depend on data.
- Repository: persistence queries.
- Entity: database mapping and persisted state.

An intern should not put the entire scoring workflow or authorization logic into a controller.

### Q27. Why are `@PreAuthorize` annotations not enough for interviewer security?

**Expected answer:**

`@PreAuthorize("hasRole('INTERVIEWER')")` proves only the role. It does not prove that the interviewer owns a particular application. `AccessService` and `InterviewService.assertCanAccess()` perform the record-level assignment check.

### Q28. How does the JWT filter work?

**Expected answer:**

`JwtAuthFilter` reads the `Authorization` header, parses and verifies the token using `JwtService`, creates an `Authentication` with `ROLE_<role>`, and puts it in the Spring Security context before the controller runs. Invalid tokens are treated as unauthenticated and protected endpoints return 401.

### Q29. Why is the application stateless?

**Expected answer:**

`SessionCreationPolicy.STATELESS` means the server does not maintain a login session. Each request carries its JWT. This is simple for the demo and horizontally scalable, but token revocation and refresh require additional design.

### Q30. What does `@Transactional` protect in resume screening?

**Expected answer:**

It groups database writes so scoring evidence and status updates are committed consistently. The code deliberately saves the score fields before applying the promoted or archived status. A candidate should also recognize that an external LLM call is not rolled back by a database transaction.

### Q31. Is calling an external LLM inside a database transaction always ideal?

**Expected answer:**

No. It can hold a transaction open while waiting on network I/O, increasing lock time and reducing throughput. This hackathon keeps the flow simple. A production design might call the LLM outside the transaction, validate the result, then use a short transaction for persistence, with idempotency and retry handling.

### Q32. What does the seed loader do?

**Expected answer:**

`JsonSeedLoader` runs after Flyway creates the schema. If the users table is empty, it reads the JSON seed file, creates users with BCrypt passwords, maps string JD IDs to generated numeric IDs, saves JDs/questions/candidates, and links candidate users to candidate records. Because H2 is in memory, startup repeats the seed process after every restart.

### Q33. Why are the JSON JD IDs not the same as database IDs?

**Expected answer:**

The seed file uses stable string IDs such as `jd-1`, while JPA entities use generated numeric IDs. `JsonSeedLoader` maintains a `Map<String, Long>` from seed ID to generated database ID and uses it for questions and candidates.

### Q34. What is a DTO and why does this project use DTOs?

**Expected answer:**

A DTO is an API-facing data shape. DTOs prevent exposing JPA entities directly, allow candidate-safe versus internal evidence responses, and make the API contract explicit. For example, `DetailDto` contains internal evidence while candidate result DTOs omit justification and confidence.

### Q35. Why is `ApplicationController` returning a different shape for `/detail` and `/my-result`?

**Expected answer:**

The two endpoints have different visibility rules. Admins and assigned interviewers may receive resume evidence, AI confidence, rubric hits, flags, and answers. Candidates receive only aggregate/band and limited timing/score information.

## Section 5: Database and Data Model Traps

### Q36. What table actually represents interviewer assignment?

**Expected answer:**

The `interviews` table. The key fields are:

```text
application_id
interviewer_id
scheduled_at
status
decision
```

Assignment is not stored on the candidate row.

### Q37. How does an assignment produce the candidate name shown in the UI?

**Expected answer:**

The backend starts with an `Interview`, follows `application_id` to `Application`, follows `candidate_id` to `Candidate`, and follows `jd_id` to `JobDescription`. The controller builds `AssignmentView` from those records.

### Q38. What database constraint prevents duplicate applications?

**Expected answer:**

`applications` has a unique constraint on `(candidate_id, jd_id)`.

### Q39. What constraint prevents answering the same question twice?

**Expected answer:**

`answers` has a unique constraint on `(application_id, question_id)`, and the service also checks for an existing answer before scoring.

### Q40. What is an embedded collection in `Interview`?

**Expected answer:**

`Interview.Note` is an `@Embeddable`, and `List<Note>` is persisted through `@ElementCollection` into the `notes` table. Notes do not have their own entity ID in this model.

### Q41. What happens to all assignments after an application restart?

**Expected answer:**

They disappear because H2 uses an in-memory JDBC URL. Seed data is recreated, but applications created by screening, answers, interviews, notes, and audit history from the previous process are lost.

### Q42. What is a potential data consistency problem in manually storing foreign-key IDs?

**Expected answer:**

The entities use scalar IDs instead of JPA relationships. This is simple, but the database schema does not visibly declare foreign-key constraints in the migration. The application must enforce existence and access checks itself, and orphaned IDs are possible if data changes unexpectedly.

### Q43. What does `findByInterviewerId` mean in Spring Data?

**Expected answer:**

It is a derived query method. Spring Data interprets the method name and generates a query filtering the `interviewer_id` property. It is not a frontend filter.

## Section 6: AI and Spring AI

### Q44. What exactly did replacing MockLLM with Spring AI change?

**Expected answer:**

The production code now injects `ChatClient.Builder`, builds a Spring AI `ChatClient`, sends the two scoring prompts through the configured model, and maps the response into the existing `LlmClient` records. `ScreeningService` and `QaService` still depend on the internal `LlmClient` interface, so their business flow did not need provider-specific code.

### Q45. Is the application provider-independent now?

**Expected answer:**

Partially. The pipeline is provider-independent through `LlmClient`, but the current concrete adapter and Maven starter are OpenAI-specific. Switching the model name is configuration-only. Switching to another Spring AI provider requires another starter and adapter/configuration, but not changes to screening or Q&A services.

### Q46. Why not inject `OpenAiChatModel` directly into `ScreeningService`?

**Expected answer:**

That would couple business logic to one vendor/provider and make testing/provider switching harder. The adapter boundary keeps prompt construction, provider calls, parsing, and validation separate from workflow state transitions.

### Q47. What are the two prompts, and why only two?

**Expected answer:**

- `resume_match`: JD text plus resume; returns score, matched skills, gaps, summary, confidence.
- `answer_score`: question, reference answer, rubric, and answer; returns score, justification, confidence, rubric hits.

The use case explicitly limits the system to two single-turn structured prompts and excludes agents, tools, and multi-turn memory.

### Q48. Does Spring AI automatically guarantee the application’s JSON contract?

**Expected answer:**

No. Spring AI transports the model request/response and provides abstractions, but the application still needs structured-output configuration and application-level parsing/validation. `SpringAiLlmClient` parses the response into records and validates ranges and required fields.

### Q49. Why is `temperature: 0.0` used?

**Expected answer:**

The task is scoring, not creative generation. Lower temperature makes responses more focused and can improve repeatability, but it does not guarantee deterministic output. Model version, provider behavior, prompts, and external system conditions can still affect results.

### Q50. What does the invalid-JSON retry guardrail do?

**Expected answer:**

The adapter makes the first call, strips an optional markdown fence, parses the JSON, and validates the result. If parsing or validation fails, it makes exactly one correction request. If the second response fails, it throws `ControlledLlmException`; it does not loop indefinitely.

### Q51. What counts as invalid even if the JSON parses?

**Expected answer:**

Examples:

- Resume score outside `0..100`
- Answer score outside `0..5`
- Confidence outside `0..1`
- Missing matched-skills/gaps/rubric-hit arrays
- Missing summary or justification
- Wrong field names that map to null values

Parsing is not the same as semantic validation.

### Q52. Why is retrying every exception dangerous?

**Expected answer:**

A malformed response and a provider outage are different failures. Blind retries can duplicate billable requests, increase latency, and worsen an outage. The use case requires at most one retry for invalid structured output. Production code should classify transient transport failures separately and use bounded, observable retry policies.

### Q53. What happens if the API key is missing?

**Expected answer:**

Spring AI OpenAI auto-configuration fails during application startup because an OpenAI API key is required. This is intentional now that the production path is real LLM-backed. The mock is not the default fallback.

### Q54. How would you test AI scoring without spending money or requiring a network?

**Expected answer:**

Keep `MockLlmClient` as a plain test helper or inject a test fake implementing `LlmClient`. Unit-test `ScreeningService` and `QaService` against that interface. Separately test `SpringAiLlmClient` with a mocked `ChatClient` or a stub HTTP provider. Do not make unit tests depend on a real API key.

### Q55. How would you prevent prompt injection from a resume?

**Expected answer:**

Treat the resume as untrusted data, clearly delimit it, instruct the model to evaluate only resume evidence, require a strict schema, validate output, and never allow resume text to become an instruction with tool access. Since this application has no tools, the main risks are scoring manipulation and data leakage rather than tool execution.

### Q56. What data should not be logged?

**Expected answer:**

Do not log API keys, JWTs, raw resumes unnecessarily, candidate answers, full prompts, or provider headers in normal production logs. If debugging requires prompt traces, redact personal data and protect the logs.

## Section 7: Scoring and Status Traps

### Q57. What is the resume score range?

**Expected answer:**

`0` to `100`.

### Q58. What is the answer score range?

**Expected answer:**

`0` to `5` per question.

### Q59. How is Q&A normalized?

**Expected answer:**

The average answer score is divided by `5` and multiplied by `100`.

### Q60. How is the combined score calculated?

**Expected answer:**

```text
combined = resumeWeight * resumeScore
         + qaWeight * normalizedQaScore
```

The weights come from the selected JD.

### Q61. What happens if the resume score is missing during fusion?

**Expected answer:**

The current implementation treats a missing resume score as `0` through `resume0To100`. An intern should question whether that is the desired production behavior, because “not scored” and “scored zero” are semantically different. A better design may reject fusion until required inputs exist.

### Q62. What happens if there are no answers?

**Expected answer:**

`fuse()` returns without changing the application if the answer list is empty. In normal flow fusion occurs only after all questions are answered.

### Q63. Why can re-running resume scoring be dangerous?

**Expected answer:**

It may overwrite scores and statuses, repeat billable LLM calls, and potentially interact badly with candidates who already progressed to Q&A or interview stages. A production implementation needs idempotency/versioning rules and a clear policy for rescoring in-progress applications.

### Q64. Is the resume threshold the same as the final PASS threshold?

**Expected answer:**

The same JD has a configured threshold used in the current implementation for both promotion and the PASS boundary, but the product conceptually has separate stages. A strong candidate should notice that separate resume-promotion and final-pass thresholds may be preferable.

### Q65. What is a status-machine bug you would look for?

**Expected answer:**

The service methods do not appear to centralize all legal transitions in one state machine. A client or endpoint could attempt actions in an invalid stage unless each service validates it. Examples include answering after interview assignment, assigning archived applications, or recording decisions on an application that has no valid interview.

## Section 8: Authentication and Authorization Traps

### Q66. Is the JWT stored securely in this demo?

**Expected answer:**

It is stored in `localStorage`, which is vulnerable to token theft through XSS. This is acceptable only as a demo trade-off and is documented as non-production authentication.

### Q67. Does the backend query the database again to verify every JWT claim?

**Expected answer:**

The filter verifies the token signature and expiry and uses claims for the principal. This is stateless and efficient, but role/user changes may not take effect until token expiry unless token revocation or a database lookup is added.

### Q68. Can an Admin assign any numeric interviewer ID?

**Expected answer:**

The current request validates that the ID is non-null, but the assignment service should also verify that the ID exists and belongs to a user with role `INTERVIEWER`. If it does not, the database can contain a logically invalid assignment. This is an important improvement to propose.

### Q69. Why is checking role in React not enough?

**Expected answer:**

The browser is controlled by the user. A user can modify local storage or call APIs directly. Backend `@PreAuthorize` and record-level access checks are mandatory.

### Q70. What is the difference between role authorization and ownership authorization?

**Expected answer:**

Role authorization asks “is this user an Admin, Candidate, or Interviewer?” Ownership/assignment authorization asks “is this candidate the owner of this application?” or “is this interviewer assigned to this application?” Both are needed.

## Section 9: Testing Questions

### Q71. What tests should exist for login?

**Expected answer:**

- Valid credentials return token and correct role.
- Invalid username returns 401.
- Invalid password returns 401.
- Protected endpoint without token returns 401.
- Wrong role receives 403.
- Expired or invalid JWT is rejected.

### Q72. What tests should exist for assignment isolation?

**Expected answer:**

- Admin can assign an existing interviewer.
- `interviewer1` sees their assignment.
- `interviewer2` does not see `interviewer1`'s assignment.
- `interviewer1` cannot access `interviewer2`'s detail endpoint by changing the URL.
- Candidate cannot access full evidence.
- Invalid interviewer ID is rejected.
- Assignment writes application status and audit event.

### Q73. What tests should exist for resume screening?

**Expected answer:**

- Above-threshold candidate becomes `SCREENING`.
- Below-threshold candidate becomes `ARCHIVED` with reason.
- Score evidence is persisted.
- LLM failure does not silently promote a candidate.
- Batch result counts scored/promoted/archived/failed correctly.
- Re-running screening behavior is explicitly defined.

### Q74. What tests should exist for Q&A?

**Expected answer:**

- Only the application owner can load questions.
- A question must belong to the application JD.
- Duplicate answer is rejected.
- Timeout flag and duration are persisted.
- One answer is scored once.
- Fusion runs after the final question.
- Candidate result excludes justification/confidence/raw evidence.

### Q75. How would you test `SpringAiLlmClient` without a real OpenAI call?

**Expected answer:**

Mock or fake `ChatClient` behavior and return:

1. Valid JSON on the first call.
2. Invalid JSON then valid JSON, verifying exactly two calls.
3. Invalid JSON twice, verifying `ControlledLlmException`.
4. Parseable but invalid ranges, verifying validation and retry.
5. Markdown-fenced JSON, verifying fence stripping.
6. Empty response and provider exception, verifying controlled failure.

### Q76. What is the difference between a unit test and an integration test here?

**Expected answer:**

A unit test isolates a service or adapter with fake repositories/LLM client. An integration test starts enough Spring context to verify controllers, security, JPA, Flyway, and JSON mapping. A real provider call is an external integration test and should be separately tagged, controlled, and never required for normal CI.

## Section 10: Tricky Implementation Questions

### Q77. The README says Spring Boot 3.4.5, but an older document says 3.2. Is that a problem?

**Expected answer:**

The implementation was upgraded to use a compatible Spring AI line. Documentation must describe the current code, not only the original requirement. The candidate should verify `pom.xml`, not rely on an old document.

### Q78. The requirements mention SQLite, but the code uses H2. What should the candidate say?

**Expected answer:**

The use case permits in-memory SQLite or an equivalent in-memory store. This implementation uses H2 with Flyway and JPA. The important behavior is startup seeding and refresh-duration persistence, not the exact embedded database brand.

### Q79. The requirements mention 2 Admins, 3 Candidates, and 2 Interviewers, but the seed has 10 candidates. Is that automatically wrong?

**Expected answer:**

No. The requirement text contains inconsistent counts in different places. The current seed has 2 Admins, 10 Candidates, and 2 Interviewers, which supports the requested demo edge cases. The candidate should identify the inconsistency and explain which source of truth the implementation follows.

### Q80. Why does the Admin assignment screen ask for a numeric interviewer ID instead of a username dropdown?

**Expected answer:**

The current UI sends `interviewerId` directly. It is simple but poor UX and error-prone. A better implementation would expose an Admin-only endpoint listing interviewer users and use a dropdown with display name/username while still submitting the numeric ID.

### Q81. What is wrong with using `new Date(datetimeLocalValue).toISOString()` without thinking?

**Expected answer:**

`datetime-local` has no timezone. Browser locale/timezone conversion can produce a different instant than the Admin intended. The application should document the timezone or use a deliberate local-time conversion strategy.

### Q82. What happens if an Admin assigns an interview twice?

**Expected answer:**

`InterviewService.assign()` looks up an existing interview by application ID and updates it, effectively reassigning/rescheduling. However, the enum includes `RESCHEDULED` but the current method always sets `SCHEDULED`; that mismatch is a subtle implementation gap.

### Q83. What happens if the LLM returns a valid JSON object with extra fields?

**Expected answer:**

Jackson may ignore unknown fields depending on configuration. Strict schema enforcement at the provider level or explicit unknown-field validation may be desired. The important fields still need range and null validation.

### Q84. What happens if the LLM returns `"score": "85"` as a string?

**Expected answer:**

Jackson coercion behavior may accept or reject it depending on configuration. A strict production contract should reject incorrect types or use provider-native JSON schema and strict ObjectMapper settings.

### Q85. Can an LLM confidence score be trusted as a calibrated probability?

**Expected answer:**

No. It is a model-generated signal unless calibrated against labeled data. It can be used as an explainability/review signal, not treated as a mathematically reliable probability.

### Q86. Why is a score from a resume not necessarily fair or objective?

**Expected answer:**

Resumes can contain proxy attributes, inconsistent wording, gaps, and bias from the model or prompt. Human review, auditability, confidence flags, validation, and avoiding automatic rejection are safeguards, but they do not eliminate bias.

### Q87. What happens if the model is unavailable after a candidate submits an answer?

**Expected answer:**

The answer should not be persisted as successfully scored unless a valid score exists. The API should surface a controlled error, let the candidate retry safely, and avoid duplicate answers or inconsistent progress. A production design may persist a pending scoring state and process asynchronously.

### Q88. Why is an in-memory database unsuitable for production?

**Expected answer:**

It loses data on restart, cannot support reliable multi-instance deployment, and has limited operational durability. A production system needs a persistent database, migrations, backups, connection management, and concurrency strategy.

## Section 11: Code-Change Exercises

Use these exercises after the questions. Ask the intern to name files, API changes, data changes, tests, and failure cases.

### Exercise 1: Replace numeric interviewer input with a dropdown

**Expected solution points:**

- Add an Admin-only endpoint to list users with role `INTERVIEWER`.
- Add `api.listInterviewers()`.
- Load interviewers in `DrillDown` or a shared Admin data layer.
- Store selected numeric ID in state.
- Render username/name, submit ID.
- Handle loading/error/empty states.
- Backend assignment must verify user existence and role.
- Add controller/service/security tests.

### Exercise 2: Add an `INVITED` state before `SCREENING`

**Expected solution points:**

- Update enum and migration if needed.
- Define legal transition rules.
- Decide whether invitation is real or still a stub.
- Add audit event.
- Update Admin/Candidate UI labels.
- Ensure candidate cannot start Q&A before `SCREENING`.
- Add tests for valid and invalid transitions.

### Exercise 3: Make scoring asynchronous

**Expected solution points:**

- Do not keep a database transaction open during the entire LLM call.
- Introduce a pending state/job record or durable queue appropriate to scope.
- Make requests idempotent.
- Persist request status and provider errors.
- Prevent duplicate answer scoring.
- Let the frontend poll or use server-sent events.
- Preserve auditability and candidate-friendly errors.

### Exercise 4: Add a second Spring AI provider

**Expected solution points:**

- Add the provider starter/BOM-compatible dependency.
- Implement another adapter of `LlmClient` or configure a provider-neutral `ChatModel` bean.
- Keep prompt contracts and domain records unchanged.
- Select provider through configuration/profile.
- Test provider selection without a live network call.
- Check provider differences in structured-output and response-format support.

### Exercise 5: Persist data in PostgreSQL

**Expected solution points:**

- Replace H2 datasource configuration with environment-driven configuration.
- Keep Flyway migrations portable or add database-specific migrations.
- Add foreign keys and useful indexes, especially application/interview ownership queries.
- Review `@ElementCollection` notes behavior.
- Handle transaction isolation and concurrent updates.
- Add container/CI integration tests.

### Exercise 6: Prevent an interviewer from deciding an already completed interview

**Expected solution points:**

- Check current interview status before recording a decision.
- Decide whether a second decision is an update, rejection, or admin-only override.
- Audit attempted invalid transitions.
- Return a meaningful 409 or 400 rather than silently overwriting.
- Add a concurrency-safe test.

### Exercise 7: Hide score breakdown from candidates

**Expected solution points:**

- Do not solve this only in React.
- Change the candidate DTO and backend endpoint to omit restricted fields.
- Test direct API access as a candidate.
- Confirm Admin/interviewer detail remains available.
- Consider whether the requirements explicitly permit aggregate score, band, per-category score, or only status.

## Section 12: Red Flags During Evaluation

A candidate may have used AI assistance, but these answers indicate insufficient ownership:

- “The frontend filters assignments, so it is secure.”
- “`@PreAuthorize` automatically checks that the interviewer owns the candidate.”
- “The JWT makes the application secure.”
- “Spring AI guarantees the model will always return valid JSON.”
- “Temperature zero makes the model deterministic.”
- “A transaction rolls back an LLM API call.”
- “The candidate can see confidence because it is useful.”
- “The UI prevents duplicate submissions, so the backend does not need a constraint.”
- “The mock class is still active because it has the same interface.”
- “The database is persistent because H2 stores records.”
- “The invitation sends an email.”
- “Changing `interviewer1` to `13` is arbitrary; the username is the ID.”
- “A 400 means the backend is down.”
- “If JSON parses, it is valid enough.”
- “Anti-cheat flags should automatically reject suspicious candidates.”

## Section 13: Strong Candidate Answers Should Mention

The strongest interns will naturally discuss several of these:

- Backend authorization is the real security boundary.
- Role checks and record-level ownership checks are separate.
- DTOs prevent accidental evidence leakage.
- External network calls and database transactions need careful boundaries.
- LLM outputs require schema and semantic validation.
- Retry policy must be bounded and failure-aware.
- Scores and statuses need explicit transition rules.
- In-memory storage is a demo trade-off.
- `localStorage` JWT storage has XSS risk.
- Candidate and interviewer data projections intentionally differ.
- Assignment is represented by the `interviews` table.
- Numeric user IDs and usernames are not interchangeable.
- Tests should use an `LlmClient` fake rather than a real API key.
- Rescoring and duplicate submissions require idempotency decisions.
- Requirements documents can contain inconsistencies; implementation and product decisions must be reconciled explicitly.
- AI confidence is a review signal, not a calibrated truth.

## Section 14: Final Practical Assessment

Ask the intern to perform this explanation without opening the code first:

> “Admin assigns application 1 to interviewer1 at 2 PM. Explain the exact browser request, backend authorization, database row, next API request, React state update, and what happens when the interviewer records ACCEPTED.”

A complete answer should include:

1. Admin browser sends `POST /api/applications/1/assign-interviewer` with numeric interviewer ID and ISO timestamp.
2. Backend requires `ADMIN`.
3. `InterviewService.assign()` creates/updates `interviews`.
4. The row contains `application_id`, `interviewer_id`, `scheduled_at`, and `SCHEDULED`.
5. Application status becomes `INTERVIEW_SCHEDULED`.
6. `ASSIGN_INTERVIEWER` is written to `audit_log`.
7. `interviewer1` logs in and receives a JWT containing their user ID and role.
8. React calls `GET /api/interviewer/assignments` with the bearer token.
9. Backend filters `interviews` by the JWT-derived user ID.
10. Controller enriches results with candidate and JD information.
11. React stores the JSON array in `rows` and maps it to the table.
12. Decision request sends `ACCEPTED` to `/api/applications/1/decision`.
13. Backend rechecks assignment ownership.
14. Interview decision becomes `ACCEPTED`; interview status becomes `COMPLETED`.
15. Application status becomes `INTERVIEW_DONE` and next steps are updated.
16. `RECORD_DECISION` is written to the audit log.

## Reference Files for Interviewers

Use these files when asking code-specific questions:

- Product requirements: [`SmartHire_Pipeline_UseCase.md`](SmartHire_Pipeline_UseCase.md)
- Main README: [`README.md`](README.md)
- Frontend routes: [`frontend/src/App.jsx`](frontend/src/App.jsx)
- API client: [`frontend/src/services/api.js`](frontend/src/services/api.js)
- Interviewer list: [`frontend/src/pages/Interviewer/AssignedList.jsx`](frontend/src/pages/Interviewer/AssignedList.jsx)
- Admin drill-down: [`frontend/src/pages/Admin/DrillDown.jsx`](frontend/src/pages/Admin/DrillDown.jsx)
- JWT login: [`backend/src/main/java/com/smarthire/service/AuthService.java`](backend/src/main/java/com/smarthire/service/AuthService.java)
- JWT filter: [`backend/src/main/java/com/smarthire/security/JwtAuthFilter.java`](backend/src/main/java/com/smarthire/security/JwtAuthFilter.java)
- Interview API: [`backend/src/main/java/com/smarthire/api/InterviewerController.java`](backend/src/main/java/com/smarthire/api/InterviewerController.java)
- Interview workflow: [`backend/src/main/java/com/smarthire/service/InterviewService.java`](backend/src/main/java/com/smarthire/service/InterviewService.java)
- Access rules: [`backend/src/main/java/com/smarthire/service/AccessService.java`](backend/src/main/java/com/smarthire/service/AccessService.java)
- AI adapter: [`backend/src/main/java/com/smarthire/ai/SpringAiLlmClient.java`](backend/src/main/java/com/smarthire/ai/SpringAiLlmClient.java)
- AI contract: [`backend/src/main/java/com/smarthire/ai/LlmClient.java`](backend/src/main/java/com/smarthire/ai/LlmClient.java)
- Seed loader: [`backend/src/main/java/com/smarthire/config/JsonSeedLoader.java`](backend/src/main/java/com/smarthire/config/JsonSeedLoader.java)
- Database schema: [`backend/src/main/resources/db/migration/V1__init.sql`](backend/src/main/resources/db/migration/V1__init.sql)
- Seed data: [`backend/src/main/resources/seed/Input_Data.json`](backend/src/main/resources/seed/Input_Data.json)
