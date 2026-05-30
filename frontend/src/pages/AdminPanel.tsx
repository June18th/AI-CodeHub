import { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';

interface Application {
  id: number;
  username: string;
  email: string;
  applyReason: string;
  createdAt: string;
}

export default function AdminPanel() {
  const token = useAuthStore((s) => s.token);
  const [apps, setApps] = useState<Application[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState('');

  const fetchApps = () => {
    setLoading(true);
    fetch('/api/v1/admin/applications', { headers: { Authorization: `Bearer ${token}` } })
      .then((r) => r.json())
      .then((d) => setApps(d.data?.records ?? []))
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchApps(); }, []);

  const review = (userId: number, action: string, role?: string) => {
    fetch('/api/v1/admin/review', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token!}` },
      body: JSON.stringify({ userId, action, role }),
    })
      .then((r) => r.json())
      .then((d) => {
        if (d.code === 200) {
          setMsg(`已${action === 'approve' ? '通过' : '驳回'}`);
          fetchApps();
        } else {
          setMsg(d.message ?? '操作失败');
        }
      });
  };

  return (
    <div className="flex flex-col h-screen bg-slate-950">
      <div className="fixed inset-0 -z-10">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-900 via-slate-950 to-slate-950" />
      </div>
      <div className="max-w-4xl mx-auto w-full px-4 py-8">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-xl font-semibold text-slate-200">运营管理平台</h1>
          <button
            onClick={() => window.location.href = '/'}
            className="text-sm text-slate-400 hover:text-white transition-colors"
          >← 返回对话</button>
        </div>

        {msg && (
          <div className="mb-4 px-4 py-2 bg-green-500/10 border border-green-500/30 rounded-xl text-sm text-green-400">
            {msg}
          </div>
        )}

        <div className="bg-slate-900/60 border border-slate-700/30 rounded-2xl overflow-hidden">
          <div className="px-5 py-3 border-b border-slate-700/30">
            <h2 className="text-sm font-medium text-slate-300">用户申请审核</h2>
          </div>
          {loading ? (
            <div className="p-8 text-center text-slate-500 text-sm">加载中...</div>
          ) : apps.length === 0 ? (
            <div className="p-8 text-center text-slate-500 text-sm">暂无待审核申请</div>
          ) : (
            <div className="divide-y divide-slate-800">
              {apps.map((a) => (
                <div key={a.id} className="px-5 py-4 flex items-center justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-medium text-slate-200">{a.username}</span>
                      <span className="text-xs text-slate-500">{a.email}</span>
                    </div>
                    {a.applyReason && (
                      <p className="text-xs text-slate-400 truncate">
                        申请理由：{a.applyReason}
                      </p>
                    )}
                    <p className="text-xs text-slate-600 mt-1">{a.createdAt}</p>
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <button
                      onClick={() => review(a.id, 'approve', 'user')}
                      className="px-3 py-1.5 text-xs font-medium bg-emerald-600/80 hover:bg-emerald-500 text-white rounded-lg transition-colors"
                    >通过为普通用户</button>
                    <button
                      onClick={() => review(a.id, 'approve', 'beta')}
                      className="px-3 py-1.5 text-xs font-medium bg-blue-600/80 hover:bg-blue-500 text-white rounded-lg transition-colors"
                    >通过为内测用户</button>
                    <button
                      onClick={() => review(a.id, 'reject')}
                      className="px-3 py-1.5 text-xs font-medium bg-red-600/20 hover:bg-red-600/40 text-red-400 rounded-lg transition-colors"
                    >驳回</button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
