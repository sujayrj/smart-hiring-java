const BASE = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

export function getToken() {
  return localStorage.getItem("smarthire_token");
}

export function getRole() {
  return localStorage.getItem("smarthire_role");
}

export function getUsername() {
  return localStorage.getItem("smarthire_user");
}

export function setSession(token, role, username) {
  localStorage.setItem("smarthire_token", token);
  localStorage.setItem("smarthire_role", role);
  localStorage.setItem("smarthire_user", username);
}

export function clearSession() {
  ["smarthire_token", "smarthire_role", "smarthire_user"].forEach((k) =>
    localStorage.removeItem(k)
  );
}

async function request(path, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${BASE}${path}`, { ...options, headers });

  if (res.status === 401) {
    clearSession();
    if (!path.includes("/login")) window.location.href = "/login";
    throw new Error("Unauthorized");
  }

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (body.message) message = body.message; // safe server-side message (§13)
    } catch {
      /* ignore */
    }
    throw new Error(message);
  }

  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  login: (username, password) =>
    request("/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),

  // Admin — JDs
  listJds: () => request("/jds"),
  getJd: (id) => request(`/jds/${id}`),
  createJd: (jd) => request("/jds", { method: "POST", body: JSON.stringify(jd) }),
  updateJd: (id, jd) => request(`/jds/${id}`, { method: "PUT", body: JSON.stringify(jd) }),

  // Admin — screening
  runScreening: (jdId) => request(`/jds/${jdId}/resume-score`, { method: "POST" }),
  runScreeningAll: () => request("/jds/resume-score-all", { method: "POST" }),
  shortlist: (jdId) => request(`/jds/${jdId}/applications`),
  listCandidates: () => request("/candidates"),

  // Applications
  getApplication: (id) => request(`/applications/${id}`),
  getQuestions: (id) => request(`/applications/${id}/questions`),
  submitAnswer: (id, body) =>
    request(`/applications/${id}/answers`, { method: "POST", body: JSON.stringify(body) }),
  assignInterviewer: (id, body) =>
    request(`/applications/${id}/assign-interviewer`, { method: "POST", body: JSON.stringify(body) }),
  recordDecision: (id, body) =>
    request(`/applications/${id}/decision`, { method: "POST", body: JSON.stringify(body) }),
  addNote: (id, text) =>
    request(`/applications/${id}/notes`, { method: "POST", body: JSON.stringify({ text }) }),
  getDetail: (id) => request(`/applications/${id}/detail`),
  getMyResult: (id) => request(`/applications/${id}/my-result`),
  overrideBand: (id, body) =>
    request(`/applications/${id}/override-band`, { method: "POST", body: JSON.stringify(body) }),
  invite: (id) => request(`/applications/${id}/invite`, { method: "POST" }),

  // Candidate — own applications (identity derived from JWT on the backend)
  myApplications: () => request("/me/applications"),

  // Interviewer
  myAssignments: () => request("/interviewer/assignments"),

  // Admin — flags & audit
  flags: () => request("/flags"),
  audit: () => request("/audit"),

  // Candidate — anti-cheat telemetry (§12)
  reportAnticheat: (applicationId, eventType, payload) =>
    request("/anticheat", {
      method: "POST",
      body: JSON.stringify({ applicationId, eventType, payload }),
    }),
};
