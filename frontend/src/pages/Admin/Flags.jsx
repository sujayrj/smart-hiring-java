import { useEffect, useState } from "react";
import { api } from "../../services/api";

export default function Flags() {
  const [flags, setFlags] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api.flags().then(setFlags).catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900 mb-1">Flags</h1>
      <p className="text-sm text-amber-700 font-medium mb-6">
        Evidence for human review — flags never auto-reject a candidate (spec §12).
      </p>
      {error && (
        <div className="mb-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">{error}</div>
      )}
      <div className="bg-white rounded-xl shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-500 text-left">
            <tr>
              <th className="px-4 py-3 font-semibold">Application</th>
              <th className="px-4 py-3 font-semibold">Flag type</th>
              <th className="px-4 py-3 font-semibold">Detail</th>
            </tr>
          </thead>
          <tbody>
            {flags.map((f, i) => (
              <tr key={i} className="border-t border-slate-100">
                <td className="px-4 py-3 font-medium text-slate-900">App #{f.applicationId} — {f.candidateName}</td>
                <td className="px-4 py-3">
                  <span className="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-700">
                    {f.type}
                  </span>
                </td>
                <td className="px-4 py-3 font-mono text-xs text-slate-600">{f.detail}</td>
              </tr>
            ))}
            {flags.length === 0 && (
              <tr><td colSpan="3" className="px-4 py-8 text-center text-slate-400">No flags raised.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
