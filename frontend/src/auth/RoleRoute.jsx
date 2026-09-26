import { Navigate } from "react-router-dom";
import { getRole } from "../services/api";

export default function RoleRoute({ role, children }) {
  const current = getRole();
  if (!current) return <Navigate to="/login" replace />;
  if (role && current !== role) return <Navigate to={`/${current.toLowerCase()}`} replace />;
  return children;
}
