import { useState, useEffect } from 'react';
import { useAuthStore } from '../store/authStore';

interface Props { open: boolean; onClose: () => void; }

const AVATARS = ['👤','🐱','🐶','🦊','🐼','🐨','🦁','🐸','🐵','🐯','🦄','🐙','🐳','🦋','🐞','🌸','🌻','⚡','🔥','💎'];

export default function ProfileModal({ open, onClose }: Props) {
  const token = useAuthStore((s) => s.token);
  const storeAvatar = useAuthStore((s) => s.avatar);
  const username = useAuthStore((s) => s.username);
  const setProfile = useAuthStore((s) => s.setProfile);
  const [name, setName] = useState(username || '');
  const [avatar, setAvatar] = useState(storeAvatar || '👤');
  const [msg, setMsg] = useState('');

  useEffect(() => {
    if (!open || !token) return;
    fetch('/api/v1/user/profile', { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.json()).then(d => {
        if (d.code === 200) { setName(d.data.username); if (d.data.avatar) setAvatar(d.data.avatar); }
      });
  }, [open, token]);

  if (!open) return null;

  const save = async () => {
    const res = await fetch('/api/v1/user/profile', {
      method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ username: name, avatar }),
    });
    const d = await res.json();
    if (d.code === 200) {
      setProfile(name, avatar);
      setMsg('已更新');
      setTimeout(() => { setMsg(''); onClose(); }, 800);
    } else setMsg(d.message || '更新失败');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" onClick={onClose}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />
      <div className="relative w-full max-w-sm bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl p-6 shadow-2xl animate-fadeIn" onClick={e => e.stopPropagation()}>
        <h3 className="text-base font-semibold text-slate-800 dark:text-slate-200 mb-4">编辑资料</h3>
        <div className="flex flex-col items-center mb-4">
          <div className="w-20 h-20 rounded-full bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center text-4xl text-white mb-3 overflow-hidden">
            {(avatar.startsWith('http') || avatar.startsWith('/minio')) ? <img src={avatar} className="w-full h-full object-cover" alt="" /> : <span>{avatar}</span>}
          </div>
          <p className="text-xs text-slate-400">选择头像</p>
          <div className="flex flex-wrap gap-1.5 justify-center mt-2">
            {AVATARS.map(a => (
              <button key={a} onClick={() => setAvatar(a)} className={`text-2xl p-1 rounded-lg transition-all ${avatar === a ? 'bg-blue-100 dark:bg-blue-500/20 ring-2 ring-blue-400' : 'hover:bg-slate-100 dark:hover:bg-slate-800'}`}>{a}</button>
            ))}
          </div>
          <label className="mt-2 text-xs text-blue-500 hover:text-blue-400 cursor-pointer">
            📷 上传图片
            <input type="file" accept="image/*" className="hidden" onChange={async e => {
              const f = e.target.files?.[0]; if (!f || !token) return;
              const form = new FormData(); form.append('file', f);
              const res = await fetch('/api/v1/user/avatar', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form });
              const d = await res.json();
              if (d.code === 200) { setAvatar(d.data.avatar); setProfile(name, d.data.avatar); }
            }} />
          </label>
        </div>
        <div className="space-y-3">
          <div><label className="text-xs text-slate-400 block mb-1">用户名</label><input value={name} onChange={e => setName(e.target.value)} className="w-full px-3 py-2 rounded-lg border text-sm focus:outline-none focus:ring-1 focus:ring-blue-400 dark:bg-slate-800 dark:border-slate-700 dark:text-slate-200" /></div>
          {msg && <p className={`text-xs ${msg.includes('更新') ? 'text-green-500' : 'text-red-400'}`}>{msg}</p>}
          <button onClick={save} className="w-full py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors">保存</button>
        </div>
      </div>
    </div>
  );
}
