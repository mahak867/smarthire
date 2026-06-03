// ── SmartHire · frontend/src/hooks/useAiScoring.ts ──
import { useEffect, useState, useCallback } from "react";
import { Client, type Frame }              from "@stomp/stompjs";
import SockJS                              from "sockjs-client";
import { useAuthStore }                    from "../store/authStore";

export interface ScoringResult {
  applicationId: string;
  aiScore:        number;
  skillMatchPct:  number;
  recommendation: string;
  summary:        string;
  scoringMethod:  string;
}

/**
 * Subscribe to real-time AI scoring updates for a specific application.
 *
 * Usage:
 *   const { score, connected } = useAiScoring(applicationId);
 *
 * The score updates automatically when the backend finishes inference —
 * no polling required.
 */
export function useAiScoring(applicationId: string | null | undefined) {
  const { accessToken }             = useAuthStore();
  const [score,     setScore]       = useState<ScoringResult | null>(null);
  const [connected, setConnected]   = useState(false);
  const [error,     setError]       = useState<string | null>(null);

  const handleFrame = useCallback((body: string) => {
    try {
      const data = JSON.parse(body) as ScoringResult;
      if (data.applicationId === applicationId) {
        setScore(data);
      }
    } catch (e) {
      console.warn("[useAiScoring] Failed to parse message", e);
    }
  }, [applicationId]);

  useEffect(() => {
    if (!applicationId || !accessToken) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${window.location.origin}/ws`),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,

      onConnect: (_frame: Frame) => {
        setConnected(true);
        setError(null);
        client.subscribe(
          `/topic/applications/${applicationId}/score`,
          (msg) => handleFrame(msg.body)
        );
      },

      onDisconnect: () => setConnected(false),
      onStompError:  (f) => {
        setError("Real-time connection error. Scores will update on refresh.");
        console.error("[useAiScoring] STOMP error", f);
      },
      onWebSocketError: (e) => {
        setError("WebSocket unavailable. Scores will update on refresh.");
        console.warn("[useAiScoring] WS error", e);
      },
    });

    client.activate();
    return () => { client.deactivate(); setConnected(false); };
  }, [applicationId, accessToken, handleFrame]);

  return { score, connected, error };
}

/**
 * Subscribe to personal user notifications (status changes, interview alerts).
 */
export function useUserNotifications() {
  const { accessToken, user }    = useAuthStore();
  const [notifications, setNotifications] = useState<any[]>([]);
  const [connected, setConnected]         = useState(false);

  useEffect(() => {
    if (!accessToken || !user?.id) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${window.location.origin}/ws`),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 10000,

      onConnect: () => {
        setConnected(true);
        client.subscribe(`/user/${user.id}/queue/notifications`, (msg) => {
          try {
            const n = JSON.parse(msg.body);
            setNotifications(prev => [n, ...prev].slice(0, 20)); // keep last 20
          } catch {}
        });
      },
      onDisconnect: () => setConnected(false),
    });

    client.activate();
    return () => client.deactivate();
  }, [accessToken, user?.id]);

  const clearNotifications = useCallback(() => setNotifications([]), []);

  return { notifications, connected, clearNotifications };
}
