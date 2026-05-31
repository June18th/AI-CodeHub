import { useCallback, useEffect, useRef, useState } from 'react';

type MessageHandler = (data: any) => void;

interface WSState {
  connected: boolean;
  connecting: boolean;
}

export function useWebSocket(token: string | null) {
  const wsRef = useRef<WebSocket | null>(null);
  const handlersRef = useRef<Map<string, Set<MessageHandler>>>(new Map());
  const reconnectRef = useRef({ attempt: 0, maxDelay: 30000, timer: null as any });
  const heartbeatRef = useRef({ ping: null as any, pong: null as any });
  const [state, setState] = useState<WSState>({ connected: false, connecting: false });

  const connect = useCallback(() => {
    if (!token || wsRef.current?.readyState === WebSocket.OPEN) return;
    setState(s => ({ ...s, connecting: true }));

    const host = location.hostname + ':3080';
    const ws = new WebSocket(`ws://${host}/ws/chat?token=${token}`);

    ws.onopen = () => {
      setState({ connected: true, connecting: false });
      reconnectRef.current.attempt = 0;
      // Heartbeat
      heartbeatRef.current.ping = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) ws.send('{"type":"ping"}');
      }, 30000);
      heartbeatRef.current.pong = setTimeout(() => {
        if (ws.readyState !== WebSocket.OPEN) ws.close();
      }, 60000);
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === 'pong') {
          clearTimeout(heartbeatRef.current.pong);
          heartbeatRef.current.pong = setTimeout(() => ws.close(), 60000);
          return;
        }
        handlersRef.current.get(msg.type)?.forEach(h => h(msg.data || msg));
        handlersRef.current.get('*')?.forEach(h => h(msg));
      } catch {}
    };

    ws.onclose = () => {
      setState({ connected: false, connecting: false });
      clearInterval(heartbeatRef.current.ping);
      clearTimeout(heartbeatRef.current.pong);
      // Reconnect with exponential backoff
      const delay = Math.min(1000 * Math.pow(2, reconnectRef.current.attempt), reconnectRef.current.maxDelay);
      reconnectRef.current.attempt++;
      reconnectRef.current.timer = setTimeout(connect, delay);
    };

    ws.onerror = () => ws.close();
    wsRef.current = ws;
  }, [token]);

  const disconnect = useCallback(() => {
    clearTimeout(reconnectRef.current.timer);
    reconnectRef.current.attempt = 999; // prevent reconnect
    wsRef.current?.close();
  }, []);

  const send = useCallback((type: string, data?: any) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type, ...data }));
    }
  }, []);

  const on = useCallback((type: string, handler: MessageHandler) => {
    if (!handlersRef.current.has(type)) handlersRef.current.set(type, new Set());
    handlersRef.current.get(type)!.add(handler);
    return () => { handlersRef.current.get(type)?.delete(handler); };
  }, []);

  useEffect(() => { connect(); return () => disconnect(); }, [connect, disconnect]);

  return { state, send, on, disconnect };
}
