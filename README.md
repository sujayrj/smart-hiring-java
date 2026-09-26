# SmartHire Pipeline

SmartHire is a role-based, AI-assisted hiring portal that connects resume screening, candidate Q&A screening, interview coordination, interviewer decisions, and audit history in one application.

> AI scores; humans decide.

This repository is the Java/Spring Boot + React implementation of the SmartHire Pipeline use case described in [`SmartHire_Pipeline_UseCase.md`](SmartHire_Pipeline_UseCase.md).

## Why SmartHire

Traditional hiring separates resume review, screening tests, and interview coordination across spreadsheets, email, and disconnected tools. SmartHire provides one auditable pipeline:

```text
Job description
  -> Resume match score
  -> Screening promotion or archive
  -> Timed candidate Q&A
  -> Per-answer AI score
  -> Weighted PASS/HOLD/REJECT band
  -> Interview assignment
  -> Interviewer decision and notes
  -> Hiring-manager review and audit history
```

The system is designed to make candidate status, evidence, confidence, and accountability visible without exposing internal AI reasoning to candidates.

## Features

- Role-based authentication for Admin, Candidate, and Interviewer users.
- Admin job-description CRUD with skills, experience, education, location, weights, thresholds, and confidence cutoffs.
- Batch resume scoring against a selected job description.
- Explainable resume results: score, matched skills, gaps, summary, and confidence.
- Automatic promotion of above-threshold candidates to the Q&A screening queue.
- Automatic archival of below-threshold candidates with a recorded reason.
- Candidate Q&A with one question at a time, progress tracking, per-question timer, and timeout submission.
- Rubric-based AI answer scoring with score, justification, confidence, and rubric hits.
- Weighted resume plus Q&A score fusion into PASS, HOLD, or REJECT.
- Candidate-safe status and result views that hide internal AI justifications.
- Admin shortlist sorting, filtering, drill-down, interviewer assignment, next steps, and invitation stub.
- Interviewer dashboard that returns only candidates assigned to the logged-in interviewer.
- Interviewer decisions: Accepted, Rejected, On-Hold, or No-Show.
- Timestamped interviewer notes.
- Confidence, score-divergence, and anti-cheat evidence flags.
- Audit events for scoring, assignments, decisions, notes, invitations, and status changes.
- In-memory H2 database seeded from JSON at startup.

## Technology Stack

### Backend

- Java 17 source compatibility
- Spring Boot 3.4.5
- Spring Web
- Spring Security with stateless JWT authentication
- Spring Data JPA
- Flyway database migrations
- H2 in-memory database
- Spring AI 1.0.0
- Spring AI OpenAI `ChatClient`
- Maven

### Frontend

- React
- Vite
- React Router
- Tailwind CSS
- JavaScript/JSX

### AI integration

The application uses a small internal `LlmClient` interface so pipeline services do not depend directly on a vendor SDK:

```text
ScreeningService / QaService
          |
          v
      LlmClient
          |
          v
 SpringAiLlmClient
          |
          v
 Spring AI ChatClient -> configured chat model/provider
```

The production implementation is [`SpringAiLlmClient.java`](backend/src/main/java/com/smarthire/ai/SpringAiLlmClient.java). The deterministic [`MockLlmClient.java`](backend/src/main/java/com/smarthire/ai/MockLlmClient.java) remains as a plain test helper, but it is not registered as a Spring bean and is not used by the running application.

## Running Locally

### Prerequisites

- Java 17 or newer
- Maven
- Node.js and npm
- An OpenAI-compatible API key for AI scoring

### Configure credentials

The repository contains only an example file. Create a local `.env` file, which is ignored by Git:

```bash
cp .env.example .env
```

Set at least:

```text
OPENAI_API_KEY=your-api-key
JWT_SECRET=your-local-random-jwt-secret
```

The `.env` file must never be committed. `.env.example` is safe to commit because it contains placeholders only.

### Start the backend

```bash
cd backend
mvn spring-boot:run
```

The backend runs on port `8080` by default.

To use another port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

If using a shell that does not automatically load `.env`, export the variables before starting Maven:

```bash
export OPENAI_API_KEY="your-api-key"
export JWT_SECRET="your-local-random-jwt-secret"
mvn spring-boot:run
```

### Start the frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend runs on `http://localhost:5173` and defaults to:

```text
VITE_API_URL=http://localhost:8080/api
```

If the backend is on port `8081`:

```bash
VITE_API_URL=http://localhost:8081/api npm run dev
```

### Run with Docker Compose

```bash
cp .env.example .env
# Add OPENAI_API_KEY and JWT_SECRET to .env
docker compose up --build
```

Docker exposes the frontend on port `5173` and backend on port `8080`.

### H2 console

When the backend is running on port `8080`:

```text
http://localhost:8080/h2-console
```

Use:

```text
JDBC URL: jdbc:h2:mem:smarthire
User:     sa
Password: empty
```

The database is intentionally in memory. Restarting the backend resets and reseeds the data.

## Demo Users

Users are loaded from [`backend/src/main/resources/seed/Input_Data.json`](backend/src/main/resources/seed/Input_Data.json) and passwords are BCrypt encoded during startup.

| Username | Password | Role |
|---|---|---|
| `admin1`, `admin2` | `admin123` | Admin |
| `candidate1` ... `candidate10` | `cand123` | Candidate |
| `interviewer1`, `interviewer2` | `int123` | Interviewer |

## End-to-End Walkthrough

### 1. Admin creates or selects a job description

The Admin portal provides job descriptions containing:

- Role/title
- Must-have skills
- Nice-to-have skills
- Minimum experience
- Education
- Location
- Resume and Q&A weights
- Resume pass threshold
- Confidence cutoff

The seeded job descriptions are:

| Seed ID | Role | Location | Experience | Resume threshold | Weights |
|---|---|---:|---:|---:|---|
| `jd-1` | Frontend Developer | Mumbai | 2 years | 70 | Resume 0.6 / Q&A 0.4 |
| `jd-2` | Java Backend Developer | Bengaluru | 3 years | 72 | Resume 0.6 / Q&A 0.4 |
| `jd-3` | Python Developer | Pune | 2 years | 70 | Resume 0.5 / Q&A 0.5 |

### 2. Admin runs resume screening

Selecting or opening a JD allows the Admin to run resume screening. For each candidate mapped to that JD, the backend sends a single `resume_match` prompt through Spring AI.

The expected structured result is:

```json
{
  "score": 0,
  "matchedSkills": [],
  "gaps": [],
  "summary": "...",
  "confidence": 0.0
}
```

The score is from `0` to `100`; confidence is from `0` to `1`.

- Score at or above the JD threshold: application becomes `SCREENING`.
- Score below the JD threshold: application becomes `ARCHIVED` with a reason.

Resume evidence is persisted before the application status is updated, within a transaction.

### 3. Candidate completes Q&A

Candidates see only their own application data. If promoted to screening, the Candidate portal provides:

- One question at a time
- Progress indicator
- Free-text answer field
- Per-question timer
- Automatic answer submission when time expires
- Anti-cheat telemetry such as tab switches, paste events, and question-copy events

Each answer is sent through one `answer_score` prompt. The structured result is:

```json
{
  "score": 0,
  "justification": "...",
  "confidence": 0.0,
  "rubricHits": []
}
```

The answer score is from `0` to `5`; confidence is from `0` to `1`.

Candidates can see their aggregate result and band, but not internal AI justifications, raw prompts, rubric hits, or anti-cheat evidence.

### 4. Scores are fused

After all questions are answered:

```text
Q&A normalized score = average answer score / 5 * 100

Combined score = resume weight * resume score
              + Q&A weight * normalized Q&A score
```

The combined score is converted to:

- `PASS`: combined score is at or above the JD threshold
- `HOLD`: combined score is at least 80% of the JD threshold but below it
- `REJECT`: combined score is below the HOLD boundary

The Admin remains the human decision-maker and may override the AI band.

### 5. Admin assigns an interview

From the application drill-down, an Admin supplies:

- Interviewer database user ID
- Interview date and time

The backend creates or updates an `interviews` row and sets the application status to `INTERVIEW_SCHEDULED`.

For the default seed insertion order, the interviewer IDs are normally:

```text
interviewer1 -> 13
interviewer2 -> 14
```

The assignment field expects the numeric database ID, not the text `interviewer1`.

### 6. Interviewer reviews assignments

After logging in as `interviewer1`, the frontend calls:

```text
GET /api/interviewer/assignments
```

The backend derives the interviewer ID from the JWT and queries only:

```text
interviews.interviewer_id = logged-in user's ID
```

The frontend does not download all candidates and filter them locally. Assignment isolation is enforced by the backend.

### 7. Interviewer records a decision and notes

The interviewer can record:

- `ACCEPTED`
- `REJECTED`
- `ON_HOLD`
- `NO_SHOW`

Saving a decision:

- Stores the decision on the interview
- Marks the interview `COMPLETED`
- Marks the application `INTERVIEW_DONE`
- Updates next steps
- Writes an audit event

Notes are stored in the `notes` table with author role and timestamp.

## Role Portals

### Admin portal

Routes include:

```text
/admin
/admin/jds/new
/admin/jds/:id/edit
/admin/candidates
/admin/jds/:jdId/shortlist
/applications/:appId
/admin/flags
/admin/audit
```

Admin capabilities include job-description management, scoring, shortlist review, drill-down evidence, assignment, band override, next steps, invitation stub, flags, and audit history.

### Candidate portal

Routes include:

```text
/candidate
/candidate/qa/:appId
/candidate/result/:appId
```

Candidate access is owner-scoped using the candidate identity in the JWT.

### Interviewer portal

Routes include:

```text
/interviewer
/interviewer/applications/:appId
```

The assignment list is interviewer-scoped on the server. The full evidence endpoint also verifies that the interviewer is assigned to the application.

## Backend API

All protected requests use:

```http
Authorization: Bearer <jwt>
```

| Method | Endpoint | Role | Purpose |
|---|---|---|---|
| `POST` | `/api/login` | Public | Authenticate and issue JWT |
| `GET` | `/api/jds` | Admin | List job descriptions |
| `POST` | `/api/jds` | Admin | Create a job description |
| `GET` | `/api/jds/{id}` | Admin | Read a job description |
| `PUT` | `/api/jds/{id}` | Admin | Update a job description |
| `GET` | `/api/candidates` | Admin | List candidates |
| `POST` | `/api/jds/{id}/resume-score` | Admin | Score candidates for one JD |
| `POST` | `/api/jds/resume-score-all` | Admin | Score all JDs |
| `GET` | `/api/jds/{id}/applications` | Admin | Read a JD shortlist |
| `GET` | `/api/applications/{id}` | Authorized | Read application-safe view |
| `GET` | `/api/applications/{id}/detail` | Admin/assigned interviewer | Read full evidence |
| `GET` | `/api/applications/{id}/questions` | Candidate owner | Load Q&A questions |
| `POST` | `/api/applications/{id}/answers` | Candidate owner | Submit and score an answer |
| `GET` | `/api/applications/{id}/my-result` | Candidate owner | Read candidate-safe result |
| `POST` | `/api/applications/{id}/assign-interviewer` | Admin | Assign interviewer and time |
| `POST` | `/api/applications/{id}/decision` | Assigned interviewer | Save interview decision |
| `POST` | `/api/applications/{id}/notes` | Interviewer/Admin | Save timestamped note |
| `POST` | `/api/applications/{id}/override-band` | Admin | Override AI band |
| `POST` | `/api/applications/{id}/invite` | Admin | Trigger invitation stub |
| `GET` | `/api/me/applications` | Candidate | Read own applications |
| `GET` | `/api/interviewer/assignments` | Interviewer | Read own assignments |
| `GET` | `/api/flags` | Admin | Read evidence flags |
| `GET` | `/api/audit` | Admin | Read audit history |
| `POST` | `/api/anticheat` | Candidate | Submit anti-cheat telemetry |

## Data Model

Flyway creates the schema from [`V1__init.sql`](backend/src/main/resources/db/migration/V1__init.sql).

```text
users
  └── interviewer_id -> interviews

interviews
  └── application_id -> applications

applications
  ├── candidate_id -> candidates
  └── jd_id -> job_descriptions

applications
  └── answers -> questions

interviews
  └── notes
```

Important tables:

- `users`: username, BCrypt password hash, role, candidate reference.
- `job_descriptions`: role requirements, weights, pass threshold, confidence cutoff.
- `candidates`: seeded plain-text resume and candidate profile.
- `applications`: resume score, Q&A score, combined score, band, status, next steps.
- `questions`: JD-specific questions, reference answers, and rubric JSON.
- `answers`: candidate answer, score, confidence, rubric hits, timing, timeout flag.
- `interviews`: application assignment, interviewer, scheduled time, state, decision.
- `notes`: timestamped interview notes.
- `audit_log`: timestamped activity history.
- `anticheat_events`: client-side telemetry sent during candidate screening.

## Seed Dataset

The seed loader reads `backend/src/main/resources/seed/Input_Data.json` at startup.

- 3 job descriptions
- 18 questions, 6 for each JD
- 10 synthetic candidates with plain-text resumes
- 14 users: 2 Admins, 10 Candidates, 2 Interviewers
- Edge cases including missing skills, legacy-stack mismatch, overqualification, contradictory claims, and low-confidence profiles

The question bank covers:

- Frontend: Virtual DOM, list virtualization, state management, JWT security, CSS box model, Hooks
- Java: dependency injection, REST controllers, JPA lifecycle, concurrency, exception handling, Spring Security/JWT
- Python: collections, GIL, decorators, framework choice, generators, SQLAlchemy transactions

## Security and Visibility Rules

- Login issues a signed JWT containing user ID, username, and role.
- Backend role checks use Spring Security `@PreAuthorize` rules.
- Candidate identity is derived from the JWT, not from a browser-supplied candidate ID.
- Interviewer assignments are filtered by the authenticated interviewer ID in the backend query.
- Full AI evidence is available only to Admins and assigned Interviewers.
- Candidate endpoints hide AI justifications, confidence, rubric hits, raw prompts, flags, and full resume evidence.
- API keys and local JWT secrets come from environment variables and are not committed.
- This is demo authentication with hard-coded seeded users, not production OAuth/SSO.

## Spring AI Configuration

The current implementation uses Spring AI's OpenAI starter and `ChatClient`.

```yaml
spring:
  ai:
    model:
      chat: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4o-mini}
          temperature: 0.0
```

Switching the OpenAI model only requires changing:

```text
OPENAI_MODEL=gpt-4o-mini
```

The scoring services depend on `LlmClient`, not on OpenAI classes. A future Spring AI provider adapter can replace `SpringAiLlmClient` without changing the screening or Q&A workflow.

The application intentionally uses only two single-turn prompts:

1. `resume_match`: job description plus resume to structured resume evidence.
2. `answer_score`: question, reference answer, rubric, and candidate answer to structured answer evidence.

There are no agents, tool calls, multi-turn flows, queues, or external microservices.

## Structured Output Guardrail

The Spring AI adapter:

1. Requests a JSON-only response.
2. Parses the response into the expected Java record.
3. Validates score ranges, confidence ranges, required text, and list fields.
4. Removes a markdown JSON fence if a model returns one.
5. Retries exactly once with a correction instruction when parsing or validation fails.
6. Throws `ControlledLlmException` after the retry fails.

No unvalidated AI result is persisted.

## Auditability and Flags

Audit records are created for events including:

- Resume screening
- Answer submission
- Scoring completion
- Interview assignment
- Interview decision
- Interview note
- Band override
- Invitation stub
- Anti-cheat telemetry

Flags are evidence for human review. They do not automatically reject a candidate. Examples include:

- Average answer confidence below the configured cutoff
- Strong disagreement between resume and Q&A scores
- Tab switching
- Paste events
- Copying the question

## Scope and Limitations

The following are intentionally outside the scope of this demo:

- Production OAuth, SSO, or external identity provider
- Real email or SMS invitations
- PDF/DOCX resume parsing; resumes are seeded as text
- External queues, Kafka, S3, or microservices
- Agentic or multi-turn AI workflows
- Automatic anti-cheat rejection
- Production-grade persistent database deployment

The invitation button is a stub that records an audit event and displays an in-app message; it does not send email.

## Verification Commands

Backend build and tests:

```bash
cd backend
mvn test
mvn package
```

Frontend build:

```bash
cd frontend
npm run build
```

## Repository Layout

```text
backend/                         Spring Boot API and domain model
backend/src/main/java/.../ai/    LlmClient, Spring AI adapter, test mock
backend/src/main/resources/      application config, Flyway, seed data
frontend/                        React/Vite single-page application
diagrams/                        Pipeline and portal diagrams
SmartHire_Pipeline_UseCase.md    Full project use-case document
SmartHire_LLD_and_Data_Model...  Detailed low-level design
SmartHire_Simplified_Requirements...  Condensed requirements
input_data.json                  Reference seed dataset
docker-compose.yml               Local two-container setup
```

## License

This repository is a project demonstration and does not currently declare a separate open-source license.
