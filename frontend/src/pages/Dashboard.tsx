import { useEffect, useRef, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup, Circle, useMap } from "react-leaflet";
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

function makeGroupIcon(members: any[]) {
  const anyChildOnline = members.some((m) => m.role === "child" && m.is_online);
  const color = anyChildOnline ? "#16a34a" : "#6b7280";
  return L.divIcon({
    className: "",
    html: `<div style="position:relative;
      background:${color};
      border-radius:50%;
      width:40px;height:40px;
      display:flex;align-items:center;justify-content:center;
      font-size:18px;
      border:3px solid white;
      box-shadow:0 2px 8px rgba(0,0,0,0.3);
    ">👨‍👧<span style="position:absolute;top:-6px;right:-6px;
      background:#2563eb;color:white;border-radius:9999px;
      min-width:20px;height:20px;padding:0 5px;
      font:bold 12px/20px sans-serif;text-align:center;
      border:2px solid white;">${members.length}</span></div>`,
    iconSize: [40, 40],
    iconAnchor: [20, 20],
  });
}

// Members closer than this share one marker; otherwise only the top one is clickable
const SAME_PLACE_METERS = 30;

function groupByPlace(positions: any[]): any[][] {
  const groups: any[][] = [];
  for (const p of positions) {
    const here = L.latLng(p.latitude, p.longitude);
    const group = groups.find(
      (g) => here.distanceTo(L.latLng(g[0].latitude, g[0].longitude)) < SAME_PLACE_METERS
    );
    if (group) group.push(p);
    else groups.push([p]);
  }
  return groups;
}

function MemberInfo({ pos }: { pos: any }) {
  return (
    <div className="text-sm space-y-1 min-w-[160px]">
      <div className="font-bold text-gray-900">{pos.name}</div>
      <div className={pos.role === "child" ? "text-green-600" : "text-blue-600"}>
        {pos.role === "child" ? "Ребёнок" : "Родитель"}
      </div>
      {pos.battery_level != null && (
        <div className="flex items-center gap-1 text-gray-600">🔋 {pos.battery_level}%</div>
      )}
      <div className="text-gray-400 text-xs">
        {pos.recorded_at
          ? formatDistanceToNow(new Date(pos.recorded_at), { addSuffix: true, locale: ru })
          : "—"}
      </div>
      <div className={`text-xs font-medium ${pos.is_online ? "text-green-600" : "text-gray-400"}`}>
        {pos.is_online ? "● Онлайн" : "○ Оффлайн"}
      </div>
    </div>
  );
}

// MapContainer only reads `center` on mount; move the map once data arrives
function FitToPositions({ positions }: { positions: any[] }) {
  const map = useMap();
  const fitted = useRef(false);
  useEffect(() => {
    if (fitted.current || positions.length === 0) return;
    fitted.current = true;
    if (positions.length === 1) {
      map.setView([positions[0].latitude, positions[0].longitude], 14);
    } else {
      map.fitBounds(
        positions.map((p) => [p.latitude, p.longitude] as [number, number]),
        { padding: [40, 40] }
      );
    }
  }, [positions, map]);
  return null;
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
            <FitToPositions positions={positionsList} />
            {positionsList.map((pos) =>
              pos.accuracy ? (
                <Circle
                  key={`acc-${pos.user_id}`}
                  center={[pos.latitude, pos.longitude]}
                  radius={pos.accuracy}
                  pathOptions={{ color: "#2563eb", fillOpacity: 0.1 }}
                />
              ) : null
            )}
            {groupByPlace(positionsList).map((group) =>
              group.length === 1 ? (
                <Marker
                  key={group[0].user_id}
                  position={[group[0].latitude, group[0].longitude]}
                  icon={makeIcon(group[0].role, group[0].is_online)}
                >
                  <Popup>
                    <MemberInfo pos={group[0]} />
                  </Popup>
                </Marker>
              ) : (
                <Marker
                  key={group.map((p) => p.user_id).join("+")}
                  position={[group[0].latitude, group[0].longitude]}
                  icon={makeGroupIcon(group)}
                >
                  <Popup>
                    <div className="space-y-3">
                      <div className="text-xs text-gray-500">Рядом: {group.length}</div>
                      {group.map((p) => (
                        <div key={p.user_id} className="border-t border-gray-100 pt-2 first:border-0 first:pt-0">
                          <MemberInfo pos={p} />
                        </div>
                      ))}
                    </div>
                  </Popup>
                </Marker>
              )
            )}
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
