import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import RoleRoute from "./auth/RoleRoute";
import Layout from "./components/Layout";
import Login from "./pages/Login/Login";
import JdList from "./pages/Admin/JdList";
import JdForm from "./pages/Admin/JdForm";
import CandidateList from "./pages/Admin/CandidateList";
import Audit from "./pages/Admin/Audit";
import Flags from "./pages/Admin/Flags";
import Shortlist from "./pages/Admin/Shortlist";
import DrillDown from "./pages/Admin/DrillDown";
import CandidateHome from "./pages/Candidate/CandidateHome";
import Qa from "./pages/Candidate/Qa";
import Result from "./pages/Candidate/Result";
import AssignedList from "./pages/Interviewer/AssignedList";
import Review from "./pages/Interviewer/Review";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />

        <Route path="/admin" element={<RoleRoute role="ADMIN"><Layout><JdList /></Layout></RoleRoute>} />
        <Route path="/admin/jds/new" element={<RoleRoute role="ADMIN"><Layout><JdForm /></Layout></RoleRoute>} />
        <Route path="/admin/jds/:id/edit" element={<RoleRoute role="ADMIN"><Layout><JdForm /></Layout></RoleRoute>} />
        <Route path="/admin/candidates" element={<RoleRoute role="ADMIN"><Layout><CandidateList /></Layout></RoleRoute>} />
        <Route path="/admin/jds/:jdId/shortlist" element={<RoleRoute role="ADMIN"><Layout><Shortlist /></Layout></RoleRoute>} />
        <Route path="/applications/:appId" element={<RoleRoute><Layout><DrillDown /></Layout></RoleRoute>} />
        <Route path="/admin/flags" element={<RoleRoute role="ADMIN"><Layout><Flags /></Layout></RoleRoute>} />
        <Route path="/admin/audit" element={<RoleRoute role="ADMIN"><Layout><Audit /></Layout></RoleRoute>} />

        <Route path="/candidate" element={<RoleRoute role="CANDIDATE"><Layout><CandidateHome /></Layout></RoleRoute>} />
        <Route path="/candidate/qa/:appId" element={<RoleRoute role="CANDIDATE"><Layout><Qa /></Layout></RoleRoute>} />
        <Route path="/candidate/result/:appId" element={<RoleRoute role="CANDIDATE"><Layout><Result /></Layout></RoleRoute>} />

        <Route path="/interviewer" element={<RoleRoute role="INTERVIEWER"><Layout><AssignedList /></Layout></RoleRoute>} />
        <Route path="/interviewer/applications/:appId" element={<RoleRoute role="INTERVIEWER"><Layout><Review /></Layout></RoleRoute>} />

        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
