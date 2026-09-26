import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../services/api";

/** §7.4 step 2: assigned candidates ONLY — server filters by interviewer. */
export default function AssignedList() {
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api.myAssignments().then(setRows).catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900 mb-1">Candidates assigned to me</h1>
      <p className="text-sm text-slate-500 mb-6">You only see candidates explicitly assigned to you.</p>

      {error && (
        <div className="mb-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">{error}</div>
      )}

      <div className="bg-white rounded-xl shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-500 text-left">
            <tr>
              <th className="px-4 py-3 font-semibold">Candidate</th>
              <th className="px-4 py-3 font-semibold">Job description</th>
              <th className="px-4 py-3 font-semibold">Scheduled</th>
              <th className="px-4 py-3 font-semibold">Status</th>
              <th className="px-4 py-3 font-semibold">Decision</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.interviewId} className="border-t border-slate-100">
                <td className="px-4 py-3 font-medium text-slate-900">{r.candidateName}</td>
                <td className="px-4 py-3 text-slate-600">{r.jdTitle || `App #${r.applicationId}`}</td>
                <td className="px-4 py-3 font-mono text-xs text-slate-600">
                  {new Date(r.scheduledAt).toLocaleString()}
                </td>
                <td className="px-4 py-3">
                  <span className="rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                    {r.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-slate-600">{r.decision || "—"}</td>
                <td className="px-4 py-3 text-right">
                  <Link
                    to={`/interviewer/applications/${r.applicationId}`}
                    className="bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold px-3 py-1.5 rounded-lg"
                  >
                    Review →
                  </Link>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr><td colSpan="6" className="px-4 py-8 text-center text-slate-400">No interviews assigned yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
