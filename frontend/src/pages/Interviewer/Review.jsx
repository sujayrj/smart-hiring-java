import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../../services/api";

/** §7.4 steps 3–5: review résumé + Q&A + AI evidence, record decision + notes.
 *  NOTE: résumé/answers/evidence come from candidate detail; aggregate via /applications/{id}. */
export default function InterviewReview() {
  const { appId } = useParams();
  const [decision, setDecision] = useState("");
  const [note, setNote] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function submitDecision(e) {
    e.preventDefault();
    setMessage("");
    setError("");
    try {
      if (decision) {
        await api.recordDecision(appId, { decision });
      }
      if (note.trim()) {
        await api.addNote(appId, note.trim());
      }
      setMessage("Saved. Decision and note are recorded with timestamps.");
      setNote("");
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="max-w-3xl">
      <h1 className="text-2xl font-bold text-slate-900 mb-1">Review &amp; decision</h1>
      <p className="text-sm text-slate-500 mb-6">
        Application #{appId} — résumé, Q&amp;A answers and AI evidence are available in the candidate detail view;
        record your decision and timestamped notes below (spec §7.4).
      </p>

      {message && (
        <div className="mb-4 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm px-4 py-3">{message}</div>
      )}
      {error && (
        <div className="mb-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">{error}</div>
      )}

      <form onSubmit={submitDecision} className="bg-white rounded-xl shadow p-6 space-y-5">
        <div>
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Decision</h3>
          <div className="space-y-2">
            {[
              { value: "SELECT", label: "SELECT — move forward", cls: "peer-checked:border-emerald-500 peer-checked:bg-emerald-50" },
              { value: "REJECT", label: "REJECT — close application", cls: "peer-checked:border-red-500 peer-checked:bg-red-50" },
              { value: "ON_HOLD", label: "ON HOLD — further review", cls: "peer-checked:border-amber-500 peer-checked:bg-amber-50" },
            ].map((opt) => (
              <label key={opt.value} className="flex items-center gap-3 rounded-lg border border-slate-200 px-4 py-3 cursor-pointer">
                <input
                  type="radio"
                  name="decision"
                  value={opt.value}
                  checked={decision === opt.value}
                  onChange={(e) => setDecision(e.target.value)}
                  className="peer sr-only"
                />
                <span className={`w-4 h-4 rounded-full border-2 border-slate-300 peer-checked:border-indigo-600 ${opt.cls}`} />
                <span className="text-sm font-medium text-slate-800">{opt.label}</span>
              </label>
            ))}
          </div>
        </div>

        <div>
          <h3 className="text-sm font-semibold text-slate-800 mb-2">Timestamped note</h3>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows="4"
            placeholder="Observations, follow-ups, concerns…"
            className="w-full rounded-lg border border-slate-300 p-3 focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={!decision && !note.trim()}
            className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 text-white font-semibold px-5 py-2 rounded-lg text-sm"
          >
            Save decision &amp; note
          </button>
        </div>
      </form>
      <p className="mt-3 text-xs text-slate-400">
        Saving writes RECORD_DECISION and ADD_NOTE audit events (spec §16).
      </p>
    </div>
  );
}
