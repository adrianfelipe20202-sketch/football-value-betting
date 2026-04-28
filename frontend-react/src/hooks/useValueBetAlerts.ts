import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import type { ValueBetAlert } from '../types';
import { api } from '../services/api';

export function useValueBetAlerts() {
  const [alerts, setAlerts] = useState<ValueBetAlert[]>([]);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    api.getAlerts()
      .then(data => setAlerts(data))
      .catch(() => {});

    const client = new Client({
      brokerURL: `${(import.meta.env.VITE_API_URL || `http://${window.location.hostname}:8080`).replace(/^http/, 'ws')}/ws-raw`,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        setConnected(true);

        client.subscribe('/topic/value-alerts', (message) => {
          try {
            const alert: ValueBetAlert = JSON.parse(message.body);
            setAlerts(prev => mergeAlerts(prev, [alert]));
          } catch {}
        });

        client.subscribe('/topic/alerts', (message) => {
          try {
            const alert: ValueBetAlert = JSON.parse(message.body);
            setAlerts(prev => mergeAlerts(prev, [alert]));
          } catch {}
        });
      },

      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
      onWebSocketError: () => setConnected(false),
    });

    client.activate();
    clientRef.current = client;

    const pollInterval = setInterval(() => {
      api.getAlerts()
        .then(data => setAlerts(prev => mergeAlerts(prev, data)))
        .catch(() => {});
    }, 15000);

    return () => {
      clearInterval(pollInterval);
      client.deactivate();
    };
  }, []);

  const refresh = useCallback(() => {
    api.getAlerts()
      .then(data => setAlerts(data))
      .catch(() => {});
  }, []);

  return { alerts, connected, refresh };
}

function mergeAlerts(existing: ValueBetAlert[], incoming: ValueBetAlert[]): ValueBetAlert[] {
  const map = new Map<string, ValueBetAlert>();
  for (const a of existing) map.set(a.id, a);
  for (const a of incoming) map.set(a.id, a);

  return Array.from(map.values())
    .sort((a, b) => b.confidenceScore - a.confidenceScore);
}
