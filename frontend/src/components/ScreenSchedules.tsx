import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { appsApi } from "../api/client";
import toast from "react-hot-toast";

// Same bit order as ScreenSchedule.days_bitmask on the backend: Mon=1 … Sun=64
const DAYS = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];

const EMPTY_FORM = {
  name: "Ночь",
  days_bitmask: 127,
  start_time: "21:00",
  end_time: "07:00",
  is_active: true,
};

function errorText(e: any) {
  const data = e.response?.data;
  if (data && typeof data === "object") return Object.values(data).flat().join(", ");
  return "Ошибка";
}

function hhmm(time: string) {
  return time?.slice(0, 5) ?? "";
}

function daysLabel(mask: number) {
  if (mask === 127) return "Каждый день";
  if (mask === 31) return "Будни";
  if (mask === 96) return "Выходные";
  return DAYS.filter((_, i) => mask & (1 << i)).join(", ");
}

export default function ScreenSchedules({ childId }: { childId: string }) {
  const qc = useQueryClient();
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);

  const { data: schedules = [] } = useQuery({
    queryKey: ["schedules", childId],
    queryFn: () => appsApi.schedules(childId).then((r) => r.data.results ?? r.data),
  });

  const refresh = () => qc.invalidateQueries({ queryKey: ["schedules", childId] });

  const saveMutation = useMutation({
    mutationFn: (data: typeof EMPTY_FORM) =>
      editingId
        ? appsApi.updateSchedule(editingId, data)
        : appsApi.createSchedule({ ...data, child: childId }),
    onSuccess: () => {
      toast.success(editingId ? "Расписание обновлено" : "Расписание создано");
      refresh();
      setShowModal(false);
    },
    onError: (e: any) => toast.error(errorText(e)),
  });

  const toggleMutation = useMutation({
    mutationFn: (s: any) => appsApi.updateSchedule(s.id, { is_active: !s.is_active }),
    onSuccess: refresh,
    onError: (e: any) => toast.error(errorText(e)),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => appsApi.deleteSchedule(id),
    onSuccess: () => {
      toast.success("Расписание удалено");
      refresh();
    },
  });

  function openModal(s?: any) {
    setEditingId(s?.id ?? null);
    setForm(
      s
        ? {
            name: s.name,
            days_bitmask: s.days_bitmask,
            start_time: hhmm(s.start_time),
            end_time: hhmm(s.end_time),
            is_active: s.is_active,
          }
        : EMPTY_FORM
    );
    setShowModal(true);
  }

  function toggleDay(i: number) {
    setForm((f) => ({ ...f, days_bitmask: f.days_bitmask ^ (1 << i) }));
  }

  return (
    <div className="card">
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="font-semibold text-gray-800">Расписание экранного времени</h2>
          <p className="text-xs text-gray-500 mt-0.5">
            В эти часы телефон ребёнка блокируется (SOS и звонки остаются доступны)
          </p>
        </div>
        <button onClick={() => openModal()} className="btn-primary">
          + Расписание
        </button>
      </div>

      {schedules.length === 0 ? (
        <p className="text-sm text-gray-400">Расписаний пока нет</p>
      ) : (
        <div className="space-y-2">
          {schedules.map((s: any) => (
            <div
              key={s.id}
              className="flex items-center justify-between rounded-lg border border-gray-100 p-3"
            >
              <div>
                <div className="text-sm font-medium text-gray-800">
                  {s.name}{" "}
                  <span className="text-gray-500 font-normal">
                    {hhmm(s.start_time)}–{hhmm(s.end_time)}
                  </span>
                </div>
                <div className="text-xs text-gray-400">{daysLabel(s.days_bitmask)}</div>
              </div>
              <div className="flex items-center gap-3">
                <button
                  onClick={() => toggleMutation.mutate(s)}
                  className={s.is_active ? "badge-success" : "badge-warning"}
                >
                  {s.is_active ? "Включено" : "Выключено"}
                </button>
                <button
                  onClick={() => openModal(s)}
                  className="text-xs text-blue-500 hover:text-blue-700"
                >
                  Изменить
                </button>
                <button
                  onClick={() => deleteMutation.mutate(s.id)}
                  className="text-xs text-red-400 hover:text-red-600"
                >
                  Удалить
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-6 w-full max-w-md shadow-2xl">
            <h2 className="text-lg font-bold text-gray-900 mb-4">
              {editingId ? "Изменить расписание" : "Новое расписание"}
            </h2>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Название
                </label>
                <input
                  value={form.name}
                  maxLength={100}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Дни недели
                </label>
                <div className="flex gap-1">
                  {DAYS.map((d, i) => (
                    <button
                      key={d}
                      type="button"
                      onClick={() => toggleDay(i)}
                      className={`flex-1 py-1.5 rounded-lg text-sm font-medium ${
                        form.days_bitmask & (1 << i)
                          ? "bg-blue-600 text-white"
                          : "bg-gray-100 text-gray-500"
                      }`}
                    >
                      {d}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex gap-3">
                <div className="flex-1">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    С
                  </label>
                  <input
                    type="time"
                    value={form.start_time}
                    onChange={(e) => setForm((f) => ({ ...f, start_time: e.target.value }))}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
                <div className="flex-1">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    До
                  </label>
                  <input
                    type="time"
                    value={form.end_time}
                    onChange={(e) => setForm((f) => ({ ...f, end_time: e.target.value }))}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
                  />
                </div>
              </div>
              {form.start_time > form.end_time && (
                <p className="text-xs text-gray-500">
                  Интервал переходит через полночь: до {form.end_time} следующего дня
                </p>
              )}
            </div>
            <div className="flex gap-3 mt-5">
              <button onClick={() => setShowModal(false)} className="btn-ghost flex-1">
                Отмена
              </button>
              <button
                onClick={() => saveMutation.mutate(form)}
                disabled={
                  !form.name ||
                  !form.days_bitmask ||
                  !form.start_time ||
                  !form.end_time ||
                  saveMutation.isPending
                }
                className="btn-primary flex-1"
              >
                {editingId ? "Сохранить" : "Создать"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
