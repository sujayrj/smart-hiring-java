import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../services/api";

/** Admin candidate pipeline: promote / assign interview (§7.4 step 1). */
export default function CandidateList() {
  const [candidates, setCandidates] = useState([]);
  const [interviewers, setInterviewers] = useState([]);
  const [assigning, setAssigning] = useState(null); // applicationId
  const [form, setForm] = useState({ interviewerId: "", scheduledAt: "" });
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function load() {
    try {
      const list = await api.listCandidates();
      setCandidates(list);
    } catch (e) {
      setError(e.message);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function assign(e) {
    e.preventDefault();
    try {
      await api.assignInterviewer(assigning, {
        interviewerId: Number(form.interviewerId),
        scheduledAt: new Date(form.scheduledAt).toISOString(),
      });
      setMessage("Interview assigned.");
      setAssigning(null);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900 mb-6">Candidates</h1>

      {message && (
        <div className="mb-4 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm px-4 py-3">{message}</div>
      )}
      {error && (
        <div className="mb-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">{error}</div>
      )}

      <div className="bg-white rounded-xl shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-500 text-left">
            <tr>
              <th className="px-4 py-3 font-semibold">Name</th>
              <th className="px-4 py-3 font-semibold">Applied JD</th>
              <th className="px-4 py-3 font-semibold">Exp</th>
              <th className="px-4 py-3 font-semibold">Status</th>
            </tr>
          </thead>
          <tbody>
            {candidates.map((c) => (
              <tr key={c.id} className="border-t border-slate-100">
                <td className="px-4 py-3 font-medium text-slate-900">{c.name}</td>
                <td className="px-4 py-3 text-slate-600">JD #{c.appliedJd}</td>
                <td className="px-4 py-3 text-slate-600">{c.experienceYears} yrs</td>
                <td className="px-4 py-3">
                  <StatusBadge status={c.status} />
                </td>
              </tr>
            ))}
            {candidates.length === 0 && (
              <tr><td colSpan="4" className="px-4 py-8 text-center text-slate-400">No candidates seeded.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      <p className="mt-3 text-xs text-slate-400">
        Assign interviews from the JD row → Run screening results, or use the application view.
      </p>
    </div>
  );
}

export function StatusBadge({ status }) {
  const map = {
    APPLIED: "bg-slate-100 text-slate-600",
    RESUME_SCORED: "bg-sky-100 text-sky-700",
    SCREENING: "bg-sky-100 text-sky-700",
    QA_IN_PROGRESS: "bg-amber-100 text-amber-700",
    FUSED: "bg-indigo-100 text-indigo-700",
    PASS: "bg-emerald-100 text-emerald-700",
    HOLD: "bg-amber-100 text-amber-700",
    REJECT: "bg-red-100 text-red-700",
    ARCHIVED: "bg-red-100 text-red-700",
    INTERVIEW_SCHEDULED: "bg-emerald-100 text-emerald-700",
    INTERVIEW_DONE: "bg-slate-200 text-slate-700",
  };
  return (
    <span className={`inline-block rounded-full px-2.5 py-1 text-xs font-semibold ${map[status] || "bg-slate-100 text-slate-600"}`}>
      {status}
    </span>
  );
}
