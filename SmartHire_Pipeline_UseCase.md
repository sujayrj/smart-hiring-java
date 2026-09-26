Use Case Short Title:
---------------------
SmartHire Pipeline : AI-Powered Screening & Interview Tracking Portal

Problem Statement & Background:
-------------------------------
Hiring today is broken across two stages, and no existing tool covers both end to end - recruiters down in resumes, 
and interview coordination lives in scattered email threads. Strong candidates get missed, weak ones reach expensive interivew
panels, and there is no single source of truth for status, evidence or accountability. A single portal that chains AI-driven screening with role based interview tracking can fix both problems at once.

Problem -> What we solve:
-------------------------
Hundreds of resumes, no reliable filter - every resume auto-matched to the JD with an explainable score, matched skills and gaps.
Manual, inconsistent conceptual screening - short AI-graded free-text Q&A round with rubric-based scoring, justification and confidence - the same yardstick for every candidate.
Interview coordination trapped in email- role based dashboards for Hiring Manager and Interviewer replace inbox chaos.
No live view of candidate status - one click status updates (Accepted/Rejected/On-Hold/No-show) interviewer notes, and "Next steps" lgged against each candidate.
Slow funnel, poor candidate experience - time to shortlist cut from days to minutes, every decision auditable, feedback loops tightened.

Objective:
---------
Build a role-based web app that runs the end to end screening funnel from one place, with a dedicated portal for each user type.
Hiring Manager(Admin) Portal - feed the description (role, must have skills experience, education); every incoming resume is auto scored against the JD by an AI call; above threshold candidates advance to screening the rest are archived with a reson. The Admin sees the full pipeline dashboard, assign interviews, logs "Next steps" and drills into all AI evidence.
Candidate Portal - filtered candidates are auot-invited into a short AI-graded free text Q&A round with per question timer and anti-cheat signals. Each answer is scored via a rubric-based AI call (score, justification, confidence); the candidate sees only their aggregate PASS/HOLD/REJECT band and can log in anytime to check their live application status.
Interviewer Portal - sees only assigned candidates with AI evidence attached, submits structured decisions (Accepted/Rejected/On-Hold/ No-Show) with notes after taking in the interview.

Single Source of truth - every candidate's live status is visible on the Admin dashboard, sortable, filterable, and fully auditable, resume-match and Q&A signals are fsed into one combined band with justification and confidence.

In-Scope (Must-have):
---------------------
- Authentication - hard-coded users (2 Admins, 3 Candidates, 2 Interviewers) with JWT-style token.
- Job Description CRUD - create /select JD, edit scoring weights, thresholds
- Resume Auto-scoring - invoked as a batch job that can run at specific intervals and process a batch of JDs
- Screening Promotion - above threshold -> "Screening" queue; below-threshold -> archived with reason.
- Candidate Q&A UI - one question at a time, progress bar, timer, auto-submit on timeout
- Answer Scoring - LLM call per answer, rubric-based JSON response.
- Combined decision - weighted score -> PASS/HOLD/REJECT band (displayed to Candidate)
- Admin Dashboard - sortable, filterable shortlist; drill-drown view; assign interviewer & interview date; free-text "Next Steps"
- Interview UI - list only assigned Candidates, view AI evidence, toggle status add notes.
- Audit log - every event timestamped and persisted
- LLM client wrapper - pluggable (openai, anthropic, ...) via .env key
- Local run via uvicorn + npm run dev


Out-of-scope (will not be built):
---------------------------------
- Multi-turn or agentic AI flows - only two single turn prompts.
- Real email / SMS invitations - stubbed toast + console log only.
- PDF / DOCX parsing - resumes are seeded as plain-text strings.
- External queues / micro-services - single monolith repo.
- Production grade auth (OAuth, SSO)
- Complex anti-cheat decisions (auto-reject)

Definition of Done:
-------------------
Objective                                                               Success Metric (Hackathon)
-----------                                                             --------------------------
End to end flow from JD upload -> resume auto-scoring                   Demonstrable walk-thru with atleast 2 JDs, 5 
                                                                        candidates per JD
-> candidate screening -> interview decision

The workflow will be as follows:
Job creation -> Resume parsing (Score 1) -> Test link (Score 2)
-> Interview (Score3) -> Final Total Score -> Hiring Manager decision.
Candidate can see only final status (select/reject...) not score




Role-based UI (Admin/Candidate/Interviewer)                             Each view reachable after login; UI updates in 
                                                                        realtime

AI intergration (2 structured prompts)                                  JSON output validated, retry on failure


Data persistence (in-memory SQLite)                                     All actions survive page refresh for the duration of 
                                                                        the hack


Auditability                                                            Every score, status transition and note stored with 
                                                                        timestamp, viewable in Admin drill-down


Anti-cheat telemetry (good to have)
                                                                        Tab-switch, paste, copy-question counters captured client-side and displayed only to admin


Score calculation                                                       At first, score should be calculated based on resume 
                                                                        screening -> if it crossed the threshold, then only a test link should trigger for the candidate and re-scoring will be done based on the test taken




Data & Setup
-------------
- Seed data (Job descriptions, synthetic resumes, question bank with reference answeres and rubrics, seeded users) loads at startup from an in-memory store (SQLite in-memory or dict). Three role-based logins work; Hiring manager (Admin), Candidate and Interviewer - each lands on their own portal.


Admin (Hiring manager) Portal:
-----------------------------
- Admin can create or select a JD (role, must-have skills , nice to have skills, experience, education, location)
- On JD selection from the list of JDs, every candidate resume for that JD is auto-scored against the JD via one structured LLM call returning JSON (score 0-100, matched skills, gaps, summary)
- Above threshold candidates are auto promoted to the screening round; below threshold profiles are achived with the reson recorded.
- Ranked shorlist per JD is visible on the dashboard - sortable and filterable by combined score, status and confidence
- Drill-down on any candidate shows: resume evidence, per-question answers, AI scores, justifications, confidence, timings, and anti-cheat flags.
- Admin can assign an interviewer to a candidate.
- Admin can log free-text "Next steps" per candidate (ex.g: Technical Round2, HR discussion)
- Auto-flag surffaced when avg confidence < 0.6 or when resume and Q&A scores strongly disagree. Based on the job description and candidate resume, use a LLM to analyze and calculate the ATS score for the job.
- Invitation action is stubbed: in-app toast plus logged event; no real email sent.

Candidate Portal:
-----------------
- Candidate can log in and see their live application status (Filtered/Screening/Passed/Hold/Rejected/Interview-scheduled/Accepted/No-show)
- If promoted to screening, candidate can launch the Q&A round from the portal.
- Q&A UI shows one question at a time with a progress bar, per-question timer, and free-text answer area; answers submit one at a time.
- Timer auto-submits the current answer on timeout
- Anti-cheat telemetry (tab-switch count, paste events, copy-of-question events) is captured client-side and sent with submission
- After Q&A submission, candidate sees only their aggregate combined score right after submission, per-category breakdown and PASS/HOLD/REJECT band - AI justifications are hidden.


Interviewer Portal:
------------------
- Interviewer sees only candidates assigned to them by the Admin.
- Per assigned candidate, Interviewer can view resume evidence, Q&A answers, AI scores, confidence and anti-cheat flags.
- Interviewer can toggle candidate status to Accepted/Rejected/On-Hold/No-show.
- Interviewer can add free-text notes againsta candidate, timestamped and preserved in the record.


AI&Performance :
----------------
- Two structured LLM prompts only - resume_match and answer_score - each returning strict JSON validated against a schema.
- Each answer is scored by one LLM call returning score (0-5), 1-2 line justification, confidence(0-1) and rubric hits.
- Weighted combined score is produced from resume-match + Q&A using configurable weights and produces a PASS / HOLD / REJECT band.
- Scoring returns within 2-3 seconds of submit for a smooth candidate experience.
- Retry on invalid-JSON guardrail keeps scoring deterministic; failure surfaces cleanly rather than silently.



Expected outcomes:
------------------
- AI-driven shortlisting - every resume auto-scored against the JD; qualified candidates surfaced in minutes, unqualified archived with a reson.
- Consistent conceptual screening - rubric based AI scoring of free-text answers gives every candidate the same yardstick with justification and confidence
- One combined decision signal - resume-match and Q&A fused into a single PASS / HOLD / REJECT band with full evidence.
- Role based clarity - Admin runs the pipeline, Interviewer sees only assigned Candidates, Candidate sees only their status no email , no confusion.
- Live, auditable status - every candidate's stage is visible on the Admin dashboard, drillable to AI scores, notes and anti-cheat flags.
- Faster, fairer hiring - time to shortlist cut from days to minutes; decisions backed by explainable reproducible AI evidence.


Solution Requirements:
----------------------
Tech Stack (open source)
- Frontend - React+Vite (or Angular/Vue); Tailwind or plain CSS for styling. Single SPA with three role-aware views.
- Backend - Python+FastAPI (recommended) or Node+Express / Java+Spring Boot / C# with .NET 10. REST APIs , JSON over HTTPS.
- Storage - In memory SQLite seeded at startup from the SQLite db in JSON format
- Auth - hardcoded users for each role (Admin/Candidate/Interviewer) with simple session token or JWT. No OAUTH, no SSO.
- Packaging - local run via uvicorn + npm run dev or a single Docker container for demo


AI Integration (LLM via public API, non-agentic)
------------------------------------------------
- Two structured prompts only - resume_match and answer_score - both single-turn, prompt-in / JSON-out. No agents, no tool-calling, no multi-turn memory.
- LLM accessed over a public HTTP API (OpenAI, Anthropic or any equivalent) behind one swappable llm_client.call (prompt, schema) function so the provider can be changed with a config flag.
- API key kept in an environment variable (.env) never committed to source.
- Strict JSON output enforced via schema or function-calling; retry once on invalid JSON, then fail cleanly.
- Deterministic and reproducible prompts embed the JD, reference answers, and rubric so scoring is explainable and repeatable.


Design & UX:
------------
- Every AI score paired with justification, confidence and rubric hits - nothing is a black box.
- Configurable per JD - recruiter can tune scoring weights, pass threshold and confidence cut-off without code changes.
- Human in the loop safety- auto-flag low-confidence answeres on when resumeand Q&A scores disagree; recruiter always has the final call.
- Anti-gaming - AU justifications hidden from candidates, anti-cheat signals surfaced to Admin only, never used for auto-reject.
- Responsive UX - scoring returns within 2-3 seconds; per question-timer and progress bar keep candidates oriented.
- Auditability - every score, status transition and interviewer note timestamped and preserved.


Out of scope (kept simple on purpose)
-------------------------------------
- No agentic AI, no Langchain agents, no autonomous tool use.
- No microservices, message bus or external queue.
- No PDF/DOCX parsing resumes seeded as plain text.
- No real email - invite is stubbed in-app toast plus logged event.
- No real authentication provider - hardcoded users only.



Detailed breakdown of Tasks:
----------------------------
Basic Tasks - Must have:
-----------------------
- Bootstrap the project : React+Vite (Angular/Vue) frontend, FastAPI (or equivalent) backend, in-memory SQLite/dict store; single-repo, single Docker or local run
- Seed JSON at startup : 2-3 JDs, 8-10 synthetic resumes, 5-7 rubric backed questions per role, 2 Admin/ 3 Candidate / 2 Interviewer users.
- REST CRUD APIs for JDs, Job role, Resumes, Questions, Candidates and Status.
- Role-based login for Admin, Candidate and Interviewer - hardcoded users, simple session token or JWT.
- Admin view: list candidates per JD with basic sort and filter by status.
- Candidate view: loging and see own live application status and date of the interview scheduled.
- Interviewer view: list only assigned candidates and date of interview scheduled.


Intermediate Tasks - Must Have
------------------------------
- LLM client wrapper (single llm_client.call (prompt, schema) function calling a public LLM HTTP API with the key in .env.
- resume_match LLM call returning strict JSON: score (0-100), matched skills, gaps, summary - invoked when Admin opens a JD.
- Auto-promotion of above threshold candidates to the screening round, archive with reason for below threshold.
- Candidate Q&A UI : one question at a time, progress bar, per-question timer, free-text input, auto-submit on timeout.
- answer_score LLM call returning strict JSON : score (0-5), justification, confidence (0-1), rubric hits.
- Weighted combined score with PASS/ HOLD/ REJECT band; candidate facing summary shows only aggregate+band (no justification)
- Admin drill-down: resume evidence, per-question answers, AI scores, confidence, timings.
- Assign-interviewer and Date of interview action from Admin; Interviewer view respects the assignment.
- Interviewer can toggle status (Accepted/Rejected/On-Hold/No-Show) and add timestamped notes.
- Candidate can also view the basic details of the interviewer like name.
- Sortable and Filterable ranked shortlist with rich drill-down (by score, status, confidence and timings).


Advanced Tasks - Good to Have
------------------------------
- Anti-cheat telemetry captured client-side: tab-switch counter, paste detection, copy-of-question detection - surfaced in the Admin dashboard, never used for auto-reject.
- Auto-flag candidates when average confidence < 0.6 or when resume and Q&A scores strongly disagree.
- "Next Steps" pipeline log per candidate (e.g. Technical Round 2, HR discussion, Background Check).
- Stubbed invitation action (in-app toast plus logged event) triggered by Admin approval.
- Retry-on-invalid-JSON guardrail (For ex: 4xx response codes) and JSON-schema validation for both LLM prompts but just once.
- Logging and alerts can be introduced to trace the errors.
- Configurable per JD screening: scoring weights, pass threshold and confidence cutoff editable from the Admin UI.
- Hiring manager / Admin can review the status updated by the LLM before the final call is taken on pass / hold / reject of the candidate.
- Docker - one container that spins up both backend (uvicorn) and frontend (npm run dev) (optional)


Guardrails (What NOT to do)
------------------------------
- Never call the LLM in a loop without the JSON-schema guard rail - must retry at most once based on error response codes.
- Do not expose AI justifications or raw prompts to the Candidate view
- Do not store any API keys in source control - use .env
- Do not implement real email / SMS - only toast notifications.
- Do not add external services (Kafka, S3, etc) - keep everything in the single container.
- Do not write production-grade authentication - hard-coded users only.


DataSet
--------
- All data lives in input_data.json (or python dict) and is loaded into the in-memory SQLite on startup.


Brief summary of the data:
--------------------------
1. Job descriptions - 3 JDs
- jd-1 Frontend Developer (Mumbai, 2y, threshold 70)
- jd-2 Java Backend Developer (Bengaluru, 3y, threshold 72)
- jd-3 Python Developer (Pune, 2y, threshold 70)

Each has : must have, nice to have, skills, experience_years, education, location, weights (resume/QA), pass_threshold, confidence_cutoff, summary

2. questions - 18 total (6 per JD)
Each has id, jd_id, text, reference_answer, and a rubric with score_5/score_3/score_0 criteria bands - used by the answer_score LLM call.
- Frontend : Virtual DOM, list virtualization, state mgmt, JWT security, CSS box model, hook rules
- Java: DI, @RestController, JPA lifecycle, concurrency locking, exception handling, JWT+Spring Security
- Python: Collections, GIL, Decorators, Framework comparison, generators, SQLAlchemy Transactions

3. Candidates - 10 plain-text resumes
Each has : id, user, name, email, applied_id, experience_years, education, location, profile_type, resume

Distribution & Edge cases:
- Frontend (4): Strong fit, missing Typescript, Senior Overqualified, Junior Borderline
- Java (3) : Strong fit, Legacy stack (no Spring Boot), Contradictory over-claim
- Python (3) : Strong fit, Django-only mismatch, Data-science low-confidence

4. Users - 14 hardcoded logins
- 2 admins (admin 1/2 -- admin123)
- 10 candidates (candidate1...10 - cand123)
- 2 interviewers (interviewer1/2...10 - int123)

The dataset contains 2 JDs, 8 synthetic resumes, 5-7 questions per JD, and the hard-coded users above.
Edge-cases (missing skill, low confidence, contradictory scores) are internationally included.





