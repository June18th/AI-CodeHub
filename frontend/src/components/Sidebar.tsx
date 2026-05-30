import { useState, useEffect } from 'react';

interface Conversation {
  id: number;
  title: string;
  model: string;
  updatedAt?: string;
}

interface Props {
  token: string | null;
  activeId: number | null;
  onSelect: (id: number) => void;
  onNew: () => void;
}

export default function Sidebar({ token, activeId, onSelect, onNew }: Props) {
  const [collapsed, setCollapsed] = useState(false);
  const [list, setList] = useState<Conversation[]>([]);
  const [editId, setEditId] = useState<number | null>(null);
  const [editTitle, setEditTitle] = useState('');

  useEffect(() => {
    if (!token) return;
    fetch('/api/v1/conversations', { headers: { Authorization: `Bearer ${token}` } })
      .then((r) => r.json())
      .then((d) => setList(d.data ?? []));
  }, [token, activeId]);

  const del = async (id: number) => {
    await fetch(`/api/v1/conversations/${id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    setList((prev) => prev.filter((c) => c.id !== id));
    if (activeId === id) onNew();
  };

  const startRename = (c: Conversation) => {
    setEditId(c.id);
    setEditTitle(c.title);
  };

  const submitRename = async () => {
    if (editId && editTitle.trim()) {
      await fetch(`/api/v1/conversations/${editId}/rename`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ title: editTitle.trim() }),
      });
      setList((prev) => prev.map((c) => (c.id === editId ? { ...c, title: editTitle.trim() } : c)));
    }
    setEditId(null);
  };

  if (collapsed) {
    return (
      <button
        onClick={() => setCollapsed(false)}
        className="shrink-0 w-10 flex flex-col items-center pt-3 text-slate-400 dark:text-slate-500 hover:text-slate-700 dark:hover:text-white transition-colors
                   border-r border-slate-200 dark:border-slate-800/80 bg-white/70 dark:bg-slate-950/70"
        title="展开对话列表"
      >
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 5l7 7-7 7M5 5l7 7-7 7" />
        </svg>
      </button>
    );
  }

  return (
    <aside className="shrink-0 w-64 flex flex-col border-r border-slate-200 dark:border-slate-800/80 bg-white/70 dark:bg-slate-950/70 backdrop-blur-xl transition-colors">
      <div className="flex items-center justify-between px-4 h-14 border-b border-slate-200 dark:border-slate-800/50">
        <span className="text-xs font-medium text-slate-500 dark:text-slate-400">对话记录</span>
        <button onClick={() => setCollapsed(true)} className="text-slate-400 dark:text-slate-500 hover:text-slate-700 dark:hover:text-white transition-colors">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
          </svg>
        </button>
      </div>

      <button
        onClick={onNew}
        className="mx-3 mt-3 mb-2 px-3 py-2 text-xs text-slate-600 dark:text-slate-300 bg-slate-100 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/50 rounded-xl
                   hover:bg-slate-200 dark:hover:bg-slate-700/60 transition-colors text-left"
      >
        + 新对话
      </button>

      <div className="flex-1 overflow-y-auto px-2 space-y-0.5">
        {token ? (
          list.length === 0 ? (
            <p className="px-2 py-4 text-xs text-slate-400 dark:text-slate-600 text-center">暂无对话</p>
          ) : (
            list.map((c) => (
              <div
                key={c.id}
                onClick={() => onSelect(c.id)}
                className={`group flex items-center gap-2 px-3 py-2.5 rounded-xl cursor-pointer transition-colors text-sm ${
                  c.id === activeId
                    ? 'bg-slate-200 dark:bg-slate-800 text-slate-900 dark:text-slate-200'
                    : 'text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800/40 hover:text-slate-700 dark:hover:text-slate-300'
                }`}
              >
                {editId === c.id ? (
                  <input
                    autoFocus
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                    onBlur={submitRename}
                    onKeyDown={(e) => { if (e.key === 'Enter') submitRename(); }}
                    onClick={(e) => e.stopPropagation()}
                    className="flex-1 bg-slate-700 text-slate-200 text-sm px-1.5 py-0.5 rounded outline-none border border-blue-500/50"
                  />
                ) : (
                  <span
                    className="flex-1 truncate"
                    onDoubleClick={(e) => { e.stopPropagation(); startRename(c); }}
                    title="双击修改标题"
                  >{c.title}</span>
                )}
                <button
                  onClick={(e) => { e.stopPropagation(); del(c.id); }}
                  className="opacity-0 group-hover:opacity-100 text-slate-500 hover:text-red-400 transition-all shrink-0"
                >
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
            ))
          )
        ) : (
          <p className="px-2 py-4 text-xs text-slate-400 dark:text-slate-600 text-center">登录后可管理对话</p>
        )}
      </div>

      <div className="px-3 py-3 border-t border-slate-200 dark:border-slate-800/50">
        <p className="text-[10px] text-slate-400 dark:text-slate-600 text-center">上下文窗口：最近 20 条</p>
      </div>
    </aside>
  );
}
