import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { useAuthStore } from "./store/authStore";
import LoginPage from "./pages/Login";
import Layout from "./components/Layout";
import DashboardPage from "./pages/Dashboard";
import DnsFilterPage from "./pages/DnsFilter";
import AppControlPage from "./pages/AppControl";
import SosHistoryPage from "./pages/SosHistory";
import FamilyPage from "./pages/Family";

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.accessToken);
  return token ? <>{children}</> : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <PrivateRoute>
              <Layout />
            </PrivateRoute>
          }
        >
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="dns" element={<DnsFilterPage />} />
          <Route path="apps" element={<AppControlPage />} />
          <Route path="sos" element={<SosHistoryPage />} />
          <Route path="family" element={<FamilyPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
