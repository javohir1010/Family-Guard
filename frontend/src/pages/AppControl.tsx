import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { appsApi, familyApi } from "../api/client";
import toast from "react-hot-toast";

export default function AppControlPage() {
  const qc = useQueryClient();
  const [selectedChild, setSelectedChild] = useState<string>("");
  const [ruleForm, setRuleForm] = useState({
    child: "",
    package_name: "",
    app_name: "",
    rule_type: "block",
    daily_limit_minutes: 60,
  });
  const [showAddRule, setShowAddRule] = useState(false);

  const { data: members = [] } = useQuery({
    queryKey: ["family-members"],
    queryFn: () => familyApi.members().then((r) => r.data),
  });

  const children = members.filter((m: any) => m.role === "child");

  const activeChild = selectedChild || children[0]?.id || "";

  const { data: usageData = [] } = useQuery({
    queryKey: ["app-usage", activeChild],
    queryFn: () =>
      activeChild
        ? appsApi.usage(activeChild).then((r) => r.data.results ?? r.data)
        : [],
    enabled: !!activeChild,
  });

  const { data: rules = [] } = useQuery({
    queryKey: ["app-rules"],
    queryFn: () => appsApi.rules().then((r) => r.data.results ?? r.data),
  });

  const { data: permAlerts = [] } = useQuery({
    queryKey: ["perm-alerts"],
    queryFn: () => appsApi.permissionAlerts().then((r) => r.data.results ?? r.data),
  });

  const createRuleMutation = useMutation({
    mutationFn: (data: object) => appsApi.createRule(data),
    onSuccess: () => {
      toast.success("Правило создано");
      qc.invalidateQueries({ queryKey: ["app-rules"] });
      setShowAddRule(false);
    },
    onError: (e: any) => toast.error(JSON.stringify(e.response?.data)),
  });

  const deleteRuleMutation = useMutation({
    mutationFn: (id: string) => appsApi.deleteRule(id),
    onSuccess: () => {
      toast.success("Правило удалено");
      qc.invalidateQueries({ queryKey: ["app-rules"] });
    },
  });

  function formatMinutes(secs: number) {
    const m = Math.round(secs / 60);
    return m >= 60 ? `${Math.floor(m / 60)}ч ${m % 60}м` : `${m}м`;
  }

  const totalScreenTime = usageData.reduce(
    (acc: number, a: any) => acc + a.duration_seconds,
    0
  );

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Контроль приложений</h1>
          <p className="text-sm text-gray-500 mt-1">
            Статистика использования и ограничения
          </p>
        </div>
        <button
          onClick={() => setShowAddRule(true)}
          className="btn-primary"
        >
          + Добавить правило
        </button>
      </div>

      {/* Child selector */}
      {children.length > 1 && (
        <div className="flex gap-2">
          {children.map((c: any) => (
            <button
              key={c.id}
              onClick={() => setSelectedChild(c.id)}
              className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${
                activeChild === c.id
                  ? "bg-blue-600 text-white"
                  : "bg-white text-gray-600 border border-gray-200 hover:bg-gray-50"
              }`}
            >
              👶 {c.first_name} {c.last_name}
            </button>
          ))}
        </div>
      )}

      <div className="grid grid-cols-3 gap-6">
        {/* Screen time summary */}
        <div className="card">
          <div className="text-center">
            <div className="text-3xl font-bold text-blue-600">
              {formatMinutes(totalScreenTime)}
            </div>
            <div className="text-sm text-gray-500 mt-1">Экранное время сегодня</div>
          </div>
          <div className="mt-4 space-y-2">
            {usageData.slice(0, 5).map((app: any) => {
              const pct = totalScreenTime
                ? Math.round((app.duration_seconds / totalScreenTime) * 100)
                : 0;
              return (
                <div key={app.id}>
                  <div className="flex justify-between text-xs text-gray-600 mb-0.5">
                    <span className="truncate max-w-[120px]">{app.app_name}</span>
                    <span>{formatMinutes(app.duration_seconds)}</span>
                  </div>
                  <div className="h-1.5 bg-gray-100 rounded-full">
                    <div
                      className="h-1.5 bg-blue-500 rounded-full"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Full usage list */}
        <div className="card col-span-2">
          <h2 className="font-semibold text-gray-800 mb-3">
            Приложения сегодня ({usageData.length})
          </h2>
          <div className="overflow-auto max-h-72">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="table-header">Приложение</th>
                  <th className="table-header">Время</th>
                  <th className="table-header">Правило</th>
                  <th className="table-header"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {usageData.map((app: any) => {
                  const rule = rules.find(
                    (r: any) => r.package_name === app.package_name
                  );
                  return (
                    <tr key={app.id} className="hover:bg-gray-50">
                      <td className="table-cell">
                        <div className="font-medium">{app.app_name}</div>
                        <div className="text-xs text-gray-400 font-mono">
                          {app.package_name}
                        </div>
                      </td>
                      <td className="table-cell">
                        {formatMinutes(app.duration_seconds)}
                      </td>
                      <td className="table-cell">
                        {rule ? (
                          <span
                            className={
                              rule.rule_type === "block"
                                ? "badge-danger"
                                : "badge-warning"
                            }
                          >
                            {rule.rule_type === "block"
                              ? "Блок"
                              : `Лимит ${rule.daily_limit_minutes}м`}
                          </span>
                        ) : (
                          <span className="badge-success">Разрешено</span>
                        )}
                      </td>
                      <td className="table-cell">
                        <button
                          onClick={() => {
                            setRuleForm((f) => ({
                              ...f,
                              package_name: app.package_name,
                              app_name: app.app_name,
                              child: activeChild,
                            }));
                            setShowAddRule(true);
                          }}
                          className="text-xs text-blue-500 hover:text-blue-700"
                        >
                          Настроить
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Permission alerts */}
      {permAlerts.length > 0 && (
        <div className="card border-yellow-200 bg-yellow-50">
          <h2 className="font-semibold text-yellow-800 mb-3">
            ⚠️ Изменения разрешений
          </h2>
          <div className="space-y-2">
            {permAlerts.slice(0, 5).map((a: any) => (
              <div
                key={a.id}
                className="flex items-center justify-between bg-white 
                           rounded-lg p-3 shadow-sm"
              >
                <div>
                  <div className="text-sm font-medium text-gray-800">
                    {a.child_name} — {a.permission_display}
                  </div>
                  <div className="text-xs text-gray-400">
                    {new Date(a.timestamp).toLocaleString("ru")}
                  </div>
                </div>
                <span className={a.was_granted ? "badge-success" : "badge-danger"}>
                  {a.was_granted ? "Выдано" : "Отозвано"}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Add rule modal */}
      {showAddRule && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-6 w-full max-w-md shadow-2xl">
            <h2 className="text-lg font-bold text-gray-900 mb-4">
              Новое правило для приложения
            </h2>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Ребёнок
                </label>
                <select
                  value={ruleForm.child || activeChild}
                  onChange={(e) =>
                    setRuleForm((f) => ({ ...f, child: e.target.value }))
                  }
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
                >
                  {children.map((c: any) => (
                    <option key={c.id} value={c.id}>
                      {c.first_name} {c.last_name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Package Name
                </label>
                <input
                  value={ruleForm.package_name}
                  onChange={(e) =>
                    setRuleForm((f) => ({ ...f, package_name: e.target.value }))
                  }
                  placeholder="com.example.app"
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm font-mono"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Название приложения
                </label>
                <input
                  value={ruleForm.app_name}
                  onChange={(e) =>
                    setRuleForm((f) => ({ ...f, app_name: e.target.value }))
                  }
                  placeholder="YouTube"
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Тип правила
                </label>
                <select
                  value={ruleForm.rule_type}
                  onChange={(e) =>
                    setRuleForm((f) => ({ ...f, rule_type: e.target.value }))
                  }
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
                >
                  <option value="block">Полная блокировка</option>
                  <option value="limit">Дневной лимит (минуты)</option>
                </select>
              </div>
              {ruleForm.rule_type === "limit" && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Лимит (минут в день)
                  </label>
                  <input
                    type="number"
                    value={ruleForm.daily_limit_minutes}
                    onChange={(e) =>
                      setRuleForm((f) => ({
                        ...f,
                        daily_limit_minutes: Number(e.target.value),
                      }))
                    }
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
              )}
            </div>
            <div className="flex gap-3 mt-5">
              <button
                onClick={() => setShowAddRule(false)}
                className="btn-ghost flex-1"
              >
                Отмена
              </button>
              <button
                onClick={() =>
                  createRuleMutation.mutate({
                    ...ruleForm,
                    child: ruleForm.child || activeChild,
                    daily_limit_minutes:
                      ruleForm.rule_type === "limit"
                        ? ruleForm.daily_limit_minutes
                        : null,
                  })
                }
                disabled={createRuleMutation.isPending}
                className="btn-primary flex-1"
              >
                Создать
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
