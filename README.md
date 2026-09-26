# SmartHire — Java/Spring Boot + React implementation

Single monolithic hiring portal implementing the SmartHire specification:
JD creation → AI résumé screening → timed candidate Q&A → weighted score fusion
(PASS/HOLD/REJECT) → interviewer decision → audit history.

**AI scores; humans decide.**

## Stack
- **Backend:** Java 17, Spring Boot 3.2, Spring Security (JWT), Spring Data JPA, Flyway, H2 in-memory
- **Frontend:** React 18 + Vite, React Router
- **AI:** Spring AI `ChatClient` with OpenAI chat model; SmartHire uses an internal `LlmClient` port so provider adapters can be changed without changing pipeline services

## Run locally

### Backend (port 8081 in dev if 8080 busy)
```bash
cd backend
mvn spring-boot:run                      # requires OPENAI_API_KEY for AI scoring
# or: mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```
H2 console: http://localhost:8080/h2-console (JDBC url `jdbc:h2:mem:smarthire`, user `sa`)

### Frontend (Vite dev server)
```bash
cd frontend
npm install
npm run dev                              # http://localhost:5173
```
The SPA expects the API at `VITE_API_URL` (default `http://localhost:8080/api`).
If the backend runs on 8081: `VITE_API_URL=http://localhost:8081/api npm run dev`

### Docker Compose
```bash
cp .env.example .env
docker compose up --build                # frontend :5173, backend :8080
```

## Demo users (seeded from input_data.json, BCrypt)
| Username | Password | Role |
|---|---|---|
| admin1, admin2 | admin123 | ADMIN |
| candidate1 … candidate10 | cand123 | CANDIDATE |
| interviewer1, interviewer2 | int123 | INTERVIEWER |

## Demo walkthrough
1. Login as **admin** → Job Descriptions → **Run ▶** on a JD (AI scores all applied candidates; ≥ threshold → SCREENING, else archived with reason)
2. Login as **candidate** → Start Q&A → answer questions (timer auto-submits at 0:00) → see aggregate band only
3. Login as **admin** → assign interviewer + date
4. Login as **interviewer1** → review assignment → record decision + note
5. Admin → **Audit History** shows every state change; **Flags** shows anti-cheat/confidence flags

## API (spec §9)
| Method | Endpoint | Role |
|---|---|---|
| POST | /api/login | Public |
| GET/POST | /api/jds, GET/PUT /api/jds/{id} | Admin |
| GET | /api/candidates | Admin |
| POST | /api/jds/{id}/resume-score | Admin |
| GET | /api/applications/{id} | Authorized (ownership enforced) |
| GET | /api/applications/{id}/questions | Candidate (owner) |
| POST | /api/applications/{id}/answers | Candidate (owner) |
| POST | /api/applications/{id}/assign-interviewer | Admin |
| POST | /api/applications/{id}/decision | Interviewer |
| POST | /api/applications/{id}/notes | Interviewer/Admin |
| GET | /api/interviewer/assignments | Interviewer |
| GET | /api/flags, /api/audit | Admin |
| POST | /api/anticheat | Candidate |

## Design notes (from the spec)
- **Monolith**, browser talks only to the backend; non-agentic
- **Two LLM prompts only**: `resume_match` (0–100 + skills/gaps/confidence), `answer_score` (0–5 + confidence + rubric hits), sent through Spring AI
- **Guardrail:** every LLM response validated; invalid JSON → exactly **one** retry → controlled error
- **Model switching:** change `OPENAI_MODEL`; swapping Spring AI providers requires changing the starter and adapter wiring, not pipeline services
- **Persist scoring before status updates** (transactional)
- Candidate sees aggregates only; AI evidence restricted to Admin/Interviewer
- Flags (confidence < 0.6, divergence > 30, tab-switch/paste) are evidence, never auto-rejection
- In-memory DB: restart resets state (seeded via Flyway)
