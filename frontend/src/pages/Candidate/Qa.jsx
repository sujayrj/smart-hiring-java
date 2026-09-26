import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../../services/api";

const DEFAULT_SECONDS = 300;

export default function Qa() {
  const { appId } = useParams();
  const navigate = useNavigate();

  const [questions, setQuestions] = useState([]);
  const [index, setIndex] = useState(0);
  const [answer, setAnswer] = useState("");
  const [secondsLeft, setSecondsLeft] = useState(DEFAULT_SECONDS);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const timerRef = useRef(null);
  const submittingRef = useRef(false);
  const qStartRef = useRef(Date.now());

  const current = questions[index];

  const submit = useCallback(
    async (timeout) => {
      if (submittingRef.current || !current) return;
      submittingRef.current = true;
      setBusy(true);
      setError("");
      try {
        const ack = await api.submitAnswer(appId, {
          questionId: current.id,
          answerText: answer,
          timeout,
          timeTakenSeconds: Math.round((Date.now() - qStartRef.current) / 1000),
        });
        if (!ack.qaComplete) {
          setAnswer("");
          setSecondsLeft(current.timeLimitSeconds || DEFAULT_SECONDS);
          qStartRef.current = Date.now();
          setIndex((i) => i + 1);
        } else {
          navigate(`/candidate/result/${appId}`);
        }
      } catch (e) {
        setError(e.message);
      } finally {
        setBusy(false);
        submittingRef.current = false;
      }
    },
    [answer, appId, current, navigate]
  );

  // one question at a time (§7.2)
  useEffect(() => {
    api
      .getQuestions(appId)
      .then((qs) => {
        setQuestions(qs);
        if (qs.length > 0) setSecondsLeft(qs[0].timeLimitSeconds || DEFAULT_SECONDS);
        else navigate(`/candidate/result/${appId}`);
      })
      .catch((e) => setError(e.message));
  }, [appId, navigate]);

  // timer with auto-submit (§7.2 step 3)
  useEffect(() => {
    if (!current) return undefined;
    timerRef.current = setInterval(() => {
      setSecondsLeft((s) => {
        if (s <= 1) {
          clearInterval(timerRef.current);
          submit(true); // timeout auto-submit
          return 0;
        }
        return s - 1;
      });
    }, 1000);
    return () => clearInterval(timerRef.current);
  }, [current, submit]);

  // anti-cheat telemetry (§12) — capture, never auto-reject
  useEffect(() => {
    function onVisibility() {
      if (document.hidden) {
        api.reportAnticheat(Number(appId), "TAB_SWITCH", "visibilitychange").catch(() => {});
      }
    }
    function onPaste() {
      api.reportAnticheat(Number(appId), "PASTE", "answer textarea").catch(() => {});
    }
    function onCopy() {
      api.reportAnticheat(Number(appId), "COPY_QUESTION", "copy event during Q&A").catch(() => {});
    }
    document.addEventListener("visibilitychange", onVisibility);
    window.addEventListener("paste", onPaste);
    window.addEventListener("copy", onCopy);
    return () => {
      document.removeEventListener("visibilitychange", onVisibility);
      window.removeEventListener("paste", onPaste);
      window.removeEventListener("copy", onCopy);
    };
  }, [appId]);

  function onVisibility() {
    if (document.hidden) {
      api.reportAnticheat(Number(appId), "TAB_SWITCH", "visibilitychange").catch(() => {});
    }
  }

  if (error) {
    return <div className="rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">{error}</div>;
  }

  if (!current) {
    return <div className="text-slate-500">Loading question…</div>;
  }

  const progress = ((index) / Math.max(questions.length, 1)) * 100;
  const mm = String(Math.floor(secondsLeft / 60)).padStart(2, "0");
  const ss = String(secondsLeft % 60).padStart(2, "0");

  return (
    <div className="max-w-3xl">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-xl font-bold text-slate-900">
          Question {index + 1} of {questions.length}
        </h1>
        <div className={`rounded-full px-4 py-1.5 font-mono font-bold text-sm ${secondsLeft <= 30 ? "bg-red-100 text-red-700" : "bg-slate-100 text-slate-700"}`}>
          ⏱ {mm}:{ss}
        </div>
      </div>

      <div className="h-2 bg-slate-200 rounded-full mb-6">
        <div className="h-2 bg-indigo-600 rounded-full transition-all" style={{ width: `${progress}%` }} />
      </div>

      <div className="bg-white rounded-xl shadow p-6 mb-4">
        <p className="text-slate-900 leading-relaxed">{current.text}</p>
      </div>

      <textarea
        value={answer}
        onChange={(e) => setAnswer(e.target.value)}
        rows="8"
        placeholder="Type your answer…"
        className="w-full rounded-xl border border-slate-300 p-4 focus:ring-2 focus:ring-indigo-500"
      />

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      <div className="mt-4 flex items-center justify-between">
        <p className="text-xs text-slate-400">
          The timer auto-submits your current answer at 0:00. Scores are not shown to you.
        </p>
        <button
          onClick={() => submit(false)}
          disabled={busy}
          className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white font-semibold px-6 py-2.5 rounded-lg"
        >
          {busy ? "Submitting…" : "Submit answer"}
        </button>
      </div>
    </div>
  );
}
