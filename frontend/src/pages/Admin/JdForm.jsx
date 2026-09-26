import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../../services/api";

const EMPTY = {
  title: "",
  location: "",
  experienceYears: 3,
  mustHave: "",
  niceToHave: "",
  resumeWeight: 0.6,
  qaWeight: 0.4,
  passThreshold: 65,
  confidenceCutoff: 0.6,
  summary: "",
};

export default function JdForm() {
  const { id } = useParams();
  const editing = Boolean(id);
  const [jd, setJd] = useState(EMPTY);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    if (id) api.getJd(id).then(setJd).catch((e) => setError(e.message));
  }, [id]);

  function set(field, value) {
    setJd((prev) => ({ ...prev, [field]: value }));
  }

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const payload = {
        ...jd,
        experienceYears: Number(jd.experienceYears),
        resumeWeight: Number(jd.resumeWeight),
        qaWeight: Number(jd.qaWeight),
        passThreshold: Number(jd.passThreshold),
        confidenceCutoff: Number(jd.confidenceCutoff),
      };
      if (id) await api.updateJd(id, payload);
      else await api.createJd(payload);
      navigate("/admin");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="max-w-3xl">
      <h1 className="text-2xl font-bold text-slate-900 mb-6">
        {editing ? "Edit Job Description" : "New Job Description"}
      </h1>

      {error && (
        <div className="mb-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">
          {error}
        </div>
      )}

      <form onSubmit={submit} className="bg-white rounded-xl shadow p-6 space-y-5">
        <div className="grid grid-cols-2 gap-4">
          <div className="col-span-2">
            <label className="block text-sm font-medium text-slate-600 mb-1">Title *</label>
            <input required value={jd.title} onChange={(e) => set("title", e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="Senior Java Developer" />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-600 mb-1">Location</label>
            <input value={jd.location} onChange={(e) => set("location", e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="Remote" />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-600 mb-1">Experience (years)</label>
            <input type="number" min="0" value={jd.experienceYears}
              onChange={(e) => set("experienceYears", e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2" />
          </div>
          <div className="col-span-2">
            <label className="block text-sm font-medium text-slate-600 mb-1">Summary</label>
            <textarea value={jd.summary} onChange={(e) => set("summary", e.target.value)} rows="2"
              className="w-full rounded-lg border border-slate-300 px-3 py-2" />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-600 mb-1">Must-have skills (comma separated)</label>
            <input value={jd.mustHave} onChange={(e) => set("mustHave", e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="Java, Spring, SQL" />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-600 mb-1">Nice-to-have</label>
            <input value={jd.niceToHave} onChange={(e) => set("niceToHave", e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="Docker, Kafka" />
          </div>
        </div>

        <div className="border-t border-slate-200 pt-4">
          <h3 className="text-sm font-semibold text-slate-800 mb-3">Scoring configuration</h3>
          <div className="grid grid-cols-4 gap-4">
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Résumé weight</label>
              <input type="number" step="0.05" min="0" max="1" value={jd.resumeWeight}
                onChange={(e) => set("resumeWeight", e.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Q&amp;A weight</label>
              <input type="number" step="0.05" min="0" max="1" value={jd.qaWeight}
                onChange={(e) => set("qaWeight", e.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Pass threshold %</label>
              <input type="number" min="0" max="100" value={jd.passThreshold}
                onChange={(e) => set("passThreshold", e.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Confidence cutoff</label>
              <input type="number" step="0.05" min="0" max="1" value={jd.confidenceCutoff}
                onChange={(e) => set("confidenceCutoff", e.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2" />
            </div>
          </div>
          <p className="text-xs text-slate-400 mt-2">
            Candidates scoring below the pass threshold are archived with a reason (spec §7.1).
          </p>
        </div>

        <div className="flex justify-end gap-3">
          <button type="button" onClick={() => navigate("/admin")}
            className="px-4 py-2 rounded-lg border border-slate-300 text-slate-600 text-sm font-semibold">
            Cancel
          </button>
          <button type="submit" disabled={busy}
            className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white text-sm font-semibold px-5 py-2 rounded-lg">
            {busy ? "Saving…" : "Save JD"}
          </button>
        </div>
      </form>
    </div>
  );
}
