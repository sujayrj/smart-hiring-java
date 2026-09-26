import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../services/api";

export default function JdList() {
  const [jds, setJds] = useState([]);
  const [busyId, setBusyId] = useState(null);
  const [message, setMessage] = useState("");

  useEffect(() => {
    api.listJds().then(setJds).catch((e) => setMessage(e.message));
  }, []);

  async function runScreening(id) {
    setBusyId(id);
    setMessage("");
    try {
      const result = await api.runScreening(id);
      setMessage(
        `Screening complete: ${result.promoted} promoted, ${result.archived} archived, ${result.failed} failed.`
      );
    } catch (e) {
      setMessage(e.message);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-slate-900">Job Descriptions</h1>
        <div className="flex gap-2">
          <button
            onClick={() => api.runScreeningAll().then((r) => setMessage(`Batch complete: ${r.jdsProcessed} JDs scored.`)).catch((e) => setMessage(e.message))}
            className="border border-indigo-300 text-indigo-700 font-semibold px-4 py-2 rounded-lg text-sm hover:bg-indigo-50"
          >
            ▶ Run all (batch)
          </button>
          <Link
            to="/admin/jds/new"
            className="bg-indigo-600 hover:bg-indigo-700 text-white font-semibold px-4 py-2 rounded-lg text-sm"
          >
            + New JD
          </Link>
        </div>
      </div>

      {message && (
        <div className="mb-4 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm px-4 py-3">
          {message}
        </div>
      )}

      <div className="bg-white rounded-xl shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-500 text-left">
            <tr>
              <th className="px-4 py-3 font-semibold">Title</th>
              <th className="px-4 py-3 font-semibold">Location</th>
              <th className="px-4 py-3 font-semibold">Exp</th>
              <th className="px-4 py-3 font-semibold">Weights R/Q</th>
              <th className="px-4 py-3 font-semibold">Pass %</th>
              <th className="px-4 py-3 font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody>
            {jds.map((jd) => (
              <tr key={jd.id} className="border-t border-slate-100">
                <td className="px-4 py-3 font-medium text-slate-900">{jd.title}</td>
                <td className="px-4 py-3 text-slate-600">{jd.location}</td>
                <td className="px-4 py-3 text-slate-600">{jd.experienceYears}+</td>
                <td className="px-4 py-3 text-slate-600 font-mono text-xs">
                  {jd.resumeWeight} / {jd.qaWeight}
                </td>
                <td className="px-4 py-3 font-semibold text-slate-900">{jd.passThreshold}</td>
                <td className="px-4 py-3 space-x-2 whitespace-nowrap">
                  <button
                    onClick={() => runScreening(jd.id)}
                    disabled={busyId === jd.id}
                    className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white text-xs font-semibold px-3 py-1.5 rounded-lg"
                  >
                    {busyId === jd.id ? "Screening…" : "Run ▶"}
                  </button>
                  <Link
                    to={`/admin/jds/${jd.id}/edit`}
                    className="border border-slate-300 text-slate-600 text-xs font-semibold px-3 py-1.5 rounded-lg"
                  >
                    Edit
                  </Link>
                  <Link
                    to={`/admin/jds/${jd.id}/shortlist`}
                    className="border border-slate-300 text-slate-600 text-xs font-semibold px-3 py-1.5 rounded-lg"
                  >
                    Shortlist
                  </Link>
                </td>
              </tr>
            ))}
            {jds.length === 0 && (
              <tr>
                <td colSpan="6" className="px-4 py-8 text-center text-slate-400">
                  No job descriptions yet — create one with “+ New JD”.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <p className="mt-3 text-xs text-slate-400">
        Run executes AI résumé screening for all candidates applied to that JD (spec §7.1).
      </p>
    </div>
  );
}
