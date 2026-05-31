import React, { useState, useRef, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useChatStream } from '../hooks/useChatStream';
import { useAuthStore, apiFetch } from '../store/authStore';
import { useThemeStore } from '../store/themeStore';
import LoginModal from '../components/LoginModal';
import Sidebar from '../components/Sidebar';
import ModelSelector from '../components/ModelSelector';
import { LogoIcon, SunIcon, MoonIcon, SendIcon, StopIcon, DocIcon } from '../components/Icons';
import type { Message } from '../types/chat';

/* ── helpers ── */
const MODEL_MAP: Record<string, string> = {
  deepseek: 'DeepSeek', gpt: 'GPT-4', zhipu: '智谱 GLM', qwen: '通义千问', openai: 'OpenAI 兼容',
};
const SUGGESTIONS = ['写一个排序算法', '解释什么是 Docker', '用 Python 解析 JSON', 'React 和 Vue 的区别'];
function uid() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 8); }
function fmtTime(ts: number) {
  const d = new Date(ts);
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`;
}
function fmtClock() { return fmtTime(Date.now()); }

/* ── markdown ── */
function applyInline(text: string): string {
  return text
    .replace(/\*\*(.+?)\*\*/g, '<strong class="font-semibold text-slate-900 dark:text-white">$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`(.+?)`/g, '$1')
    // Remove ALL remaining backtick characters
    .replace(/`/g, '');
}

function renderMarkdown(text: string) {
  const lines = text.split('\n');
  const result: React.ReactNode[] = [];
  let listItems: string[] = [], listType: 'ul' | 'ol' | null = null;
  let tableRows: string[][] = [];
  const flushList = () => {
    if (!listItems.length) return;
    const tag = listType === 'ol' ? 'ol' : 'ul';
    const cls = tag === 'ol' ? 'list-decimal' : 'list-disc';
    result.push(React.createElement(tag, {
      key: tag + '-' + result.length,
      className: `ml-5 my-3 ${cls} space-y-1.5`,
    }, listItems.map((li, j) => React.createElement('li', {
      key: j, className: 'leading-7 text-slate-700 dark:text-slate-300',
      dangerouslySetInnerHTML: { __html: li },
    }))));
    listItems = [];
  };
  const flushTable = () => {
    if (tableRows.length < 2) { tableRows = []; return; }
    const header = tableRows[0];
    const body = tableRows.filter((_,i) => i !== 1); // skip separator row
    result.push(React.createElement('div', { key: 'tbl-' + result.length, className: 'my-4 overflow-hidden rounded-xl border border-slate-200 dark:border-slate-700' },
      React.createElement('table', {
        className: 'w-full text-sm',
      }, [
        React.createElement('thead', { key: 'th' },
          React.createElement('tr', { className: 'bg-slate-50 dark:bg-slate-800/60' },
            header.map((c, j) => React.createElement('th', {
              key: j,
              className: 'px-4 py-2.5 text-left text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider border-b border-slate-200 dark:border-slate-700',
              dangerouslySetInnerHTML: { __html: c },
            }))
          )
        ),
        React.createElement('tbody', { key: 'tb' },
          body.map((row, ri) => React.createElement('tr', {
            key: ri,
            className: ri % 2 === 0 ? 'bg-white dark:bg-slate-900/60' : 'bg-slate-50/50 dark:bg-slate-800/30',
          },
            row.map((c, cj) => React.createElement('td', {
              key: cj,
              className: 'px-4 py-2.5 text-slate-700 dark:text-slate-300 border-b border-slate-100 dark:border-slate-700/50',
              dangerouslySetInnerHTML: { __html: c },
            }))
          ))
        ),
      ])
    ));
    tableRows = [];
  };

  lines.forEach((line, i) => {
    const trimmed = line.trim();
    if (/^---+\s*$/.test(trimmed)) {
      flushList();
      result.push(<hr key={'hr-' + i} className="my-5 border-slate-200 dark:border-slate-700" />);
      return;
    }
    let html = applyInline(line);

    if (/^####\s/.test(html)) { flushList(); result.push(<h4 key={'h4-' + i} className="text-[15px] font-semibold mt-4 mb-1.5 text-slate-800 dark:text-slate-100" dangerouslySetInnerHTML={{ __html: html.slice(5) }} />); }
    else if (/^###\s/.test(html)) { flushList(); result.push(<h3 key={'h3-' + i} className="text-lg font-bold mt-5 mb-2 text-slate-900 dark:text-white" dangerouslySetInnerHTML={{ __html: html.slice(4) }} />); }
    else if (/^##\s/.test(html)) { flushList(); result.push(<h2 key={'h2-' + i} className="text-xl font-bold mt-6 mb-2.5 text-slate-900 dark:text-white border-b border-slate-200 dark:border-slate-700 pb-2" dangerouslySetInnerHTML={{ __html: html.slice(3) }} />); }
    else if (/^\d+\.\s/.test(html)) { if (listType !== 'ol') { flushList(); listType = 'ol'; } listItems.push(html.replace(/^\d+\.\s/, '')); }
    else if (/^\*\s/.test(html) || /^-\s/.test(html)) { if (listType !== 'ul') { flushList(); listType = 'ul'; } listItems.push(html.slice(2)); }
    else if (/^\|.+\|$/.test(trimmed)) {
      flushList();
      const cells = trimmed.split('|').slice(1, -1).map(c => applyInline(c.trim()));
      tableRows.push(cells);
    }
    else if (html.trim() === '') { flushList(); flushTable(); }
    else { flushList(); flushTable(); result.push(<p key={'p-' + i} className="leading-7 min-h-[0.5rem]" dangerouslySetInnerHTML={{ __html: html }} />); }
  });
  flushList(); flushTable();
  return result;
}

/* ── tiny components ── */
function UserAvatar() {
  return (
    <div className="w-10 h-10 rounded-full bg-gradient-to-br from-violet-500 to-fuchsia-500 flex items-center justify-center text-white text-sm font-bold shrink-0 shadow-md shadow-violet-500/20 ring-2 ring-white dark:ring-slate-900">
      U
    </div>
  );
}
function BotAvatar({ model }: { model: string }) {
  const m: Record<string, string> = {
    gpt: 'from-emerald-400 to-teal-500',
    zhipu: 'from-blue-400 to-indigo-500',
    qwen: 'from-orange-400 to-rose-500',
    deepseek: 'from-cyan-400 to-sky-500',
    openai: 'from-pink-400 to-fuchsia-500',
  };
  const g = m[model] ?? m.gpt;
  return (
    <div className={`w-10 h-10 rounded-full bg-gradient-to-br ${g} flex items-center justify-center text-white text-xs font-bold shrink-0 shadow-md ring-2 ring-white dark:ring-slate-900`}>
      AI
    </div>
  );
}
function TypingDots() {
  return (
    <div className="flex items-center gap-1.5 px-1 py-2">
      {[0, 1, 2].map((i) => (
        <span key={i} className="w-2.5 h-2.5 rounded-full bg-slate-300 dark:bg-slate-600 animate-bounce"
              style={{ animationDelay: `${i * 160}ms`, animationDuration: '0.7s' }} />
      ))}
    </div>
  );
}
function EmptyState({ onSuggestion }: { onSuggestion: (text: string) => void }) {
  return (
    <div className="flex flex-col items-center justify-center h-full px-6 animate-fadeIn select-none">
      <div className="relative mb-10">
        <div className="w-24 h-24 rounded-3xl bg-gradient-to-br from-blue-500 via-violet-500 to-fuchsia-500 flex items-center justify-center shadow-2xl shadow-violet-500/20">
          <svg className="w-12 h-12 text-white/90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.6}
              d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
          </svg>
        </div>
        <div className="absolute -bottom-1 -right-1 w-5 h-5 rounded-full bg-emerald-400 border-[3px] border-white dark:border-slate-950 animate-pulse" />
      </div>
      <h2 className="text-2xl font-semibold text-slate-800 dark:text-slate-100 mb-3 tracking-tight">AI-CodeHub</h2>
      <p className="text-[15px] text-slate-500 dark:text-slate-400 mb-10">选择模型，开始对话</p>
      <div className="flex flex-wrap justify-center gap-2.5 max-w-md">
        {SUGGESTIONS.map((s) => (
          <button key={s} onClick={() => onSuggestion(s)}
            className="px-4 py-2.5 text-sm text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800/70 border border-slate-200 dark:border-slate-700/50 rounded-2xl
                       hover:bg-slate-50 dark:hover:bg-slate-700/80 hover:border-slate-300 dark:hover:border-slate-600
                       shadow-sm transition-all duration-200">
            {s}
          </button>
        ))}
      </div>
    </div>
  );
}

/* ── main ── */
export default function ChatInterface() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [model, setModel] = useState('deepseek');
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const { id } = useParams();
  const navigate = useNavigate();
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [conversationSlug, setConversationSlug] = useState<string | null>(id || null);
  const [modalOpen, setModalOpen] = useState(false);
  const [agentMode, setAgentMode] = useState(false);
  const [clock, setClock] = useState(fmtClock);
  const scrollRef = useRef<HTMLDivElement>(null);

  const { startStream, abort } = useChatStream();
  const { isLoggedIn, isAdmin, token } = useAuthStore();
  const { theme, toggle: toggleTheme } = useThemeStore();

  useEffect(() => { const id = setInterval(() => setClock(fmtClock()), 1000); return () => clearInterval(id); }, []);
  useEffect(() => { (window as any).__openLoginModal = () => setModalOpen(true); return () => { delete (window as any).__openLoginModal; }; }, []);
  useEffect(() => { const t = setTimeout(() => { if (!isLoggedIn) setModalOpen(true); }, 300); return () => clearTimeout(t); }, [isLoggedIn]);
  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' }); }, [messages]);

  // Resolve slug to conversation ID
  useEffect(() => {
    if (!token || !conversationSlug) return;
    apiFetch('/api/v1/conversations', { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.json()).then(d => {
        const conv = (d.data ?? []).find((c: { slug: string }) => c.slug === conversationSlug);
        if (conv) { setConversationId(conv.id); setConversationSlug(null); }
      });
  }, [token, conversationSlug]);

  useEffect(() => {
    if (!token || !conversationId) return;
    apiFetch(`/api/v1/conversations/${conversationId}/messages`, { headers: { Authorization: `Bearer ${token}` } })
      .then((r) => r.json()).then((d) => {
        const msgs = (d.data ?? []).map((m: { id: number; role: string; content: string; createdAt: string; inputTokens?: number; outputTokens?: number }) => ({
          id: String(m.id), role: m.role as 'user' | 'assistant', content: m.content, model,
          timestamp: new Date(m.createdAt).getTime(), inputTokens: m.inputTokens ?? undefined, outputTokens: m.outputTokens ?? undefined,
        }));
        if (msgs.length > 0) setMessages(msgs);
      });
  }, [token, conversationId]); // eslint-disable-line react-hooks/exhaustive-deps

  const newConversation = useCallback(() => { setMessages([]); setConversationId(null); setConversationSlug(null); navigate('/chat', { replace: true }); }, [navigate]);

  const sendWithConvId = (text: string, cid: number) => {
    const now = Date.now();
    const um: Message = { id: uid(), role: 'user', content: text, timestamp: now };
    const am: Message = { id: uid(), role: 'assistant', content: '', model, timestamp: now };
    setMessages((prev) => [...prev, um, am]); setInput('');
    const base = agentMode ? '/api/v1/agent/chat' : '/api/v1/chat/stream';
    const url = `${base}?prompt=${encodeURIComponent(text)}&modelType=${model}&conversationId=${cid}`;
    setStreaming(true);
    startStream(url,
      (c) => setMessages((p) => { const u = [...p]; const l = u[u.length - 1]; if (l?.role === 'assistant') u[u.length - 1] = { ...l, content: l.content + c }; return u; }),
      () => setStreaming(false),
      (err) => { setMessages((p) => { const u = [...p]; const l = u[u.length - 1]; if (l?.role === 'assistant') u[u.length - 1] = { ...l, content: `请求失败：${err.message}` }; return u; }); setStreaming(false); },
      (i, o) => setMessages((p) => { const u = [...p]; const l = u[u.length - 1]; if (l?.role === 'assistant') u[u.length - 1] = { ...l, inputTokens: i, outputTokens: o }; return u; }),
    );
  };

  const sendMessage = useCallback((text: string, useAgent: boolean) => {
    if (!text.trim() || streaming) return;
    const now = Date.now();
    setMessages((prev) => [...prev, { id: uid(), role: 'user', content: text, timestamp: now }, { id: uid(), role: 'assistant', content: '', model, timestamp: now }]);
    setInput('');
    const base = useAgent ? '/api/v1/agent/chat' : '/api/v1/chat/stream';
    const params = `prompt=${encodeURIComponent(text)}&modelType=${model}`;
    const url = isLoggedIn && conversationId ? `${base}?${params}&conversationId=${conversationId}` : `${base}?${params}`;
    setStreaming(true);
    startStream(url,
      (c) => setMessages((p) => { const u = [...p]; const l = u[u.length - 1]; if (l?.role === 'assistant') u[u.length - 1] = { ...l, content: l.content + c }; return u; }),
      () => setStreaming(false),
      (err) => { setMessages((p) => { const u = [...p]; const l = u[u.length - 1]; if (l?.role === 'assistant') u[u.length - 1] = { ...l, content: `请求失败：${err.message}` }; return u; }); setStreaming(false); },
      (i, o) => setMessages((p) => { const u = [...p]; const l = u[u.length - 1]; if (l?.role === 'assistant') u[u.length - 1] = { ...l, inputTokens: i, outputTokens: o }; return u; }),
    );
  }, [model, streaming, startStream, isLoggedIn, conversationId]);

  const handleSendOrCreate = async () => {
    const text = input.trim(); if (!text || streaming) return;
    if (isLoggedIn && !conversationId) {
      const res = await apiFetch('/api/v1/conversations', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token!}` }, body: JSON.stringify({ model, title: text }) });
      const data = await res.json();
      if (data.code === 200 && data.data?.id) { setConversationId(data.data.id); navigate('/chat/'+data.data.slug, { replace: true }); sendWithConvId(text, data.data.id); return; }
    }
    sendMessage(text, agentMode);
  };

  const handleStop = () => { abort(); setStreaming(false); };
  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSendOrCreate(); } };

  const lastMsg = messages[messages.length - 1];
  const isTyping = streaming && lastMsg?.role === 'assistant' && lastMsg.content === '';

  return (
    <div className="flex h-screen bg-white dark:bg-[#0b0f14] text-slate-800 dark:text-slate-200 transition-colors duration-500">
      {/* === gradient aura === */}
      <div className="fixed inset-0 -z-10 pointer-events-none overflow-hidden">
        <div className="absolute -top-1/2 left-1/4 w-[60rem] h-[60rem] rounded-full bg-gradient-to-br from-blue-100/40 via-transparent to-violet-100/30 dark:from-blue-950/30 dark:via-transparent dark:to-violet-950/20 blur-3xl opacity-50" />
        <div className="absolute -bottom-1/3 right-1/4 w-[50rem] h-[50rem] rounded-full bg-gradient-to-tl from-cyan-100/30 via-transparent to-rose-100/20 dark:from-cyan-950/20 dark:via-transparent dark:to-rose-950/15 blur-3xl opacity-40" />
      </div>

      {/* === sidebar === */}
      <Sidebar token={token} activeId={conversationId} onSelect={(id, slug) => { setConversationId(id); navigate('/chat/'+slug, { replace: true }); }} onNew={newConversation} />

      {/* === body === */}
      <main className="flex-1 flex flex-col min-w-0">
        {/* header */}
        <header className="shrink-0 border-b border-slate-200/60 dark:border-slate-800/40 bg-white/60 dark:bg-[#0b0f14]/70 backdrop-blur-2xl transition-colors">
          <div className="flex items-center justify-between px-8 h-16 w-full">
            {/* left */}
            <div className="flex items-center gap-3.5 min-w-0 shrink-0">
              <LogoIcon size={36} />
              <a href="/chat" className="text-lg font-bold text-slate-800 dark:text-slate-100 tracking-tight hover:opacity-80 transition-opacity">AI-CodeHub</a>
              <span className="hidden md:block text-[13px] text-slate-400 dark:text-slate-500 ml-3 pl-3 border-l border-slate-200 dark:border-slate-700">
                多模型对话 · Agent · RAG
              </span>
            </div>
            {/* right */}
            <div className="flex items-center gap-2">
              <span className="hidden sm:flex items-center gap-1.5 text-[13px] font-mono font-medium text-slate-500 dark:text-slate-400 bg-slate-100/80 dark:bg-slate-800/50 px-3 py-1.5 rounded-xl backdrop-blur-sm">
                <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
                {clock}
              </span>

              <button onClick={() => { if (document.fullscreenElement) { document.exitFullscreen(); } else { document.documentElement.requestFullscreen(); } }}
                className="flex items-center gap-1 h-9 px-2.5 rounded-xl text-[13px] font-medium text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-all"
                title="全屏">
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5l-5-5m5 5v-4m0 4h-4"/></svg>
              </button>
              <button onClick={toggleTheme}
                className="flex items-center gap-1.5 h-9 px-3 rounded-xl text-[13px] font-medium text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-all"
                title={theme === 'dark' ? '浅色模式' : '深色模式'}>
                {theme === 'dark' ? <SunIcon size={18} /> : <MoonIcon size={17} />}
                <span>{theme === 'dark' ? '浅色' : '深色'}</span>
              </button>

              <a href="/copilot"
                className="flex items-center gap-1.5 h-9 px-3 rounded-xl text-[13px] font-medium text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-all"
                title="Copilot 工作台">
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" /></svg>
                工作台
              </a>

              {isLoggedIn && (
                <a href="/rag"
                  className="flex items-center gap-1.5 h-9 px-3 rounded-xl text-[13px] font-medium text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-all"
                  title="知识库">
                  <DocIcon size={17} />知识库
                </a>
              )}

              {isAdmin && (
                <a href="/admin" className="h-9 px-3.5 flex items-center text-[13px] font-semibold text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 rounded-xl hover:bg-amber-100 dark:hover:bg-amber-500/20 transition-all">管理</a>
              )}

            </div>
          </div>
        </header>

        {/* messages */}
        <div ref={scrollRef} className="flex-1 overflow-y-auto scroll-smooth">
          {messages.length === 0 ? (
            <EmptyState onSuggestion={(t) => { setInput(t); if (isLoggedIn) setTimeout(() => handleSendOrCreate(), 60); else sendMessage(t, agentMode); }} />
          ) : (
            <div className="w-full mx-auto px-6 py-8 space-y-6">
              {messages.map((msg) => (
                <div key={msg.id} className={`flex gap-4 animate-fadeIn ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}>
                  {msg.role === 'user' ? <UserAvatar /> : <BotAvatar model={msg.model ?? 'deepseek'} />}
                  <div className={`flex-1 flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'}`}>
                    <div className={`max-w-[88%] rounded-2xl px-5 py-3.5 text-[15px] leading-7 ${
                      msg.role === 'user'
                        ? 'bg-gradient-to-br from-indigo-600 to-blue-600 text-white rounded-br-md shadow-lg shadow-indigo-500/20'
                        : 'bg-white dark:bg-slate-800/40 text-slate-800 dark:text-slate-100 rounded-bl-md border border-slate-100 dark:border-slate-700/20 shadow-sm'
                    }`}>
                      {msg.role === 'assistant' && msg.model && (
                        <div className="text-[11px] font-semibold text-slate-400 dark:text-slate-500 mb-2 uppercase tracking-wide">
                          {MODEL_MAP[msg.model ?? ''] ?? msg.model}
                        </div>
                      )}
                      <div className="break-words">
                        {msg.content ? renderMarkdown(msg.content) : (msg === lastMsg && isTyping ? <TypingDots /> : null)}
                        {msg.role === 'assistant' && streaming && msg === lastMsg && msg.content && (
                          <span className="inline-block w-[3px] h-5 ml-0.5 bg-blue-400 rounded-full align-text-bottom animate-pulse" />
                        )}
                      </div>
                    </div>
                    <div className={`flex items-center gap-3 mt-2 ${msg.role === 'user' ? 'flex-row-reverse mr-2' : 'ml-2'}`}>
                      <span className="text-[12px] text-slate-400 dark:text-slate-600">{fmtTime(msg.timestamp)}</span>
                      {msg.role === 'assistant' && msg.inputTokens != null && (
                        <span className="text-[12px] font-medium text-slate-500 dark:text-slate-500 bg-slate-100 dark:bg-slate-800/50 px-2 py-0.5 rounded-lg">
                          输入 {msg.inputTokens} · 输出 {msg.outputTokens} tokens
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
              {streaming && lastMsg?.role === 'user' && (
                <div className="flex gap-4 animate-fadeIn">
                  <BotAvatar model={model} />
                  <div className="bg-white dark:bg-slate-800/60 rounded-2xl rounded-bl-lg border border-slate-200/60 dark:border-slate-700/30 px-5 py-3.5 shadow-sm">
                    <TypingDots />
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* input */}
        <div className="shrink-0 border-t border-slate-200/60 dark:border-slate-800/40 bg-white/60 dark:bg-[#0b0f14]/70 backdrop-blur-2xl transition-colors">
          <div className="w-full mx-auto px-5 py-4 space-y-3">
            {/* toolbar */}
            <div className="flex items-center gap-2">
              <ModelSelector value={model} onChange={setModel} />
              <button onClick={() => setAgentMode(!agentMode)}
                className={`h-8 px-3.5 text-[13px] font-semibold rounded-xl transition-all duration-200 ${
                  agentMode ? 'bg-violet-600 text-white shadow-md shadow-violet-500/20' : 'text-slate-500 dark:text-slate-400 bg-slate-100/80 dark:bg-slate-800/50 border border-slate-200/60 dark:border-slate-700/40 hover:border-slate-300 dark:hover:border-slate-600'
                }`}>
                {agentMode ? 'Agent' : '对话'}
              </button>
              <span className="text-[12px] text-slate-400 dark:text-slate-500 hidden sm:inline">
                {agentMode ? '工具调用 · 知识库检索' : 'Enter 发送 · Shift+Enter 换行'}
              </span>
            </div>
            {/* input row */}
            <div className="flex items-center gap-3 bg-slate-100/80 dark:bg-slate-800/40 rounded-2xl border border-slate-200/60 dark:border-slate-700/30 px-4 py-2.5
                            focus-within:border-blue-400/50 dark:focus-within:border-blue-500/40 focus-within:ring-4 focus-within:ring-blue-500/10 dark:focus-within:ring-blue-500/5 transition-all duration-200">
              <input value={input} onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown} disabled={streaming}
                placeholder="输入消息，Enter 发送…"
                className="flex-1 bg-transparent px-1 text-[15px] placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none disabled:opacity-50 text-slate-800 dark:text-slate-200 leading-relaxed" />
              {streaming ? (
                <button onClick={handleStop}
                  className="flex items-center gap-1.5 h-9 px-4 text-[13px] font-semibold text-white bg-rose-500 hover:bg-rose-600 rounded-xl transition-colors shrink-0 shadow-sm">
                  <StopIcon size={15} />停止
                </button>
              ) : (
                <button onClick={handleSendOrCreate} disabled={!input.trim()}
                  className="flex items-center gap-1.5 h-9 px-4 text-[13px] font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-xl
                             disabled:bg-slate-300 dark:disabled:bg-slate-700 disabled:text-slate-400 disabled:cursor-not-allowed
                             transition-all duration-200 shrink-0 shadow-sm shadow-blue-600/20">
                  <SendIcon size={15} />发送
                </button>
              )}
            </div>
          </div>
        </div>
      </main>
      <LoginModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </div>
  );
}
