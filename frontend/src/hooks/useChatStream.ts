import { useCallback, useRef } from 'react';

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

      let headers: Record<string, string> = {};
      try {
        const saved = JSON.parse(localStorage.getItem('aicodehub-auth') || '{}');
        if (saved.token) headers['Authorization'] = `Bearer ${saved.token}`;
      } catch {}
      fetch(url, { signal: controller.signal, headers })
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
