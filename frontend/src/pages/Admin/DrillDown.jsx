import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, getRole } from "../../services/api";
import { BandBadge } from "../Admin/Shortlist";

/**
 * Unified drill-down (Admin + assigned Interviewer): résumé evidence, per-question
 * answers with justifications/confidence/timings, flags, interview state.
 * Role-aware actions: Admin → assign · override band · next steps · invite stub.
 * Interviewer → decision (Accepted/Rejected/On-Hold/No-Show) + notes.
 */
export default function DrillDown() {
  const { appId } = useParams();
  const role = getRole();
  const [d, setD] = useState(null);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [note, setNote] = useState("");
  const [decision, setDecision] = useState("");
  const [assign, setAssign] = useState({ interviewerId: "", scheduledAt: "" });
  const [override, setOverride] = useState({ band: "", nextSteps: "" });

  const load = useCallback(
    () => api.getDetail(appId).then(setD).catch((e) => setError(e.message)),
    [appId]
  );

  useEffect(() => {
    load();
  }, [load]);

  function act(fn, okMsg) {
    fn.then((res) => {
      setToast(okMsg || (res && res.message) || "Done");
      setError("");
      load();
    }).catch((e) => {
      setError(e.message);
      setToast("");
    });
  }

  if (error && !d)
    return <div className="rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">{error}</div>;
  if (!d) return <div className="text-slate-500">Loading…</div>;

  return (
    <div className="max-w-5xl">
      <Link to={role === "INTERVIEWER" ? "/interviewer" : `/admin/jds/${d.jdTitle ? "" : ""}`} onClick={(e) => { if (role !== "INTERVIEWER") { e.preventDefault(); window.history.back(); } }} className="text-sm text-indigo-600 font-medium">← Back</Link>
      <div className="flex items-center justify-between mt-1 mb-1">
        <h1 className="text-2xl font-bold text-slate-900">
          {d.candidateName} <span className="text-slate-400 font-normal text-lg">· {d.jdTitle}</span>
        </h1>
        <div className="flex items-center gap-2">
          <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600">{d.status}</span>
          {d.band && <BandBadge band={d.band} />}
          {d.combinedScore != null && (
            <span className="font-mono text-sm font-bold text-indigo-700">{Math.round(d.combinedScore * 10) / 10}/100</span>
          )}
        </div>
      </div>
      <p className="text-sm text-slate-500 mb-6">{d.candidateEmail} · application #{d.applicationId}</p>

      {toast && <div className="mb-4 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm px-4 py-3">{toast}</div>}
      {error && <div className="mb-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">{error}</div>}

      <div className="grid grid-cols-2 gap-4">
        {/* Résumé evidence */}
        <section className="bg-white rounded-xl shadow p-5">
          <h2 className="text-sm font-bold text-slate-800 mb-3">Résumé evidence (AI)</h2>
          <div className="flex gap-4 mb-3">
            <Metric label="Score" value={d.resumeScore == null ? "—" : `${Math.round(d.resumeScore)}/100`} />
            <Metric label="Confidence" value={d.resumeConfidence == null ? "—" : d.resumeConfidence} />
          </div>
          <p className="text-xs text-slate-500 mb-1"><span className="font-semibold text-emerald-700">Matched:</span> {d.resumeMatched || "—"}</p>
          <p className="text-xs text-slate-500 mb-2"><span className="font-semibold text-red-700">Gaps:</span> {d.resumeGaps || "—"}</p>
          <p className="text-xs text-slate-600 italic">{d.resumeSummary}</p>
        </section>

        {/* Flags */}
        <section className="bg-white rounded-xl shadow p-5">
          <h2 className="text-sm font-bold text-slate-800 mb-3">Flags (evidence only — never auto-reject)</h2>
          {d.flags.length === 0 && <p className="text-xs text-slate-400">No flags raised.</p>}
          {d.flags.map((f, i) => (
            <div key={i} className="mb-2 rounded-lg bg-amber-50 border border-amber-200 px-3 py-2">
              <span className="text-xs font-bold text-amber-700">{f.type}</span>
              <span className="text-xs text-slate-600 ml-2 font-mono">{f.detail}</span>
            </div>
          ))}
        </section>

        {/* Q&A evidence */}
        <section className="bg-white rounded-xl shadow p-5 col-span-2">
          <h2 className="text-sm font-bold text-slate-800 mb-3">Q&amp;A answers (justifications visible to authorized roles only)</h2>
          {d.qa.length === 0 && <p className="text-xs text-slate-400">No answers yet.</p>}
          {d.qa.map((r) => (
            <div key={r.questionId} className="border-t border-slate-100 py-3 first:border-0">
              <p className="text-sm font-semibold text-slate-800">{r.questionText}</p>
              <p className="text-xs text-slate-600 mt-1 mb-2">“{r.answerText}”</p>
              <div className="flex flex-wrap gap-2 text-xs">
                <Chip>score {r.score}/5</Chip>
                <Chip>conf {r.confidence}</Chip>
                <Chip>{r.timeTakenSeconds != null ? `${r.timeTakenSeconds}s taken` : "time n/a"}</Chip>
                {r.timeout && <Chip red>timeout auto-submit</Chip>}
                <Chip>rubric: {r.rubricHits?.join(", ") || "—"}</Chip>
              </div>
            </div>
          ))}
        </section>

        {/* Interview block */}
        <section className="bg-white rounded-xl shadow p-5">
          <h2 className="text-sm font-bold text-slate-800 mb-3">Interview</h2>
          {!d.interview && <p className="text-xs text-slate-400">Not assigned yet.</p>}
          {d.interview && (
            <>
              <p className="text-xs text-slate-600 mb-1">Interviewer: <b>{d.interview.interviewerName}</b></p>
              <p className="text-xs text-slate-600 mb-1">When: {new Date(d.interview.scheduledAt).toLocaleString()}</p>
              <p className="text-xs text-slate-600 mb-2">State: {d.interview.status}{d.interview.decision ? ` · decision: ${d.interview.decision}` : ""}</p>
              {d.interview.notes.map((n, i) => (
                <p key={i} className="text-xs text-slate-500 border-l-2 border-slate-200 pl-2 mb-1">
                  {new Date(n.createdAt).toLocaleString()} · {n.authorRole}: {n.text}
                </p>
              ))}
            </>
          )}
          {d.nextSteps && <p className="text-xs text-indigo-700 mt-2 font-medium">Next steps: {d.nextSteps}</p>}
        </section>

        {/* Role actions */}
        <section className="bg-white rounded-xl shadow p-5">
          <h2 className="text-sm font-bold text-slate-800 mb-3">Actions</h2>

          {role === "ADMIN" && (
            <div className="space-y-4">
              <div>
                <p className="text-xs font-semibold text-slate-500 mb-1">Assign interviewer + date</p>
                <div className="flex gap-2">
                  <input
                    type="number"
                    min="1"
                    step="1"
                    placeholder="user id (13 / 14)"
                    value={assign.interviewerId}
                    onChange={(e) => setAssign({ ...assign, interviewerId: e.target.value })}
                    className="border border-slate-300 rounded-lg px-2 py-1.5 text-xs w-28" />
                  <input type="datetime-local" value={assign.scheduledAt}
                    onChange={(e) => setAssign({ ...assign, scheduledAt: e.target.value })}
                    className="border border-slate-300 rounded-lg px-2 py-1.5 text-xs" />
                  <button
                    disabled={
                      !assign.interviewerId ||
                      !Number.isInteger(Number(assign.interviewerId)) ||
                      Number(assign.interviewerId) < 1 ||
                      !assign.scheduledAt
                    }
                    onClick={() => act(api.assignInterviewer(appId, {
                      interviewerId: Number(assign.interviewerId),
                      scheduledAt: new Date(assign.scheduledAt).toISOString(),
                    }), "Interviewer assigned")}
                    className="bg-indigo-600 text-white rounded-lg px-3 py-1.5 text-xs font-semibold disabled:opacity-40">Assign</button>
                </div>
              </div>

              <div>
                <p className="text-xs font-semibold text-slate-500 mb-1">Override band (human final call)</p>
                <div className="flex gap-2">
                  <select value={override.band} onChange={(e) => setOverride({ ...override, band: e.target.value })}
                    className="border border-slate-300 rounded-lg px-2 py-1.5 text-xs">
                    <option value="">choose band…</option>
                    <option value="PASS">PASS</option>
                    <option value="HOLD">HOLD</option>
                    <option value="REJECT">REJECT</option>
                  </select>
                  <input placeholder="next steps (optional)" value={override.nextSteps}
                    onChange={(e) => setOverride({ ...override, nextSteps: e.target.value })}
                    className="border border-slate-300 rounded-lg px-2 py-1.5 text-xs flex-1" />
                  <button disabled={!override.band}
                    onClick={() => act(api.overrideBand(appId, override), `Band overridden to ${override.band}`)}
                    className="bg-amber-500 text-white rounded-lg px-3 py-1.5 text-xs font-semibold disabled:opacity-40">Override</button>
                </div>
              </div>

              <button onClick={() => act(api.invite(appId))}
                className="border border-indigo-300 text-indigo-700 rounded-lg px-3 py-1.5 text-xs font-semibold hover:bg-indigo-50">
                ✉ Send invitation (stubbed → toast + audit)
              </button>
            </div>
          )}

          {role === "INTERVIEWER" && (
            <div className="space-y-4">
              <div>
                <p className="text-xs font-semibold text-slate-500 mb-1">Record decision</p>
                <div className="grid grid-cols-2 gap-2">
                  {[["ACCEPTED", "bg-emerald-600"], ["REJECTED", "bg-red-600"], ["ON_HOLD", "bg-amber-500"], ["NO_SHOW", "bg-slate-500"]].map(([v, cls]) => (
                    <button key={v} onClick={() => setDecision(v)}
                      className={`rounded-lg px-3 py-1.5 text-xs font-semibold ${decision === v ? `${cls} text-white` : "border border-slate-300 text-slate-600"}`}>
                      {v.replace("_", "-")}
                    </button>
                  ))}
                </div>
                <button disabled={!decision}
                  onClick={() => act(api.recordDecision(appId, { decision }), `Decision recorded: ${decision}`)}
                  className="mt-2 w-full bg-indigo-600 text-white rounded-lg px-3 py-1.5 text-xs font-semibold disabled:opacity-40">Save decision</button>
              </div>
              <div>
                <p className="text-xs font-semibold text-slate-500 mb-1">Add timestamped note</p>
                <textarea rows="2" value={note} onChange={(e) => setNote(e.target.value)}
                  className="w-full border border-slate-300 rounded-lg px-2 py-1.5 text-xs" placeholder="Observations…" />
                <button disabled={!note.trim()}
                  onClick={() => act(api.addNote(appId, note.trim()).then(() => setNote("")), "Note added")}
                  className="mt-1 w-full bg-indigo-600 text-white rounded-lg px-3 py-1.5 text-xs font-semibold disabled:opacity-40">Save note</button>
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

function Metric({ label, value }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2">
      <div className="text-[10px] uppercase tracking-wide text-slate-400 font-semibold">{label}</div>
      <div className="text-sm font-bold text-slate-800 font-mono">{value}</div>
    </div>
  );
}

function Chip({ children, red }) {
  return (
    <span className={`rounded-full px-2 py-0.5 font-mono ${red ? "bg-red-50 text-red-700" : "bg-slate-100 text-slate-600"}`}>
      {children}
    </span>
  );
}
