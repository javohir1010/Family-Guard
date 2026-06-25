import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { dnsApi } from "../api/client";
import toast from "react-hot-toast";

export default function DnsFilterPage() {
  const qc = useQueryClient();
  const [newDomain, setNewDomain] = useState("");
  const [reason, setReason] = useState("");
  const [search, setSearch] = useState("");
  const [filterBlocked, setFilterBlocked] = useState<boolean | null>(null);

  const { data: blockedDomains = [] } = useQuery({
    queryKey: ["dns-blocked"],
    queryFn: () => dnsApi.blocked().then((r) => r.data.results ?? r.data),
  });

  const { data: queries = [] } = useQuery({
    queryKey: ["dns-queries", search, filterBlocked],
    queryFn: () =>
      dnsApi
        .queries({
          search: search || undefined,
          was_blocked: filterBlocked === null ? undefined : filterBlocked,
        })
        .then((r) => r.data.results ?? r.data),
  });

  const { data: categories = [] } = useQuery({
    queryKey: ["dns-categories"],
    queryFn: () => dnsApi.categories().then((r) => r.data.results ?? r.data),
  });

  const addBlockMutation = useMutation({
    mutationFn: () => dnsApi.addBlock(newDomain, reason),
    onSuccess: () => {
      toast.success(`Домен ${newDomain} заблокирован`);
      setNewDomain("");
      setReason("");
      qc.invalidateQueries({ queryKey: ["dns-blocked"] });
    },
    onError: (e: any) => toast.error(e.response?.data?.domain?.[0] || "Ошибка"),
  });

  const removeBlockMutation = useMutation({
    mutationFn: (id: string) => dnsApi.removeBlock(id),
    onSuccess: () => {
      toast.success("Домен разблокирован");
      qc.invalidateQueries({ queryKey: ["dns-blocked"] });
    },
  });

  const toggleCatMutation = useMutation({
    mutationFn: (id: number) => dnsApi.toggleCategory(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["dns-categories"] }),
  });

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-xl font-bold text-gray-900">DNS Фильтрация</h1>
        <p className="text-sm text-gray-500 mt-1">
          Управление блокировкой сайтов через VPN-сервис на устройстве ребёнка
        </p>
      </div>

      <div className="grid grid-cols-3 gap-6">
        {/* Add block */}
        <div className="card col-span-1">
          <h2 className="font-semibold text-gray-800 mb-4">Заблокировать домен</h2>
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Домен
              </label>
              <input
                type="text"
                value={newDomain}
                onChange={(e) => setNewDomain(e.target.value)}
                placeholder="example.com"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Причина (необяз.)
              </label>
              <input
                type="text"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Нежелательный контент"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <button
              onClick={() => addBlockMutation.mutate()}
              disabled={!newDomain || addBlockMutation.isPending}
              className="btn-danger w-full"
            >
              Заблокировать
            </button>
          </div>

          {/* Categories */}
          <div className="mt-6">
            <h3 className="font-medium text-gray-700 mb-3">Категории блокировки</h3>
            <div className="space-y-2">
              {categories.map((cat: any) => (
                <div
                  key={cat.id}
                  className="flex items-center justify-between p-2 rounded-lg bg-gray-50"
                >
                  <div>
                    <div className="text-sm font-medium text-gray-800 capitalize">
                      {cat.name}
                    </div>
                    <div className="text-xs text-gray-400">{cat.description}</div>
                  </div>
                  <button
                    onClick={() => toggleCatMutation.mutate(cat.id)}
                    className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                      cat.is_enabled ? "bg-blue-600" : "bg-gray-200"
                    }`}
                  >
                    <span
                      className={`inline-block h-4 w-4 transform rounded-full bg-white shadow 
                                  transition-transform ${cat.is_enabled ? "translate-x-4" : "translate-x-0.5"}`}
                    />
                  </button>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Right panels */}
        <div className="col-span-2 space-y-4">
          {/* Blocked domains list */}
          <div className="card">
            <h2 className="font-semibold text-gray-800 mb-3">
              Заблокированные домены ({blockedDomains.length})
            </h2>
            {blockedDomains.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-4">
                Нет заблокированных доменов
              </p>
            ) : (
              <div className="space-y-2 max-h-48 overflow-auto">
                {blockedDomains.map((d: any) => (
                  <div
                    key={d.id}
                    className="flex items-center justify-between py-2 px-3 rounded-lg 
                               bg-red-50 border border-red-100"
                  >
                    <div>
                      <span className="text-sm font-mono text-red-700">{d.domain}</span>
                      {d.reason && (
                        <span className="text-xs text-gray-400 ml-2">— {d.reason}</span>
                      )}
                    </div>
                    <button
                      onClick={() => removeBlockMutation.mutate(d.id)}
                      className="text-gray-400 hover:text-red-600 text-sm ml-2 flex-shrink-0"
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* DNS Query log */}
          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-800">Журнал DNS-запросов</h2>
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Поиск домена..."
                  className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm w-40
                             focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <select
                  value={String(filterBlocked)}
                  onChange={(e) => {
                    const v = e.target.value;
                    setFilterBlocked(v === "null" ? null : v === "true");
                  }}
                  className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm
                             focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="null">Все</option>
                  <option value="false">Разрешённые</option>
                  <option value="true">Заблокированные</option>
                </select>
              </div>
            </div>

            <div className="overflow-auto max-h-80">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="table-header">Домен</th>
                    <th className="table-header">Ребёнок</th>
                    <th className="table-header">Статус</th>
                    <th className="table-header">Время</th>
                    <th className="table-header"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {queries.map((q: any) => (
                    <tr key={q.id} className="hover:bg-gray-50">
                      <td className="table-cell font-mono text-xs">{q.domain}</td>
                      <td className="table-cell text-gray-500">{q.child_name}</td>
                      <td className="table-cell">
                        <span
                          className={q.was_blocked ? "badge-danger" : "badge-success"}
                        >
                          {q.was_blocked ? "Блок" : "ОК"}
                        </span>
                      </td>
                      <td className="table-cell text-gray-400 text-xs">
                        {new Date(q.timestamp).toLocaleString("ru")}
                      </td>
                      <td className="table-cell">
                        {!q.was_blocked && (
                          <button
                            onClick={() => {
                              setNewDomain(q.domain);
                              toast("Домен добавлен в форму блокировки");
                            }}
                            className="text-xs text-red-500 hover:text-red-700"
                          >
                            Заблок.
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {queries.length === 0 && (
                <p className="text-center text-sm text-gray-400 py-6">
                  Нет записей
                </p>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
