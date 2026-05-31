import { useCallback, useRef } from 'react';

function jwtExp(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 < Date.now();
  } catch { return true; }
}

async function getValidToken(): Promise<string | null> {
  const saved = JSON.parse(localStorage.getItem('aicodehub-auth') || '{}');
  if (!saved.token) return null;
  if (!jwtExp(saved.token)) return saved.token;
  // Token expired, try refresh
  if (!saved.refreshToken) return null;
  const rr = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: saved.refreshToken }),
  });
  if (rr.ok) {
    const data = await rr.json();
    saved.token = data.data.token;
    localStorage.setItem('aicodehub-auth', JSON.stringify(saved));
    return data.data.token;
  }
  return null;
}

async function fetchWithRefresh(url: string, init: RequestInit): Promise<Response> {
  const token = await getValidToken();
  const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};
  let res = await fetch(url, { ...init, headers });
  if (res.status === 401) {
    // Fallback: try refresh again if server rejects
    const saved = JSON.parse(localStorage.getItem('aicodehub-auth') || '{}');
    if (saved.refreshToken) {
      const rr = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: saved.refreshToken }),
      });
      if (rr.ok) {
        const data = await rr.json();
        saved.token = data.data.token;
        localStorage.setItem('aicodehub-auth', JSON.stringify(saved));
        res = await fetch(url, { ...init, headers });
      }
    }
  }
  return res;
}

export function useChatStream() {
  const abortRef = useRef<AbortController | null>(null);

  const startStream = useCallback(
    (
      url: string,
      onChunk: (text: string) => void,
      onDone: () => void,
      onError: (err: Error) => void,
      onToken?: (input: number, output: number) => void,
    ) => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      fetchWithRefresh(url, { signal: controller.signal })
        .then(async (response) => {
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          const reader = response.body?.getReader();
          if (!reader) throw new Error('No response body');

          const decoder = new TextDecoder();
          let buffer = '';

          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const events = buffer.split('\n\n');
            buffer = events.pop() ?? '';

            for (const event of events) {
              const dataLines: string[] = [];
              for (const line of event.split('\n')) {
                if (line.startsWith('data:')) {
                  dataLines.push(line.slice(5).trim());
                }
              }
              if (dataLines.length === 0) continue;
              const text = dataLines.join('\n');
              if (text === '[DONE]') { onDone(); return; }
              if (text.startsWith('[TOKEN]')) {
                const m = text.match(/input=(\d+),output=(\d+)/);
                if (m && onToken) onToken(Number(m[1]), Number(m[2]));
                continue;
              }
              onChunk(text);
            }
          }
          onDone();
        })
        .catch((err) => {
          if (err.name !== 'AbortError') onError(err);
        });
    },
    [],
  );

  const abort = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  return { startStream, abort };
}
