import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { familyApi } from "../api/client";
import toast from "react-hot-toast";

export default function FamilyPage() {
  const qc = useQueryClient();
  const [showInvite, setShowInvite] = useState(false);
  const [inviteCode, setInviteCode] = useState<string | null>(null);

  const { data: family } = useQuery({
    queryKey: ["family-detail"],
    queryFn: () => familyApi.detail().then((r) => r.data),
  });

  const { data: members = [] } = useQuery({
    queryKey: ["family-members"],
    queryFn: () => familyApi.members().then((r) => r.data),
  });

  const generateInviteMutation = useMutation({
    mutationFn: () => familyApi.generateInvite(),
    onSuccess: (res) => {
      setInviteCode(res.data.code);
      setShowInvite(true);
    },
    onError: () => toast.error("Ошибка генерации кода"),
  });

  const removeMemberMutation = useMutation({
    mutationFn: (id: string) => familyApi.removeMember(id),
    onSuccess: () => {
      toast.success("Участник удалён");
      qc.invalidateQueries({ queryKey: ["family-members"] });
    },
  });

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
        <button
          onClick={() => generateInviteMutation.mutate()}
          disabled={generateInviteMutation.isPending}
          className="btn-primary"
        >
          + Пригласить ребёнка
        </button>
      </div>

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
                  onRemove={() => removeMemberMutation.mutate(m.id)}
                />
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Invite modal */}
      {showInvite && inviteCode && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-8 w-full max-w-sm shadow-2xl text-center">
            <div className="text-4xl mb-4">🔑</div>
            <h2 className="text-xl font-bold text-gray-900 mb-2">
              Инвайт-код
            </h2>
            <p className="text-sm text-gray-500 mb-6">
              Передайте этот код ребёнку для входа в приложение.
              <br />
              Действует 24 часа.
            </p>
            <div className="bg-blue-50 border-2 border-blue-200 rounded-xl py-4 px-6 mb-6">
              <span className="text-4xl font-mono font-bold tracking-[0.3em] text-blue-700">
                {inviteCode}
              </span>
            </div>
            <button
              onClick={() => {
                navigator.clipboard.writeText(inviteCode);
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
}: {
  member: any;
  canRemove: boolean;
  onRemove?: () => void;
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
