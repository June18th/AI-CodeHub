import { useState, useEffect } from 'react';
import { AddIcon, TrashIcon, ChevronLeftIcon } from './Icons';
import { useAuthStore } from '../store/authStore';
import ProfileModal from './ProfileModal';

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
  const [profileOpen, setProfileOpen] = useState(false);
  const { isLoggedIn, username, avatar } = useAuthStore();

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
        <span className="rotate-180"><ChevronLeftIcon size={20} /></span>
      </button>
    );
  }

  return (
    <aside className="shrink-0 w-64 flex flex-col border-r border-slate-100 dark:border-slate-800/40 bg-white/80 dark:bg-[#0b0f14]/80 backdrop-blur-2xl transition-colors">
      <div className="flex items-center justify-between px-4 h-14 border-b border-slate-200 dark:border-slate-800/50">
        <span className="text-xs font-medium text-slate-500 dark:text-slate-400">对话记录</span>
        <button onClick={() => setCollapsed(true)} className="text-slate-400 dark:text-slate-500 hover:text-slate-700 dark:hover:text-white transition-colors" title="收起侧边栏">
          <ChevronLeftIcon size={18} />
        </button>
      </div>

      <button
        onClick={onNew}
        className="mx-3 mt-3 mb-2 px-3 py-2 text-xs font-medium text-indigo-600 dark:text-indigo-300 bg-indigo-50/50 dark:bg-indigo-500/5 border border-indigo-100 dark:border-indigo-500/10 rounded-xl
                   hover:bg-indigo-100 dark:hover:bg-indigo-500/10 transition-all text-left"
      >
        <AddIcon size={14} /> 新对话
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
                    ? 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 font-medium'
                    : 'text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800/30 hover:text-slate-700 dark:hover:text-slate-300'
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
                  className="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-red-400 transition-all shrink-0"
                >
                  <TrashIcon size={14} />
                </button>
              </div>
            ))
          )
        ) : (
          <p className="px-2 py-4 text-xs text-slate-400 dark:text-slate-600 text-center">登录后可管理对话</p>
        )}
      </div>

      {isLoggedIn ? (
        <div className="border-t border-slate-200 dark:border-slate-800/50 px-4 py-3 flex items-center gap-3">
          <button onClick={() => setProfileOpen(true)} className="flex items-center gap-3 flex-1 min-w-0 hover:bg-slate-100 dark:hover:bg-slate-800/50 rounded-xl px-2 py-1.5 -ml-2 transition-colors">
            <div className="w-9 h-9 rounded-full bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center text-white text-base shrink-0 overflow-hidden">
              {avatar ? ((avatar.startsWith('http') || avatar.startsWith('/minio')) ? <img src={avatar} className="w-full h-full object-cover" alt="" /> : <span>{avatar}</span>) : (username?.charAt(0)?.toUpperCase() || 'U')}
            </div>
            <span className="text-sm font-medium text-slate-700 dark:text-slate-200 truncate">{username}</span>
          </button>
          <button onClick={() => { useAuthStore.getState().logout(); }} className="shrink-0 w-8 h-8 flex items-center justify-center rounded-lg text-slate-400 hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-500/10 transition-colors" title="退出登录">
            <svg className="w-4.5 h-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/></svg>
          </button>
        </div>
      ) : (
        <div className="border-t border-slate-200 dark:border-slate-800/50 px-3 py-2.5">
          <button onClick={() => { (window as any).__openLoginModal?.(); }} className="w-full py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-xl transition-colors shadow-sm">
            登录
          </button>
        </div>
      )}
      <ProfileModal open={profileOpen} onClose={() => setProfileOpen(false)} />
    </aside>
  );
}
