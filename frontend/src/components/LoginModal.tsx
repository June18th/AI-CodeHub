import { useState } from 'react';
import { useAuthStore } from '../store/authStore';

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function LoginModal({ open, onClose }: Props) {
  const [tab, setTab] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [reason, setReason] = useState('');
  const [msg, setMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const login = useAuthStore((s) => s.login);

  if (!open) return null;

  const reset = () => {
    setUsername('');
    setEmail('');
    setPassword('');
    setReason('');
    setMsg('');
  };

  const switchTab = (t: 'login' | 'register') => {
    setTab(t);
    reset();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMsg('');
    try {
      if (tab === 'login') {
        const res = await fetch('/api/v1/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password }),
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message ?? '登录失败');
        login(data.data.token, data.data.role, data.data.username);
        onClose();
        reset();
      } else {
        const res = await fetch('/api/v1/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, email, password, applyReason: reason }),
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message ?? '注册失败');
        setMsg('申请已提交，请等待管理员审核');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '请求失败';
      setMsg(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-sm bg-slate-900 border border-slate-700/50 rounded-2xl p-6 shadow-2xl animate-fadeIn">
        {/* Tabs */}
        <div className="flex rounded-xl bg-slate-800/60 p-1 mb-6">
          <button
            onClick={() => switchTab('login')}
            className={`flex-1 py-2 text-sm font-medium rounded-lg transition-all ${
              tab === 'login' ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-400'
            }`}
          >登录</button>
          <button
            onClick={() => switchTab('register')}
            className={`flex-1 py-2 text-sm font-medium rounded-lg transition-all ${
              tab === 'register' ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-400'
            }`}
          >注册</button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <label className="block">
            <span className="text-xs text-slate-400 ml-1 mb-1 block">账号</span>
            <input
              type="text" placeholder="请输入用户名" required value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-4 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-sm text-slate-200
                         placeholder:text-slate-500 focus:outline-none focus:border-blue-500/50 focus:ring-2 focus:ring-blue-500/20"
            />
          </label>
          {tab === 'register' && (
            <label className="block">
              <span className="text-xs text-slate-400 ml-1 mb-1 block">邮箱</span>
              <input
                type="email" placeholder="请输入邮箱" required value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-4 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-sm text-slate-200
                           placeholder:text-slate-500 focus:outline-none focus:border-blue-500/50 focus:ring-2 focus:ring-blue-500/20"
              />
            </label>
          )}
          <label className="block">
            <span className="text-xs text-slate-400 ml-1 mb-1 block">密码</span>
            <input
              type="password" placeholder="请输入密码" required value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-sm text-slate-200
                         placeholder:text-slate-500 focus:outline-none focus:border-blue-500/50 focus:ring-2 focus:ring-blue-500/20"
            />
          </label>
          {tab === 'register' && (
            <label className="block">
              <span className="text-xs text-slate-400 ml-1 mb-1 block">申请理由</span>
              <textarea
                placeholder="选填" value={reason} rows={2}
                onChange={(e) => setReason(e.target.value)}
                className="w-full px-4 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-sm text-slate-200
                           placeholder:text-slate-500 focus:outline-none focus:border-blue-500/50 focus:ring-2 focus:ring-blue-500/20 resize-none"
              />
            </label>
          )}

          {msg && (
            <p className={`text-xs ${msg.includes('成功') || msg.includes('提交') ? 'text-green-400' : 'text-red-400'}`}>
              {msg}
            </p>
          )}

          <button
            type="submit" disabled={loading}
            className="w-full py-2.5 bg-gradient-to-r from-blue-600 to-blue-500 text-white text-sm font-medium rounded-xl
                       hover:from-blue-500 hover:to-blue-400 disabled:opacity-50 transition-all shadow-lg shadow-blue-600/25"
          >
            {loading ? '处理中...' : tab === 'login' ? '登录' : '提交申请'}
          </button>
        </form>
      </div>
    </div>
  );
}
