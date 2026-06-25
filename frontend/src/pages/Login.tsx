import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authApi, familyApi } from "../api/client";
import { useAuthStore } from "../store/authStore";
import toast from "react-hot-toast";

export default function LoginPage() {
  const navigate = useNavigate();
  const { setTokens, setUser } = useAuthStore();
  const [tab, setTab] = useState<"login" | "register">("login");
  const [loading, setLoading] = useState(false);

  const [loginForm, setLoginForm] = useState({ email: "", password: "" });
  const [regForm, setRegForm] = useState({
    email: "",
    username: "",
    first_name: "",
    last_name: "",
    password: "",
    password2: "",
    role: "parent",
    phone_number: "",
  });

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const { data } = await authApi.login(loginForm.email, loginForm.password);
      setTokens(data.access, data.refresh);
      const profile = await authApi.profile();
      setUser(profile.data);
      toast.success("Добро пожаловать!");
      navigate("/dashboard");
    } catch (err: any) {
      toast.error(err.response?.data?.detail || "Ошибка входа");
    } finally {
      setLoading(false);
    }
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const { data } = await authApi.register(regForm);
      setTokens(data.access, data.refresh);
      setUser(data.user);

      // Auto-create family for parent
      if (regForm.role === "parent") {
        try {
          await familyApi.create(`Семья ${data.user.last_name}`);
        } catch {}
      }

      toast.success("Регистрация успешна!");
      navigate("/dashboard");
    } catch (err: any) {
      const errors = err.response?.data;
      const msg = errors
        ? Object.values(errors).flat().join(", ")
        : "Ошибка регистрации";
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-600 to-blue-800 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-white rounded-2xl shadow-lg mb-4">
            <span className="text-3xl">🛡️</span>
          </div>
          <h1 className="text-3xl font-bold text-white">FamilyGuard</h1>
          <p className="text-blue-200 mt-1">Система родительского контроля</p>
        </div>

        <div className="bg-white rounded-2xl shadow-xl overflow-hidden">
          {/* Tabs */}
          <div className="flex border-b border-gray-100">
            {(["login", "register"] as const).map((t) => (
              <button
                key={t}
                onClick={() => setTab(t)}
                className={`flex-1 py-4 text-sm font-semibold transition-colors ${
                  tab === t
                    ? "text-blue-600 border-b-2 border-blue-600"
                    : "text-gray-500 hover:text-gray-700"
                }`}
              >
                {t === "login" ? "Войти" : "Регистрация"}
              </button>
            ))}
          </div>

          <div className="p-6">
            {tab === "login" ? (
              <form onSubmit={handleLogin} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Email
                  </label>
                  <input
                    type="email"
                    required
                    value={loginForm.email}
                    onChange={(e) =>
                      setLoginForm((f) => ({ ...f, email: e.target.value }))
                    }
                    className="w-full border border-gray-200 rounded-lg px-3 py-2.5 
                               focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="parent@example.com"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Пароль
                  </label>
                  <input
                    type="password"
                    required
                    value={loginForm.password}
                    onChange={(e) =>
                      setLoginForm((f) => ({ ...f, password: e.target.value }))
                    }
                    className="w-full border border-gray-200 rounded-lg px-3 py-2.5
                               focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="••••••••"
                  />
                </div>
                <button
                  type="submit"
                  disabled={loading}
                  className="btn-primary w-full py-3"
                >
                  {loading ? "Входим..." : "Войти"}
                </button>
              </form>
            ) : (
              <form onSubmit={handleRegister} className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Имя
                    </label>
                    <input
                      type="text"
                      required
                      value={regForm.first_name}
                      onChange={(e) =>
                        setRegForm((f) => ({ ...f, first_name: e.target.value }))
                      }
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm
                                 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Фамилия
                    </label>
                    <input
                      type="text"
                      required
                      value={regForm.last_name}
                      onChange={(e) =>
                        setRegForm((f) => ({ ...f, last_name: e.target.value }))
                      }
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm
                                 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Email
                  </label>
                  <input
                    type="email"
                    required
                    value={regForm.email}
                    onChange={(e) =>
                      setRegForm((f) => ({ ...f, email: e.target.value }))
                    }
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm
                               focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Имя пользователя
                  </label>
                  <input
                    type="text"
                    required
                    value={regForm.username}
                    onChange={(e) =>
                      setRegForm((f) => ({ ...f, username: e.target.value }))
                    }
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm
                               focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Роль
                  </label>
                  <select
                    value={regForm.role}
                    onChange={(e) =>
                      setRegForm((f) => ({ ...f, role: e.target.value }))
                    }
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm
                               focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="parent">Родитель</option>
                    <option value="child">Ребёнок</option>
                  </select>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Пароль
                    </label>
                    <input
                      type="password"
                      required
                      value={regForm.password}
                      onChange={(e) =>
                        setRegForm((f) => ({ ...f, password: e.target.value }))
                      }
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm
                                 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Повтор
                    </label>
                    <input
                      type="password"
                      required
                      value={regForm.password2}
                      onChange={(e) =>
                        setRegForm((f) => ({ ...f, password2: e.target.value }))
                      }
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm
                                 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>
                <button
                  type="submit"
                  disabled={loading}
                  className="btn-primary w-full py-3 mt-2"
                >
                  {loading ? "Создаём аккаунт..." : "Создать аккаунт"}
                </button>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
