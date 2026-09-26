import { NavLink, useNavigate } from "react-router-dom";
import { clearSession, getRole, getUsername } from "../services/api";
const NAV = {
  ADMIN: [
    { to: "/admin", label: "Job Descriptions" },
    { to: "/admin/candidates", label: "Candidates" },
    { to: "/admin/flags", label: "Flags" },
    { to: "/admin/audit", label: "Audit History" },
  ],
  CANDIDATE: [{ to: "/candidate", label: "My application" }],
  INTERVIEWER: [{ to: "/interviewer", label: "My Interviews" }],
};

export default function Layout({ children }) {
  const role = getRole();
  const username = getUsername();
  const navigate = useNavigate();

  function logout() {
    clearSession();
    navigate("/login");
  }

  return (
    <div className="min-h-screen bg-slate-100 flex">
      <aside className="w-60 bg-white border-r border-slate-200 flex flex-col">
        <div className="p-5 flex items-center gap-2.5 border-b border-slate-100">
          <div className="w-8 h-8 rounded-lg bg-indigo-600 text-white flex items-center justify-center font-bold text-sm">SH</div>
          <div>
            <div className="font-bold text-slate-900 text-sm">SmartHire</div>
            <div className="text-[11px] text-slate-400 capitalize">{role?.toLowerCase()} portal</div>
          </div>
        </div>
        <nav className="p-3 space-y-1 flex-1">
          {(NAV[role] || []).map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end
              className={({ isActive }) =>
                `block px-3 py-2 rounded-lg text-sm font-medium ${
                  isActive ? "bg-indigo-50 text-indigo-700" : "text-slate-500 hover:bg-slate-50"
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <button
          onClick={logout}
          className="m-3 text-left px-3 py-2 text-sm font-semibold text-red-600 hover:bg-red-50 rounded-lg"
        >
          Log out
        </button>
      </aside>

      <main className="flex-1 p-8">
        <div className="mb-4 text-right text-xs text-slate-400">
          {username} · {role}
        </div>
        {children}
      </main>
    </div>
  );
}
