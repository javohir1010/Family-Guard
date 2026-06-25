import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { sosApi } from "../api/client";
import toast from "react-hot-toast";

export default function SosHistoryPage() {
  const qc = useQueryClient();

  const { data: events = [] } = useQuery({
    queryKey: ["sos-history"],
    queryFn: () => sosApi.history().then((r) => r.data.results ?? r.data),
    refetchInterval: 30_000,
  });

  const resolveMutation = useMutation({
    mutationFn: (id: string) => sosApi.resolve(id),
    onSuccess: () => {
      toast.success("SOS отмечен как решённый");
      qc.invalidateQueries({ queryKey: ["sos-history"] });
    },
  });

  const statusColors: Record<string, string> = {
    pending: "badge-warning",
    notified: "badge-info",
    emergency_sent: "badge-danger",
    resolved: "badge-success",
  };

  const statusLabels: Record<string, string> = {
    pending: "Ожидание",
    notified: "Уведомлён",
    emergency_sent: "SOS отправлен",
    resolved: "Решено",
  };

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-gray-900">История SOS сигналов</h1>
        <p className="text-sm text-gray-500 mt-1">
          Все экстренные сигналы от устройств детей
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-4 mb-6">
        {[
          { label: "Всего", value: events.length, color: "text-gray-800" },
          {
            label: "Экстренных",
            value: events.filter((e: any) => e.sos_type === "emergency").length,
            color: "text-red-600",
          },
          {
            label: "Нерешённых",
            value: events.filter((e: any) => e.status !== "resolved").length,
            color: "text-yellow-600",
          },
          {
            label: "Решённых",
            value: events.filter((e: any) => e.status === "resolved").length,
            color: "text-green-600",
          },
        ].map((s) => (
          <div key={s.label} className="card text-center">
            <div className={`text-3xl font-bold ${s.color}`}>{s.value}</div>
            <div className="text-sm text-gray-500 mt-1">{s.label}</div>
          </div>
        ))}
      </div>

      {/* Events list */}
      <div className="card">
        {events.length === 0 ? (
          <div className="text-center py-12 text-gray-400">
            <div className="text-5xl mb-3">🟢</div>
            <div className="font-medium">Нет SOS сигналов</div>
            <div className="text-sm mt-1">Всё спокойно</div>
          </div>
        ) : (
          <div className="space-y-3">
            {events.map((event: any) => (
              <div
                key={event.id}
                className={`flex items-start justify-between p-4 rounded-xl border ${
                  event.sos_type === "emergency"
                    ? "border-red-200 bg-red-50"
                    : "border-yellow-200 bg-yellow-50"
                }`}
              >
                <div className="flex items-start gap-3">
                  <div className="text-2xl mt-0.5">
                    {event.sos_type === "emergency" ? "🚨" : "⚠️"}
                  </div>
                  <div>
                    <div className="font-semibold text-gray-900">
                      {event.sos_type === "emergency"
                        ? "ЭКСТРЕННЫЙ СИГНАЛ"
                        : "Сигнал о помощи"}{" "}
                      — {event.child_name}
                    </div>
                    <div className="text-sm text-gray-600 mt-0.5">
                      Устройство: {event.child_device || "—"} &nbsp;·&nbsp;{" "}
                      {event.press_count} нажатий кнопки SOS
                    </div>
                    {event.latitude && event.longitude && (
                      <a
                        href={`https://maps.google.com/?q=${event.latitude},${event.longitude}`}
                        target="_blank"
                        rel="noreferrer"
                        className="text-sm text-blue-600 hover:underline mt-1 inline-block"
                      >
                        📍 Открыть на карте
                      </a>
                    )}
                    <div className="text-xs text-gray-400 mt-1">
                      {new Date(event.created_at).toLocaleString("ru")}
                    </div>
                  </div>
                </div>
                <div className="flex flex-col items-end gap-2">
                  <span className={statusColors[event.status]}>
                    {statusLabels[event.status]}
                  </span>
                  {event.status !== "resolved" && (
                    <button
                      onClick={() => resolveMutation.mutate(event.id)}
                      disabled={resolveMutation.isPending}
                      className="text-xs text-green-600 hover:text-green-800 
                                 border border-green-300 rounded-md px-2 py-1"
                    >
                      Отметить решённым
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
