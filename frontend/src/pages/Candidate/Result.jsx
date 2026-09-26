import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../../services/api";

/** Candidate result: aggregate + per-question breakdown. Justifications/confidence are hidden (guardrail). */
export default function Result() {
  const { appId } = useParams();
  const [res, setRes] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .getMyResult(appId)
      .then(setRes)
      .catch((e) => setError(e.message));
  }, [appId]);

  if (error) return <div className="max-w-xl mx-auto rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">{error}</div>;
  if (!res) return <div className="text-slate-500">Loading…</div>;

  const tone = {
    PASS: "bg-emerald-50 border-emerald-200 text-emerald-800",
    HOLD: "bg-amber-50 border-amber-200 text-amber-800",
    REJECT: "bg-slate-50 border-slate-200 text-slate-700",
  }[res.band] || "bg-white border-slate-200 text-slate-700";

  return (
    <div className="max-w-2xl mx-auto">
      <div className={`rounded-2xl border p-8 text-center mb-6 ${tone}`}>
        {res.band ? (
          <>
            <div className="text-3xl font-extrabold mb-3">{res.band}</div>
            <p className="text-sm">
              Combined score: <b>{Math.round((res.combinedScore ?? 0) * 10) / 10}/100</b> — {res.jdTitle}
            </p>
            <p className="text-xs mt-3 opacity-70">
              Justifications and rubric details are not shared with candidates.
            </p>
          </>
        ) : (
          <>
            <div className="text-lg font-bold mb-2">Screening in progress</div>
            <p className="text-sm text-slate-500">Complete the Q&amp;A round to receive your result.</p>
          </>
        )}
      </div>

      {res.breakdown.length > 0 && (
        <div className="bg-white rounded-xl shadow p-6">
          <h2 className="text-sm font-bold text-slate-800 mb-4">Your per-question breakdown</h2>
          {res.breakdown.map((r, i) => (
            <div key={i} className="border-t border-slate-100 py-3 first:border-0">
              <p className="text-sm text-slate-800 font-medium">{i + 1}. {r.questionText}</p>
              <div className="flex gap-2 mt-1 text-xs">
                <span className="rounded-full bg-slate-100 px-2 py-0.5 font-mono text-slate-600">
                  score {r.score}/5
                </span>
                {r.timeTakenSeconds != null && (
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 font-mono text-slate-600">
                    {r.timeTakenSeconds}s
                  </span>
                )}
                {r.timeout && (
                  <span className="rounded-full bg-red-50 px-2 py-0.5 font-mono text-red-700">
                    auto-submitted (timeout)
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
