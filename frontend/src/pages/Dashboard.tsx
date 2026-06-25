import { useEffect, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup, Circle } from "react-leaflet";
import L from "leaflet";
import { useQuery } from "@tanstack/react-query";
import { locationApi } from "../api/client";
import { useAuthStore } from "../store/authStore";
import { useFamilyMapWs } from "../hooks/useFamilyMapWs";
import { formatDistanceToNow } from "date-fns";
import { ru } from "date-fns/locale";

// Fix Leaflet default icon
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png",
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
});

function makeIcon(role: string, isOnline: boolean) {
  const color = role === "child" ? (isOnline ? "#16a34a" : "#6b7280") : "#2563eb";
  const emoji = role === "child" ? "👶" : "👨";
  return L.divIcon({
    className: "",
    html: `<div style="
      background:${color};
      border-radius:50%;
      width:36px;height:36px;
      display:flex;align-items:center;justify-content:center;
      font-size:18px;
      border:3px solid white;
      box-shadow:0 2px 8px rgba(0,0,0,0.3);
    ">${emoji}</div>`,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
  });
}

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const familyId = user?.family;

  const { positions, sosAlerts, connected } = useFamilyMapWs(familyId);

  const { data: initialPositions } = useQuery({
    queryKey: ["locations"],
    queryFn: () => locationApi.currentPositions().then((r) => r.data),
    enabled: !!familyId,
  });

  const [allPositions, setAllPositions] = useState<Record<string, any>>({});

  useEffect(() => {
    if (initialPositions) {
      const map: Record<string, any> = {};
      initialPositions.forEach((p: any) => {
        map[p.user_id] = p;
      });
      setAllPositions(map);
    }
  }, [initialPositions]);

  useEffect(() => {
    if (Object.keys(positions).length > 0) {
      setAllPositions((prev) => ({ ...prev, ...positions }));
    }
  }, [positions]);

  const positionsList = Object.values(allPositions);
  const center: [number, number] =
    positionsList.length > 0
      ? [positionsList[0].latitude, positionsList[0].longitude]
      : [41.2995, 69.2401]; // Tashkent default

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 bg-white border-b border-gray-100">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Карта семьи</h1>
          <p className="text-sm text-gray-500">Реальное время</p>
        </div>
        <div className="flex items-center gap-3">
          {sosAlerts.length > 0 && (
            <div className="flex items-center gap-2 bg-red-50 border border-red-200 
                            rounded-lg px-3 py-1.5 text-red-700 text-sm font-medium animate-pulse">
              🚨 SOS: {sosAlerts[0].child_name}
            </div>
          )}
          <div
            className={`flex items-center gap-2 text-sm px-3 py-1.5 rounded-full font-medium ${
              connected
                ? "bg-green-50 text-green-700"
                : "bg-gray-100 text-gray-500"
            }`}
          >
            <span
              className={`w-2 h-2 rounded-full ${connected ? "bg-green-500" : "bg-gray-400"}`}
            />
            {connected ? "Подключено" : "Переподключение..."}
          </div>
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Map */}
        <div className="flex-1 p-4">
          <MapContainer
            center={center}
            zoom={13}
            className="rounded-xl shadow-sm border border-gray-200"
            style={{ height: "100%" }}
          >
            <TileLayer
              attribution='&copy; <a href="https://openstreetmap.org">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            {positionsList.map((pos) => (
              <Marker
                key={pos.user_id}
                position={[pos.latitude, pos.longitude]}
                icon={makeIcon(pos.role, pos.is_online)}
              >
                <Popup>
                  <div className="text-sm space-y-1 min-w-[160px]">
                    <div className="font-bold text-gray-900">{pos.name}</div>
                    <div className={pos.role === "child" ? "text-green-600" : "text-blue-600"}>
                      {pos.role === "child" ? "Ребёнок" : "Родитель"}
                    </div>
                    {pos.battery_level !== null && (
                      <div className="flex items-center gap-1 text-gray-600">
                        🔋 {pos.battery_level}%
                      </div>
                    )}
                    <div className="text-gray-400 text-xs">
                      {pos.recorded_at
                        ? formatDistanceToNow(new Date(pos.recorded_at), {
                            addSuffix: true,
                            locale: ru,
                          })
                        : "—"}
                    </div>
                    <div
                      className={`text-xs font-medium ${
                        pos.is_online ? "text-green-600" : "text-gray-400"
                      }`}
                    >
                      {pos.is_online ? "● Онлайн" : "○ Оффлайн"}
                    </div>
                    {pos.accuracy && (
                      <Circle
                        center={[pos.latitude, pos.longitude]}
                        radius={pos.accuracy}
                        pathOptions={{ color: "#2563eb", fillOpacity: 0.1 }}
                      />
                    )}
                  </div>
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        </div>

        {/* Side panel */}
        <div className="w-72 p-4 space-y-3 overflow-auto">
          <div className="card">
            <h2 className="font-semibold text-gray-800 mb-3">Члены семьи</h2>
            {positionsList.length === 0 ? (
              <p className="text-sm text-gray-400">Нет данных о местоположении</p>
            ) : (
              <div className="space-y-2">
                {positionsList.map((pos) => (
                  <div
                    key={pos.user_id}
                    className="flex items-center gap-3 p-2 rounded-lg hover:bg-gray-50"
                  >
                    <div
                      className={`w-8 h-8 rounded-full flex items-center justify-center text-sm
                                  ${pos.is_online ? "bg-green-100" : "bg-gray-100"}`}
                    >
                      {pos.role === "child" ? "👶" : "👨"}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-gray-800 truncate">
                        {pos.name}
                      </div>
                      <div className="text-xs text-gray-400">
                        {pos.battery_level != null ? `🔋${pos.battery_level}%` : ""}
                        {pos.is_online ? " ● онлайн" : " ○ оффлайн"}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* SOS Alerts */}
          {sosAlerts.length > 0 && (
            <div className="card border-red-200 bg-red-50">
              <h2 className="font-semibold text-red-800 mb-3">🚨 SOS сигналы</h2>
              <div className="space-y-2">
                {sosAlerts.slice(0, 5).map((sos, i) => (
                  <div key={i} className="bg-white rounded-lg p-2 shadow-sm">
                    <div className="text-sm font-semibold text-red-700">
                      {sos.sos_type === "emergency" ? "ЭКСТРЕННЫЙ" : "Сигнал"} —{" "}
                      {sos.child_name}
                    </div>
                    <div className="text-xs text-gray-500">
                      {sos.press_count} нажатий •{" "}
                      {formatDistanceToNow(new Date(sos.created_at), {
                        addSuffix: true,
                        locale: ru,
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
