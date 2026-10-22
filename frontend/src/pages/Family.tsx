import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { authApi, familyApi } from "../api/client";
import { useAuthStore } from "../store/authStore";
import toast from "react-hot-toast";

export default function FamilyPage() {
  const qc = useQueryClient();
  const [showInvite, setShowInvite] = useState(false);
  const [invite, setInvite] = useState<{ code: string; role: string; expires_at: string } | null>(null);
  const [joinCode, setJoinCode] = useState("");
  const [joinRequestId, setJoinRequestId] = useState<string | null>(null);
  const [familyName, setFamilyName] = useState("");
  const { user, setUser } = useAuthStore();
  const hasFamily = !!user?.family;

  const { data: family } = useQuery({
    queryKey: ["family-detail"],
    queryFn: () => familyApi.detail().then((r) => r.data),
    enabled: hasFamily,
  });

  const { data: members = [] } = useQuery({
    queryKey: ["family-members"],
    queryFn: () => familyApi.members().then((r) => r.data),
    enabled: hasFamily,
  });

  // Join requests wait for a parent; poll so they show up quickly
  const { data: requests = [] } = useQuery({
    queryKey: ["family-requests"],
    queryFn: () => familyApi.requests().then((r) => r.data),
    enabled: hasFamily && user?.role === "parent",
    refetchInterval: 5000,
  });

  const decideMutation = useMutation({
    mutationFn: ({ id, approve }: { id: string; approve: boolean }) =>
      approve ? familyApi.approveRequest(id) : familyApi.rejectRequest(id),
    onSuccess: (_res, { approve }) => {
      toast.success(approve ? "Подтверждено" : "Отклонено");
      qc.invalidateQueries({ queryKey: ["family-requests"] });
      qc.invalidateQueries({ queryKey: ["family-members"] });
    },
    onError: (e: any) => toast.error(e.response?.data?.error || "Заявка устарела"),
  });

  // Without a family: join one by a code from another parent, then wait
  const joinMutation = useMutation({
    mutationFn: () => familyApi.join(joinCode.trim()),
    onSuccess: (res) => {
      setJoinRequestId(res.data.request.id);
      toast.success("Заявка отправлена");
    },
    onError: (e: any) => toast.error(e.response?.data?.error || "Неверный или истёкший код"),
  });

  const { data: joinRequest } = useQuery({
    queryKey: ["join-request", joinRequestId],
    queryFn: async () => {
      const r = (await familyApi.requestStatus(joinRequestId!)).data;
      if (r.status === "approved") {
        const profile = await authApi.profile();
        setUser(profile.data);
        toast.success("Вас добавили в семью");
        qc.invalidateQueries();
      }
      return r;
    },
    enabled: !hasFamily && !!joinRequestId,
    refetchInterval: (q) => (q.state.data?.status === "pending" ? 3000 : false),
  });

  const createFamilyMutation = useMutation({
    mutationFn: () => familyApi.create(familyName.trim()),
    onSuccess: async () => {
      const profile = await authApi.profile();
      setUser(profile.data);
      toast.success("Семья создана");
      qc.invalidateQueries();
    },
    onError: (e: any) => toast.error(e.response?.data?.error || "Ошибка создания семьи"),
  });

  const generateInviteMutation = useMutation({
    mutationFn: (role: "child" | "parent") => familyApi.generateInvite(role),
    onSuccess: (res) => {
      setInvite(res.data);
      setShowInvite(true);
    },
    onError: (e: any) => toast.error(e.response?.data?.error || "Ошибка генерации кода"),
  });

  const unlinkMutation = useMutation({
    mutationFn: (id: string) => familyApi.unlinkDevice(id),
    onSuccess: () => {
      toast.success("Устройство отвязано");
      qc.invalidateQueries({ queryKey: ["family-members"] });
    },
    onError: () => toast.error("Не удалось отвязать устройство"),
  });

  const removeMemberMutation = useMutation({
    mutationFn: (id: string) => familyApi.removeMember(id),
    onSuccess: () => {
      toast.success("Участник удалён");
      qc.invalidateQueries({ queryKey: ["family-members"] });
    },
    onError: () => toast.error("Не удалось удалить участника"),
  });

  if (!hasFamily) {
    return (
      <div className="p-6">
        <div className="card max-w-md">
          <h1 className="text-xl font-bold text-gray-900 mb-2">Создайте семью</h1>
          <p className="text-sm text-gray-500 mb-4">
            Семья нужна, чтобы приглашать детей и видеть их на карте.
          </p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              createFamilyMutation.mutate();
            }}
            className="flex gap-2"
          >
            <input
              value={familyName}
              onChange={(e) => setFamilyName(e.target.value)}
              placeholder="Название семьи"
              required
              className="flex-1 border border-gray-200 rounded-lg px-3 py-2 text-sm
                         focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              type="submit"
              disabled={!familyName.trim() || createFamilyMutation.isPending}
              className="btn-primary"
            >
              Создать
            </button>
          </form>
        </div>

        <div className="card max-w-md mt-6">
          <h2 className="text-lg font-bold text-gray-900 mb-2">Или присоединитесь к семье</h2>
          <p className="text-sm text-gray-500 mb-4">
            Введите код приглашения от второго родителя. Он должен будет подтвердить заявку.
          </p>
          {joinRequestId && joinRequest?.status !== "rejected" && joinRequest?.status !== "expired" ? (
            <div className="text-sm text-blue-700 bg-blue-50 rounded-lg p-3">
              ⏳ Ждём подтверждения от родителя…
            </div>
          ) : (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                joinMutation.mutate();
              }}
              className="flex gap-2"
            >
              <input
                value={joinCode}
                onChange={(e) => setJoinCode(e.target.value.replace(/\D/g, ""))}
                inputMode="numeric"
                maxLength={6}
                placeholder="000000"
                className="flex-1 border border-gray-200 rounded-lg px-3 py-2 text-sm font-mono
                           focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <button
                type="submit"
                disabled={joinCode.length !== 6 || joinMutation.isPending}
                className="btn-primary"
              >
                Отправить
              </button>
            </form>
          )}
          {joinRequest?.status === "rejected" && (
            <p className="text-sm text-red-500 mt-2">Заявку отклонили. Попросите новый код.</p>
          )}
          {joinRequest?.status === "expired" && (
            <p className="text-sm text-red-500 mt-2">Заявка устарела. Попросите новый код.</p>
          )}
        </div>
      </div>
    );
  }

  const parents = members.filter((m: any) => m.role === "parent");
  const children = members.filter((m: any) => m.role === "child");

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">
            {family?.name || "Семья"}
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            {members.length} участников
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => generateInviteMutation.mutate("parent")}
            disabled={generateInviteMutation.isPending}
            className="btn-ghost"
          >
            + Пригласить родителя
          </button>
          <button
            onClick={() => generateInviteMutation.mutate("child")}
            disabled={generateInviteMutation.isPending}
            className="btn-primary"
          >
            + Пригласить ребёнка
          </button>
        </div>
      </div>

      {requests.length > 0 && (
        <div className="card border-2 border-amber-200 bg-amber-50">
          <h2 className="font-semibold text-gray-800 mb-3">🔔 Ждут подтверждения</h2>
          <div className="space-y-2">
            {requests.map((r: any) => (
              <div key={r.id} className="flex items-center gap-3 bg-white rounded-xl p-3">
                <div className="flex-1 min-w-0 text-sm">
                  <div className="font-medium text-gray-900 truncate">
                    {r.user.first_name} {r.user.last_name}{" "}
                    <span className="text-gray-400 font-normal">{r.user.email}</span>
                  </div>
                  <div className="text-gray-500">
                    {r.role === "parent"
                        ? "хочет присоединиться как родитель"
                        : "хочет присоединиться как ребёнок"}
                    {r.device_name && ` · 📱 ${r.device_name}`}
                  </div>
                </div>
                <button
                  onClick={() => decideMutation.mutate({ id: r.id, approve: false })}
                  disabled={decideMutation.isPending}
                  className="btn-ghost text-red-500"
                >
                  Отклонить
                </button>
                <button
                  onClick={() => decideMutation.mutate({ id: r.id, approve: true })}
                  disabled={decideMutation.isPending}
                  className="btn-primary"
                >
                  Подтвердить
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-6">
        {/* Parents */}
        <div className="card">
          <h2 className="font-semibold text-gray-800 mb-4">
            👨 Родители ({parents.length})
          </h2>
          <div className="space-y-3">
            {parents.map((m: any) => (
              <MemberCard key={m.id} member={m} canRemove={false} />
            ))}
          </div>
        </div>

        {/* Children */}
        <div className="card">
          <h2 className="font-semibold text-gray-800 mb-4">
            👶 Дети ({children.length})
          </h2>
          {children.length === 0 ? (
            <div className="text-center py-8 text-gray-400">
              <div className="text-4xl mb-2">👶</div>
              <div className="text-sm">
                Нет добавленных детей.
                <br />
                Нажмите «Пригласить ребёнка»
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              {children.map((m: any) => (
                <MemberCard
                  key={m.id}
                  member={m}
                  canRemove
                  onUnlink={() => {
                    if (confirm(`Выйти из аккаунта на телефоне ${m.first_name || m.email}? Ребёнок останется в семье.`)) {
                      unlinkMutation.mutate(m.id);
                    }
                  }}
                  onRemove={() => {
                    if (confirm(`Удалить ${m.first_name || m.email} из семьи?`)) {
                      removeMemberMutation.mutate(m.id);
                    }
                  }}
                />
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Invite modal */}
      {showInvite && invite && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-8 w-full max-w-sm shadow-2xl text-center">
            <div className="text-4xl mb-4">🔑</div>
            <h2 className="text-xl font-bold text-gray-900 mb-2">
              Инвайт-код
            </h2>
            <p className="text-sm text-gray-500 mb-6">
              {invite.role === "parent"
                ? "Передайте код второму родителю: он вводит его при регистрации или на странице «Семья»."
                : "Передайте код ребёнку: он вводит его в приложении на телефоне."}
              <br />
              Действует до{" "}
              {new Date(invite.expires_at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
              . Вход подтвердите здесь, когда придёт заявка.
            </p>
            <div className="bg-blue-50 border-2 border-blue-200 rounded-xl py-4 px-6 mb-6">
              <span className="text-4xl font-mono font-bold tracking-[0.3em] text-blue-700">
                {invite.code}
              </span>
            </div>
            <button
              onClick={() => {
                navigator.clipboard.writeText(invite.code);
                toast.success("Скопировано!");
              }}
              className="btn-primary w-full mb-3"
            >
              Скопировать код
            </button>
            <button
              onClick={() => setShowInvite(false)}
              className="btn-ghost w-full"
            >
              Закрыть
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function MemberCard({
  member,
  canRemove,
  onRemove,
  onUnlink,
}: {
  member: any;
  canRemove: boolean;
  onRemove?: () => void;
  onUnlink?: () => void;
}) {
  return (
    <div className="flex items-center gap-3 p-3 rounded-xl bg-gray-50 hover:bg-gray-100 transition-colors">
      <div
        className={`w-10 h-10 rounded-full flex items-center justify-center 
                    text-sm font-bold shadow-sm
                    ${member.role === "child" ? "bg-green-100 text-green-700" : "bg-blue-100 text-blue-700"}`}
      >
        {member.first_name?.[0] || member.email?.[0] || "?"}
      </div>
      <div className="flex-1 min-w-0">
        <div className="font-medium text-gray-900 truncate">
          {member.first_name} {member.last_name}
        </div>
        <div className="text-xs text-gray-400 truncate">{member.email}</div>
        {member.device_name && (
          <div className="text-xs text-gray-400">📱 {member.device_name}</div>
        )}
      </div>
      <div className="flex flex-col items-end gap-1">
        <div
          className={`flex items-center gap-1 text-xs ${
            member.is_online ? "text-green-600" : "text-gray-400"
          }`}
        >
          <span
            className={`w-2 h-2 rounded-full ${
              member.is_online ? "bg-green-500" : "bg-gray-300"
            }`}
          />
          {member.is_online ? "онлайн" : "оффлайн"}
        </div>
        {onUnlink && (
          <button
            onClick={onUnlink}
            className="text-xs text-gray-500 hover:text-gray-700"
          >
            Отвязать устройство
          </button>
        )}
        {canRemove && onRemove && (
          <button
            onClick={onRemove}
            className="text-xs text-red-400 hover:text-red-600"
          >
            Удалить
          </button>
        )}
      </div>
    </div>
  );
}
