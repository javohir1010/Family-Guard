import { useEffect, useRef, useState, useCallback } from "react";
import { useAuthStore } from "../store/authStore";

interface WsMessage {
  type: string;
  data: any;
}

export function useFamilyMapWs(familyId: string | null) {
  const token = useAuthStore((s) => s.accessToken);
  const wsRef = useRef<WebSocket | null>(null);
  const [positions, setPositions] = useState<Record<string, any>>({});
  const [sosAlerts, setSosAlerts] = useState<any[]>([]);
  const [connected, setConnected] = useState(false);
  const pingInterval = useRef<ReturnType<typeof setInterval> | null>(null);

  const connect = useCallback(() => {
    if (!familyId || !token) return;

    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const host = window.location.host;
    const url = `${protocol}://${host}/ws/family/${familyId}/map/?token=${token}`;

    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onopen = () => {
      setConnected(true);
      // Keepalive ping every 30 seconds
      pingInterval.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: "ping" }));
        }
      }, 30_000);
    };

    ws.onmessage = (event) => {
      try {
        const msg: WsMessage = JSON.parse(event.data);

        if (msg.type === "initial_positions") {
          const posMap: Record<string, any> = {};
          (msg.data as any[]).forEach((p) => {
            posMap[p.user_id] = p;
          });
          setPositions(posMap);
        } else if (msg.type === "location_update") {
          setPositions((prev) => ({
            ...prev,
            [msg.data.user_id]: msg.data,
          }));
        } else if (msg.type === "sos_alert") {
          setSosAlerts((prev) => [msg.data, ...prev].slice(0, 50));
        }
      } catch {
        // ignore parse errors
      }
    };

    ws.onclose = () => {
      setConnected(false);
      if (pingInterval.current) clearInterval(pingInterval.current);
      // Reconnect after 5 seconds
      setTimeout(connect, 5_000);
    };

    ws.onerror = () => ws.close();
  }, [familyId, token]);

  useEffect(() => {
    connect();
    return () => {
      wsRef.current?.close();
      if (pingInterval.current) clearInterval(pingInterval.current);
    };
  }, [connect]);

  return { positions, sosAlerts, connected };
}
