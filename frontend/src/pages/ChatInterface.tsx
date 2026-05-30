import { useState, useRef, useEffect, useCallback } from 'react';
import { useChatStream } from '../hooks/useChatStream';
import { useAuthStore } from '../store/authStore';
import { useThemeStore } from '../store/themeStore';
import LoginModal from '../components/LoginModal';
import Sidebar from '../components/Sidebar';
import type { Message, ModelOption } from '../types/chat';

const MODELS: ModelOption[] = [
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'gpt', label: 'GPT-4' },
  { value: 'zhipu', label: '智谱 GLM' },
  { value: 'qwen', label: '通义千问' },
  { value: 'openai', label: 'OpenAI 兼容' },
];

const SUGGESTIONS = ['写一个排序算法', '解释什么是 Docker', '用 Python 解析 JSON', 'React 和 Vue 的区别'];

function uid() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 8); }

function fmtTime(ts: number) {
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}
function fmtClock() {
  const now = new Date();
  return now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function UserAvatar() {
  return (
    <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center text-white text-sm font-bold shrink-0 shadow-lg shadow-purple-500/20">
      U
    </div>
  );
}

function BotAvatar({ model }: { model: string }) {
  const colors: Record<string, string> = {
    gpt: 'from-emerald-400 to-teal-500 shadow-emerald-500/20',
    zhipu: 'from-blue-400 to-indigo-500 shadow-blue-500/20',
    qwen: 'from-orange-400 to-rose-500 shadow-orange-500/20',
    deepseek: 'from-cyan-400 to-sky-500 shadow-cyan-500/20',
    openai: 'from-pink-400 to-fuchsia-500 shadow-pink-500/20',
  };
  const g = colors[model] ?? colors.gpt;
  return (
    <div className={`w-10 h-10 rounded-xl bg-gradient-to-br ${g} flex items-center justify-center text-white text-sm font-bold shrink-0 shadow-lg`}>
      AI
    </div>
  );
}

function TypingIndicator() {
  return (
    <div className="flex items-center gap-1 px-1 py-1">
      {[0, 1, 2].map((i) => (
        <span key={i} className="w-2 h-2 rounded-full bg-slate-400 dark:bg-slate-500 animate-bounce"
              style={{ animationDelay: `${i * 150}ms`, animationDuration: '0.8s' }} />
      ))}
    </div>
  );
}

function EmptyState({ onSuggestion }: { onSuggestion: (text: string) => void }) {
  return (
    <div className="flex flex-col items-center justify-center h-full px-4 animate-fadeIn">
      <div className="relative mb-8">
        <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-blue-500 via-purple-500 to-pink-500 flex items-center justify-center shadow-2xl shadow-purple-500/30">
          <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
              d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
          </svg>
        </div>
        <div className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-green-400 border-2 border-white dark:border-slate-900 animate-pulse" />
      </div>
      <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-200 mb-2">你好，欢迎使用 AI-CodeHub</h2>
      <p className="text-sm text-slate-500 dark:text-slate-400 mb-8">选择模型，开始智能对话</p>
      <div className="flex flex-wrap justify-center gap-2 max-w-sm">
        {SUGGESTIONS.map((s) => (
          <button key={s} onClick={() => onSuggestion(s)}
            className="px-3 py-2 text-xs text-slate-600 dark:text-slate-300 bg-slate-100 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/50 rounded-xl
                       hover:bg-slate-200 dark:hover:bg-slate-700/60 transition-all duration-200">
            {s}
          </button>
        ))}
      </div>
    </div>
  );
}

export default function ChatInterface() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [model, setModel] = useState('deepseek');
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [conversationId, setConversationId] = useState<number | null>(() => {
    const s = localStorage.getItem('aicodehub-active-conv');
    return s ? Number(s) : null;
  });
  const [modalOpen, setModalOpen] = useState(false);
  const [agentMode, setAgentMode] = useState(false);
  const [clock, setClock] = useState(fmtClock);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const { startStream, abort } = useChatStream();
  const { isLoggedIn, isAdmin, username, token, logout } = useAuthStore();
  const { theme, toggle: toggleTheme } = useThemeStore();

  // clock
  useEffect(() => {
    const id = setInterval(() => setClock(fmtClock()), 1000);
    return () => clearInterval(id);
  }, []);

  // scroll
  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  // load conversation
  useEffect(() => {
    if (!token || !conversationId) return;
    fetch(`/api/v1/conversations/${conversationId}/messages`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((r) => r.json())
      .then((d) => {
        const msgs = (d.data ?? []).map(
          (m: { id: number; role: string; content: string; createdAt: string; inputTokens?: number; outputTokens?: number }) => ({
            id: String(m.id),
            role: m.role as 'user' | 'assistant',
            content: m.content,
            model,
            timestamp: new Date(m.createdAt).getTime(),
          }),
        );
        if (msgs.length > 0) setMessages(msgs);
      });
  }, [token, conversationId]); // eslint-disable-line react-hooks/exhaustive-deps

  const newConversation = useCallback(() => {
    setMessages([]); setConversationId(null);
    localStorage.removeItem('aicodehub-active-conv');
  }, []);

  const sendWithConvId = (text: string, cid: number) => {
    const now = Date.now();
    const um: Message = { id: uid(), role: 'user', content: text.trim(), timestamp: now };
    const am: Message = { id: uid(), role: 'assistant', content: '', model, timestamp: now };
    setMessages((prev) => [...prev, um, am]);
    setInput('');
    const base = agentMode ? '/api/v1/agent/chat' : '/api/v1/chat/stream';
    const url = `${base}?prompt=${encodeURIComponent(text.trim())}&modelType=${model}&conversationId=${cid}`;
    setStreaming(true);
    startStream(url,
      (chunk) => setMessages((prev) => {
        const u = [...prev]; const l = u[u.length - 1];
        if (l?.role === 'assistant') u[u.length - 1] = { ...l, content: l.content + chunk };
        return u;
      }),
      () => setStreaming(false),
      (err) => {
        setMessages((prev) => {
          const u = [...prev]; const l = u[u.length - 1];
          if (l?.role === 'assistant') u[u.length - 1] = { ...l, content: `请求失败：${err.message}` };
          return u;
        });
        setStreaming(false);
      },
      (inputT, outputT) => {
        setMessages((prev) => {
          const u = [...prev]; const l = u[u.length - 1];
          if (l?.role === 'assistant') u[u.length - 1] = { ...l, inputTokens: inputT, outputTokens: outputT };
          return u;
        });
      },
    );
  };

  const sendMessage = useCallback((text: string, useAgent: boolean) => {
    if (!text.trim() || streaming) return;
    const now = Date.now();
    const um: Message = { id: uid(), role: 'user', content: text.trim(), timestamp: now };
    const am: Message = { id: uid(), role: 'assistant', content: '', model, timestamp: now };
    setMessages((prev) => [...prev, um, am]);
    setInput('');
    const base = useAgent ? '/api/v1/agent/chat' : '/api/v1/chat/stream';
    const params = `prompt=${encodeURIComponent(text.trim())}&modelType=${model}`;
    const url = isLoggedIn && conversationId
      ? `${base}?${params}&conversationId=${conversationId}`
      : `${base}?${params}`;
    setStreaming(true);
    startStream(url,
      (chunk) => setMessages((prev) => {
        const u = [...prev]; const l = u[u.length - 1];
        if (l?.role === 'assistant') u[u.length - 1] = { ...l, content: l.content + chunk };
        return u;
      }),
      () => setStreaming(false),
      (err) => {
        setMessages((prev) => {
          const u = [...prev]; const l = u[u.length - 1];
          if (l?.role === 'assistant') u[u.length - 1] = { ...l, content: `请求失败：${err.message}` };
          return u;
        });
        setStreaming(false);
      },
      (inputT, outputT) => {
        setMessages((prev) => {
          const u = [...prev]; const l = u[u.length - 1];
          if (l?.role === 'assistant') u[u.length - 1] = { ...l, inputTokens: inputT, outputTokens: outputT };
          return u;
        });
      },
    );
  }, [model, streaming, startStream, isLoggedIn, conversationId]);

  const handleSendOrCreate = async () => {
    const text = input.trim();
    if (!text || streaming) return;
    if (isLoggedIn && !conversationId) {
      const res = await fetch('/api/v1/conversations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token!}` },
        body: JSON.stringify({ model, title: text }),
      });
      const data = await res.json();
      if (data.code === 200 && data.data?.id) {
        setConversationId(data.data.id);
        sendWithConvId(text, data.data.id);
        return;
      }
    }
    sendMessage(text, agentMode);
  };

  const handleSuggestion = (text: string) => {
    if (isLoggedIn) {
      setInput(text);
      setTimeout(() => handleSendOrCreate(), 50);
    } else sendMessage(text, agentMode);
  };

  const handleStop = () => { abort(); setStreaming(false); };
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSendOrCreate(); }
  };

  const lastMsg = messages[messages.length - 1];
  const isTyping = streaming && lastMsg?.role === 'assistant' && lastMsg.content === '';

  return (
    <div className="flex h-screen bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-200 transition-colors duration-300">
      {/* Background gradient */}
      <div className="fixed inset-0 -z-10 pointer-events-none">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-100 via-white to-white dark:from-slate-900 dark:via-slate-950 dark:to-slate-950" />
      </div>

      <Sidebar token={token} activeId={conversationId} onSelect={(id) => { setConversationId(id); }} onNew={newConversation} />

      <div className="flex-1 flex flex-col min-w-0">
        {/* Header */}
        <header className="shrink-0 border-b border-slate-200 dark:border-slate-800/80 bg-white/70 dark:bg-slate-950/70 backdrop-blur-xl transition-colors">
          <div className="flex items-center justify-between px-4 h-14">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center shadow-lg shadow-purple-500/20">
                <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
                </svg>
              </div>
              <span className="text-sm font-bold text-slate-800 dark:text-slate-200 tracking-tight">AI-CodeHub</span>
              <span className="text-xs text-slate-400 dark:text-slate-500 font-mono hidden sm:inline">{clock}</span>
            </div>

            <div className="flex items-center gap-2">
              {/* Theme toggle */}
              <button onClick={toggleTheme}
                className="w-8 h-8 flex items-center justify-center rounded-lg text-slate-500 dark:text-slate-400
                           hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors text-lg"
                title={theme === 'dark' ? '切换浅色模式' : '切换深色模式'}>
                {theme === 'dark' ? '☀️' : '🌙'}
              </button>

              <button onClick={() => setAgentMode(!agentMode)}
                className={`h-8 px-3 text-xs font-semibold rounded-lg transition-all ${
                  agentMode
                    ? 'bg-purple-600 text-white shadow-lg shadow-purple-500/20'
                    : 'text-slate-500 dark:text-slate-400 bg-slate-100 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/50 hover:text-slate-700 dark:hover:text-white'
                }`}>
                {agentMode ? '🤖 Agent' : '💬 对话'}
              </button>

              <select value={model} onChange={(e) => setModel(e.target.value)}
                className="h-8 pl-3 pr-8 text-xs font-medium text-slate-600 dark:text-slate-300 bg-slate-100 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/50 rounded-lg
                           focus:outline-none focus:ring-2 focus:ring-blue-500/50 cursor-pointer appearance-none
                           bg-[url('data:image/svg+xml;charset=utf-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20viewBox%3D%220%200%2020%2020%22%20fill%3D%22%2394a3b8%22%3E%3Cpath%20fill-rule%3D%22evenodd%22%20d%3D%22M5.23%207.21a.75.75%200%20011.06.02L10%2011.168l3.71-3.938a.75.75%200%20111.08%201.04l-4.25%204.5a.75.75%200%2001-1.08%200l-4.25-4.5a.75.75%200%2001.02-1.06z%22%20clip-rule%3D%22evenodd%22%2F%3E%3C%2Fsvg%3E')] bg-[length:1.1rem] bg-[right_0.25rem_center] bg-no-repeat
                           hover:bg-slate-200 dark:hover:bg-slate-700/60 transition-colors">
                {MODELS.map((m) => (<option key={m.value} value={m.value}>{m.label}</option>))}
              </select>

              {isAdmin && (
                <a href="/admin" className="h-8 px-3 flex items-center text-xs font-semibold text-amber-600 dark:text-amber-400
                  bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/30 rounded-lg hover:bg-amber-100 dark:hover:bg-amber-500/20 transition-colors">
                  管理后台
                </a>
              )}

              {isLoggedIn ? (
                <div className="flex items-center gap-2">
                  <span className="text-xs text-slate-500 dark:text-slate-400 hidden sm:inline">{username}</span>
                  <button onClick={logout} className="h-8 px-3 text-xs font-medium text-slate-500 dark:text-slate-400
                    bg-slate-100 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/50 rounded-lg
                    hover:text-slate-700 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-slate-700/60 transition-colors">退出</button>
                </div>
              ) : (
                <button onClick={() => setModalOpen(true)} className="h-8 px-3 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors shadow-sm">登录</button>
              )}
            </div>
          </div>
        </header>

        {/* Messages */}
        <div ref={scrollRef} className="flex-1 overflow-y-auto">
          {messages.length === 0 ? (
            <EmptyState onSuggestion={handleSuggestion} />
          ) : (
            <div className="max-w-3xl mx-auto px-4 py-6 space-y-5">
              {messages.map((msg) => (
                <div key={msg.id} className={`flex gap-3 animate-fadeIn ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}>
                  {msg.role === 'user' ? <UserAvatar /> : <BotAvatar model={msg.model ?? 'deepseek'} />}
                  <div className={`flex-1 flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'}`}>
                    <div className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${
                      msg.role === 'user'
                        ? 'bg-blue-600 text-white rounded-tr-md shadow-lg shadow-blue-600/20'
                        : 'bg-slate-100 dark:bg-slate-800/60 text-slate-800 dark:text-slate-200 rounded-tl-md border border-slate-200 dark:border-slate-700/30'
                    }`}>
                      {msg.role === 'assistant' && msg.model && (
                        <div className="text-[11px] text-slate-400 dark:text-slate-400 mb-1.5 font-medium">
                          {MODELS.find((m) => m.value === msg.model)?.label ?? msg.model}
                        </div>
                      )}
                      <div className="whitespace-pre-wrap break-words">
                        {msg.content || (msg === lastMsg && isTyping ? <TypingIndicator /> : null)}
                        {msg.role === 'assistant' && streaming && msg === lastMsg && msg.content && (
                          <span className="inline-block w-[3px] h-4 ml-0.5 bg-blue-400 rounded-full align-text-bottom animate-pulse" />
                        )}
                      </div>
                    </div>
                    {/* Timestamp + Token info */}
                    <div className={`flex items-center gap-2 mt-1 ${msg.role === 'user' ? 'flex-row-reverse mr-1' : 'ml-1'}`}>
                      <span className="text-[10px] text-slate-400 dark:text-slate-600">{fmtTime(msg.timestamp)}</span>
                      {msg.role === 'assistant' && msg.inputTokens != null && (
                        <span className="text-[10px] text-slate-400 dark:text-slate-600">
                          Token: ↑{msg.inputTokens} ↓{msg.outputTokens}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
              {streaming && lastMsg?.role === 'user' && (
                <div className="flex gap-3 animate-fadeIn">
                  <BotAvatar model={model} />
                  <div className="bg-slate-100 dark:bg-slate-800/60 rounded-2xl rounded-tl-md border border-slate-200 dark:border-slate-700/30 px-4 py-3">
                    <TypingIndicator />
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Input */}
        <div className="shrink-0 border-t border-slate-200 dark:border-slate-800/80 bg-white/70 dark:bg-slate-950/70 backdrop-blur-xl transition-colors">
          <div className="max-w-3xl mx-auto px-4 py-3">
            <div className="flex items-center gap-2 bg-slate-100 dark:bg-slate-900/80 rounded-2xl border border-slate-200 dark:border-slate-700/50 px-3 py-2
                            focus-within:border-blue-500/50 focus-within:ring-2 focus-within:ring-blue-500/20 transition-all duration-200">
              <input ref={inputRef} type="text" value={input} onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown} disabled={streaming}
                placeholder="输入消息，Enter 发送..."
                className="flex-1 bg-transparent px-1 text-sm placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none disabled:opacity-50 text-slate-800 dark:text-slate-200" />
              {streaming ? (
                <button onClick={handleStop}
                  className="flex items-center gap-1.5 h-8 px-3 text-xs font-semibold text-white bg-red-500 rounded-xl hover:bg-red-600 transition-colors shrink-0">
                  <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="2" /></svg>停止
                </button>
              ) : (
                <button onClick={handleSendOrCreate} disabled={!input.trim()}
                  className="flex items-center gap-1.5 h-8 px-4 text-xs font-semibold text-white bg-gradient-to-r from-blue-600 to-blue-500 rounded-xl
                             hover:from-blue-500 hover:to-blue-400 disabled:from-slate-300 dark:disabled:from-slate-700 disabled:text-slate-400
                             disabled:cursor-not-allowed transition-all duration-200 shrink-0 shadow-lg shadow-blue-600/25">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 12L3.269 3.125A59.77 59.77 0 0121.485 12 59.77 59.77 0 013.27 20.875L5.999 12zm0 0h7.5" />
                  </svg>发送
                </button>
              )}
            </div>
          </div>
        </div>
      </div>

      <LoginModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </div>
  );
}
