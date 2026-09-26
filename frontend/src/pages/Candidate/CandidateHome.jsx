import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../services/api";
import { StatusBadge } from "../Admin/CandidateList";

/**
 * Candidate homepage (UseCase lines 110–117, 196, 211):
 *  - own live application status (FILTERED/SCREENING/PASSED/HOLD/REJECTED/
 *    INTERVIEW-SCHEDULED/ACCEPTED/NO-SHOW)
 *  - Start / Continue Q&A when promoted
 *  - interview date + interviewer name when scheduled
 *  - aggregate band + next steps (AI justifications hidden, lines 117/172/231)
 *
 * Identity is resolved by the backend from the JWT — GET /api/me/applications.
 */
export default function CandidateHome() {
  const [me, setMe] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .myApplications()
      .then(setMe)
      .catch((e) => setError(e.message));
  }, []);

  if (error) {
    return (
      <div className="max-w-2xl">
        <h1 className="text-2xl font-bold text-slate-900 mb-6">My applications</h1>
        <div className="rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">{error}</div>
      </div>
    );
  }

  if (!me) {
    return <div className="text-slate-500">Loading your applications…</div>;
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-slate-900 mb-1">Welcome, {me.candidateName}</h1>
      <p className="text-sm text-slate-500 mb-6">Your live application status</p>

      {me.applications.length === 0 && (
        <div className="bg-white rounded-xl shadow p-6 text-center">
          <p className="text-sm text-slate-600 font-medium mb-1">No screening activity yet</p>
          <p className="text-xs text-slate-400">
            Your application is awaiting résumé screening by the hiring team. Check back soon.
          </p>
        </div>
      )}

      {me.applications.map((a) => (
        <div key={a.id} className="bg-white rounded-xl shadow p-6 mb-4">
          <div className="flex items-center justify-between mb-3">
            <div>
              <h2 className="text-lg font-bold text-slate-900">{a.jdTitle || `Application #${a.id}`}</h2>
              <p className="text-xs text-slate-400">Application #{a.id}</p>
            </div>
            <StatusBadge status={a.status} />
          </div>

          {a.nextSteps && <p className="text-sm text-slate-600 mb-4">{a.nextSteps}</p>}

          {a.interviewAt && (
            <div className="rounded-lg bg-indigo-50 border border-indigo-200 px-4 py-3 text-sm text-indigo-800 mb-4">
              Interview scheduled with <b>{a.interviewerName}</b> on{" "}
              {new Date(a.interviewAt).toLocaleString()}
            </div>
          )}

          {(a.status === "SCREENING" || a.status === "QA_IN_PROGRESS") && (
            <Link
              to={`/candidate/qa/${a.id}`}
              className="inline-block bg-indigo-600 hover:bg-indigo-700 text-white font-semibold px-4 py-2 rounded-lg text-sm"
            >
              ▶ {a.status === "QA_IN_PROGRESS" ? "Continue Q&A" : "Start Q&A"}
            </Link>
          )}

          {a.band && (
            <div className="mt-4">
              <div className="rounded-lg bg-emerald-50 border border-emerald-200 px-4 py-3 text-sm text-emerald-800 mb-3">
                Result: <strong>{a.band}</strong>
                {a.nextSteps ? ` — ${a.nextSteps}` : ""}
              </div>
              <Link
                to={`/candidate/result/${a.id}`}
                className="text-sm text-indigo-600 font-semibold hover:underline"
              >
                View your detailed result →
              </Link>
            </div>
          )}
        </div>
      ))}

      <p className="text-xs text-slate-400 mt-4">
        You see only aggregate results. Detailed AI evidence is not shared with candidates (spec §8.3, §11).
      </p>
    </div>
  );
}
