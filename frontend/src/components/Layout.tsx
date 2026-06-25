import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";
import { authApi } from "../api/client";
import toast from "react-hot-toast";

const navItems = [
  { to: "/dashboard", icon: "🗺️", label: "Карта" },
  { to: "/sos", icon: "🚨", label: "SOS история" },
  { to: "/dns", icon: "🔒", label: "DNS фильтр" },
  { to: "/apps", icon: "📱", label: "Приложения" },
  { to: "/family", icon: "👨‍👩‍👧", label: "Семья" },
];

export default function Layout() {
  const { user, refreshToken, logout } = useAuthStore();
  const navigate = useNavigate();

  async function handleLogout() {
    try {
      if (refreshToken) await authApi.logout(refreshToken);
    } catch {}
    logout();
    navigate("/login");
    toast.success("Выход выполнен");
  }

  return (
    <div className="flex h-screen overflow-hidden">
      {/* Sidebar */}
      <aside className="w-60 bg-white border-r border-gray-100 flex flex-col">
        {/* Logo */}
        <div className="flex items-center gap-3 px-5 py-5 border-b border-gray-100">
          <span className="text-2xl">🛡️</span>
          <div>
            <div className="font-bold text-gray-900 leading-tight">FamilyGuard</div>
            <div className="text-xs text-gray-400">Родительский контроль</div>
          </div>
        </div>

        {/* Nav */}
        <nav className="flex-1 p-3 space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `sidebar-item ${isActive ? "active" : ""}`
              }
            >
              <span className="text-lg">{item.icon}</span>
              <span className="text-sm">{item.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* User */}
        <div className="p-3 border-t border-gray-100">
          <div className="flex items-center gap-3 px-3 py-2 rounded-lg bg-gray-50">
            <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center text-blue-700 font-bold text-sm">
              {user?.first_name?.[0] || user?.email?.[0] || "?"}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium text-gray-800 truncate">
                {user?.first_name} {user?.last_name}
              </div>
              <div className="text-xs text-gray-400 truncate">{user?.email}</div>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="w-full mt-2 text-sm text-red-500 hover:text-red-700 
                       py-2 rounded-lg hover:bg-red-50 transition-colors"
          >
            Выйти
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto bg-gray-50">
        <Outlet />
      </main>
    </div>
  );
}
