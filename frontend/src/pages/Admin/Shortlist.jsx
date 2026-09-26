import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../../services/api";

/** Ranked shortlist per JD — sortable and filterable (requirement: by score, status, confidence). */
export default function Shortlist() {
  const { jdId } = useParams();
  const [rows, setRows] = useState([]);
  const [jd, setJd] = useState(null);
  const [error, setError] = useState("");
  const [sortKey, setSortKey] = useState("combinedScore");
  const [sortDir, setSortDir] = useState("desc");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [minScore, setMinScore] = useState("");

  useEffect(() => {
    api.getJd(jdId).then(setJd).catch((e) => setError(e.message));
    api
      .shortlist(jdId)
      .then(setRows)
      .catch((e) => setError(e.message));
  }, [jdId]);

  const view = useMemo(() => {
    let v = [...rows];
    if (statusFilter !== "ALL") v = v.filter((r) => r.status === statusFilter);
    if (minScore !== "") v = v.filter((r) => (r.combinedScore ?? r.resumeScore ?? 0) >= Number(minScore));
    v.sort((a, b) => {
      const av = a[sortKey] ?? -1;
      const bv = b[sortKey] ?? -1;
      const cmp = typeof av === "string" ? av.localeCompare(bv) : av - bv;
      return sortDir === "asc" ? cmp : -cmp;
    });
    return v;
  }, [rows, sortKey, sortDir, statusFilter, minScore]);

  function header(label, key) {
    const active = sortKey === key;
    return (
      <th
        className="px-3 py-2.5 font-semibold cursor-pointer select-none hover:text-slate-800"
        onClick={() => {
          if (active) setSortDir(sortDir === "asc" ? "desc" : "asc");
          else {
            setSortKey(key);
            setSortDir("desc");
          }
        }}
      >
        {label} {active ? (sortDir === "asc" ? "▲" : "▼") : ""}
      </th>
    );
  }

  const statuses = ["ALL", ...new Set(rows.map((r) => r.status))];

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <div>
          <Link to="/admin" className="text-sm text-indigo-600 font-medium">← Job Descriptions</Link>
          <h1 className="text-2xl font-bold text-slate-900 mt-1">
            Shortlist — {jd ? jd.title : `JD #${jdId}`}
          </h1>
        </div>
        <button
          onClick={() => api.runScreening(jdId).then(() => api.shortlist(jdId).then(setRows)).catch((e) => setError(e.message))}
          className="bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold px-4 py-2 rounded-lg"
        >
          ▶ Re-run scoring
        </button>
      </div>

      {error && <div className="mb-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">{error}</div>}

      <div className="flex gap-3 mb-4 items-center">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}
          className="rounded-lg border border-slate-300 text-sm px-3 py-2">
          {statuses.map((s) => <option key={s} value={s}>{s === "ALL" ? "All statuses" : s}</option>)}
        </select>
        <input value={minScore} onChange={(e) => setMinScore(e.target.value)} placeholder="min combined score"
          className="rounded-lg border border-slate-300 text-sm px-3 py-2 w-44" />
        <span className="text-xs text-slate-400">{view.length} of {rows.length} shown · click headers to sort</span>
      </div>

      <div className="bg-white rounded-xl shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-500 text-left">
            <tr>
              {header("Candidate", "candidateName")}
              {header("Profile", "profileType")}
              {header("Résumé", "resumeScore")}
              {header("Conf.", "resumeConfidence")}
              {header("Q&A", "qaScore")}
              {header("Combined", "combinedScore")}
              {header("Band", "band")}
              {header("Status", "status")}
              <th></th>
            </tr>
          </thead>
          <tbody>
            {view.map((r) => (
              <tr key={r.applicationId} className="border-t border-slate-100">
                <td className="px-3 py-2.5 font-medium text-slate-900">{r.candidateName}</td>
                <td className="px-3 py-2.5 text-xs text-slate-500">{r.profileType}</td>
                <td className="px-3 py-2.5 font-mono text-xs">{fmt(r.resumeScore)}</td>
                <td className="px-3 py-2.5 font-mono text-xs">{fmt(r.resumeConfidence)}</td>
                <td className="px-3 py-2.5 font-mono text-xs">{fmt(r.qaScore)}</td>
                <td className="px-3 py-2.5 font-mono text-xs font-bold">{fmt(r.combinedScore)}</td>
                <td className="px-3 py-2.5">{r.band ? <BandBadge band={r.band} /> : "—"}</td>
                <td className="px-3 py-2.5"><span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600">{r.status}</span></td>
                <td className="px-3 py-2.5 text-right">
                  <Link to={`/applications/${r.applicationId}`} className="bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold px-3 py-1.5 rounded-lg">Drill in →</Link>
                </td>
              </tr>
            ))}
            {view.length === 0 && (
              <tr><td colSpan="9" className="px-4 py-8 text-center text-slate-400">No applications — run scoring first.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function fmt(v) {
  return v == null ? "—" : Math.round(v * 10) / 10;
}

export function BandBadge({ band }) {
  const map = {
    PASS: "bg-emerald-100 text-emerald-700",
    HOLD: "bg-amber-100 text-amber-700",
    REJECT: "bg-red-100 text-red-700",
  };
  return <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${map[band] || "bg-slate-100 text-slate-600"}`}>{band}</span>;
}
