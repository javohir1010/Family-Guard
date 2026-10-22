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
  // Parents confirm login with a code sent by email
  const [challenge, setChallenge] = useState<{ id: string; hint: string } | null>(null);
  const [loginCode, setLoginCode] = useState("");
  const [inviteCode, setInviteCode] = useState("");
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
      if (data.otp_required) {
        setChallenge({ id: data.challenge_id, hint: data.email_hint });
        setLoginCode("");
        toast.success(`Код отправлен на ${data.email_hint}`);
      } else {
        await finishLogin(data.access, data.refresh);
      }
    } catch (err: any) {
      toast.error(err.response?.data?.detail || "Ошибка входа");
    } finally {
      setLoading(false);
    }
  }

  async function handleVerify(e: React.FormEvent) {
    e.preventDefault();
    if (!challenge) return;
    setLoading(true);
    try {
      const { data } = await authApi.verifyLogin(challenge.id, loginCode);
      await finishLogin(data.access, data.refresh);
    } catch (err: any) {
      toast.error(err.response?.data?.detail || "Неверный код");
    } finally {
      setLoading(false);
    }
  }

  async function finishLogin(access: string, refresh: string) {
    setTokens(access, refresh);
    const profile = await authApi.profile();
    setUser(profile.data);
    toast.success("Добро пожаловать!");
    navigate("/dashboard");
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const { data } = await authApi.register(regForm);
      setTokens(data.access, data.refresh);
      setUser(data.user);

      // Invited by another parent: ask to join their family (they approve it)
      if (inviteCode.trim()) {
        try {
          await familyApi.join(inviteCode.trim());
          toast.success("Заявка отправлена — второй родитель должен её подтвердить");
        } catch (err: any) {
          toast.error(err.response?.data?.error || "Неверный или истёкший код приглашения");
        }
        navigate("/family");
        return;
      }

      // Auto-create family for parent, then reload the profile so the stored
      // user has its family id (the dashboard map and WebSocket need it)
      if (regForm.role === "parent") {
        try {
          await familyApi.create(`Семья ${data.user.last_name}`);
          const profile = await authApi.profile();
          setUser(profile.data);
        } catch {
          toast.error("Не удалось создать семью — создайте её на странице «Семья»");
        }
      }

      toast.success("Регистрация успешна!");
      navigate("/dashboard");
    } catch (err: any) {
      const errors = err.response?.data;
      const msg =
        errors && typeof errors === "object"
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
            {tab === "login" && challenge ? (
              <form onSubmit={handleVerify} className="space-y-4">
                <p className="text-sm text-gray-600">
                  Мы отправили 6-значный код на <b>{challenge.hint}</b>. Введите его,
                  чтобы подтвердить вход.
                </p>
                <input
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  autoFocus
                  required
                  maxLength={6}
                  value={loginCode}
                  onChange={(e) => setLoginCode(e.target.value.replace(/\D/g, ""))}
                  className="w-full border border-gray-200 rounded-lg px-3 py-3 text-center
                             text-2xl font-mono tracking-[0.4em]
                             focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="000000"
                />
                <button
                  type="submit"
                  disabled={loading || loginCode.length !== 6}
                  className="btn-primary w-full py-3"
                >
                  {loading ? "Проверяем..." : "Подтвердить"}
                </button>
                <button
                  type="button"
                  onClick={() => setChallenge(null)}
                  className="btn-ghost w-full"
                >
                  Назад
                </button>
              </form>
            ) : tab === "login" ? (
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
                {regForm.role === "parent" && (
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Код приглашения от второго родителя (необязательно)
                    </label>
                    <input
                      inputMode="numeric"
                      maxLength={6}
                      value={inviteCode}
                      onChange={(e) => setInviteCode(e.target.value.replace(/\D/g, ""))}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm
                                 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      placeholder="Пусто — будет создана новая семья"
                    />
                  </div>
                )}
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
