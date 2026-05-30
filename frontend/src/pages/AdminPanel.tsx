import { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { fmtDate } from '../utils/format';

interface Application { id: number; username: string; email: string; applyReason: string; role: string; status: string; createdAt: string; }

interface Dashboard {
  todayCalls: number; activeUsers: number; todayTokens: number; todayErrors: number;
  avgLatency: number; errorRate: number;
  modelUsage: { model: string; cnt: number }[];
  hourlyTrend: { hour: number; cnt: number }[];
  recentLogs: { id: number; username: string; model: string; inputTokens: number; outputTokens: number; latencyMs: number; status: string; createdAt: string }[];
}

export default function AdminPanel() {
  const token = useAuthStore((s) => s.token);
  const [tab, setTab] = useState<'dashboard' | 'review'>('dashboard');
  const [apps, setApps] = useState<Application[]>([]);
  const [dash, setDash] = useState<Dashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState('');

  const fetchApps = () => {
    setLoading(true);
    fetch('/api/v1/admin/applications', { headers: { Authorization: `Bearer ${token}` } })
      .then((r) => r.json()).then((d) => setApps(d.data?.records ?? [])).finally(() => setLoading(false));
  };

  const fetchDashboard = () => {
    fetch('/api/v1/dashboard', { headers: { Authorization: `Bearer ${token}` } })
      .then((r) => r.json()).then((d) => { if (d.code === 200) setDash(d.data); });
  };

  useEffect(() => { fetchApps(); fetchDashboard(); }, []);

  const review = (userId: number, action: string, role?: string) => {
    fetch('/api/v1/admin/review', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token!}` },
      body: JSON.stringify({ userId, action, role }),
    }).then((r) => r.json()).then((d) => {
      setMsg(d.code === 200 ? `已${action === 'approve' ? '通过' : '驳回'}` : d.message ?? '失败');
      fetchApps();
    });
  };

  const fmt = (n: number) => n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n);

  const barMax = Math.max(1, ...(dash?.hourlyTrend.map((h) => h.cnt) ?? [1]));

  return (
    <div className="flex flex-col h-screen bg-[#0b0f14]">
      <div className="fixed inset-0 -z-10 pointer-events-none">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-900 via-[#0b0f14] to-[#0b0f14]" />
      </div>

      <header className="shrink-0 border-b border-slate-800/40 bg-[#0b0f14]/70 backdrop-blur-2xl">
        <div className="flex items-center justify-between px-6 h-16">
          <div className="flex items-center gap-6">
            <a href="/copilot" className="text-[13px] text-slate-400 hover:text-white transition-colors">← 返回</a>
            <span className="text-base font-semibold text-slate-200">运营管理平台</span>
          </div>
          <div className="flex rounded-xl bg-slate-800/60 p-1">
            <button onClick={() => setTab('dashboard')}
              className={`px-4 py-1.5 text-[13px] font-medium rounded-lg transition-all ${tab === 'dashboard' ? 'bg-blue-600 text-white shadow' : 'text-slate-400'}`}>
              运营监控
            </button>
            <button onClick={() => setTab('review')}
              className={`px-4 py-1.5 text-[13px] font-medium rounded-lg transition-all ${tab === 'review' ? 'bg-blue-600 text-white shadow' : 'text-slate-400'}`}>
              用户审核
            </button>
          </div>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-6xl mx-auto px-6 py-8">
          {tab === 'dashboard' && dash && (
            <div className="space-y-6">
              {/* Metric cards */}
              <div className="grid grid-cols-4 gap-4">
                {[
                  { label: '今日调用', value: fmt(dash.todayCalls), color: 'blue' },
                  { label: '活跃用户', value: dash.activeUsers, color: 'emerald' },
                  { label: 'Token 消耗', value: fmt(dash.todayTokens), color: 'violet' },
                  { label: '平均延迟', value: dash.avgLatency + 'ms', color: 'amber' },
                ].map((m) => (
                  <div key={m.label} className="rounded-2xl border border-slate-700/30 bg-slate-900/60 p-5">
                    <p className="text-[12px] text-slate-500 mb-2">{m.label}</p>
                    <p className={`text-2xl font-bold text-${m.color}-400`}>{m.value}</p>
                  </div>
                ))}
              </div>

              {/* Charts row */}
              <div className="grid grid-cols-2 gap-4">
                {/* Hourly trend */}
                <div className="rounded-2xl border border-slate-700/30 bg-slate-900/60 p-5">
                  <h3 className="text-sm font-semibold text-slate-300 mb-4">24h 调用趋势</h3>
                  <div className="flex items-end gap-1 h-32">
                    {Array.from({ length: 24 }, (_, i) => {
                      const h = dash.hourlyTrend.find((ht) => ht.hour === i);
                      const hh = h ? h.cnt : 0;
                      return (
                        <div key={i} className="flex-1 flex flex-col justify-end items-center gap-1">
                          <span className="text-[9px] text-slate-500">{hh || ''}</span>
                          <div className="w-full rounded-sm bg-blue-500/60 hover:bg-blue-400 transition-colors"
                               style={{ height: `${Math.max(4, (hh / barMax) * 100)}%` }} />
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Model usage */}
                <div className="rounded-2xl border border-slate-700/30 bg-slate-900/60 p-5">
                  <h3 className="text-sm font-semibold text-slate-300 mb-4">模型用量分布</h3>
                  <div className="space-y-3">
                    {dash.modelUsage.map((m) => {
                      const total = dash.modelUsage.reduce((s, x) => s + x.cnt, 0);
                      const pct = total > 0 ? Math.round((m.cnt / total) * 100) : 0;
                      return (
                        <div key={m.model} className="flex items-center gap-3">
                          <span className="text-[13px] text-slate-400 w-20">{m.model}</span>
                          <div className="flex-1 h-5 bg-slate-800 rounded-full overflow-hidden">
                            <div className="h-full bg-gradient-to-r from-blue-500 to-blue-400 rounded-full transition-all"
                                 style={{ width: `${pct}%` }} />
                          </div>
                          <span className="text-[12px] text-slate-500 w-10 text-right">{pct}%</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>

              {/* Recent logs */}
              <div className="rounded-2xl border border-slate-700/30 bg-slate-900/60 overflow-hidden">
                <div className="px-5 py-3 border-b border-slate-700/30">
                  <h3 className="text-sm font-semibold text-slate-300">最近调用日志</h3>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-[13px]">
                    <thead>
                      <tr className="text-left text-slate-500 border-b border-slate-800">
                        <th className="px-5 py-2 font-medium">用户</th>
                        <th className="px-5 py-2 font-medium">模型</th>
                        <th className="px-5 py-2 font-medium">Token</th>
                        <th className="px-5 py-2 font-medium">延迟</th>
                        <th className="px-5 py-2 font-medium">状态</th>
                        <th className="px-5 py-2 font-medium">时间</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dash.recentLogs.map((l) => (
                        <tr key={l.id} className="border-b border-slate-800/50">
                          <td className="px-5 py-2.5 text-slate-300">{l.username || '游客'}</td>
                          <td className="px-5 py-2.5 text-slate-400">{l.model}</td>
                          <td className="px-5 py-2.5 text-slate-400">↑{l.inputTokens} ↓{l.outputTokens}</td>
                          <td className="px-5 py-2.5 text-slate-400">{l.latencyMs}ms</td>
                          <td className="px-5 py-2.5">
                            <span className={`text-[11px] px-1.5 py-0.5 rounded ${l.status === 'success' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'}`}>
                              {l.status}
                            </span>
                          </td>
                          <td className="px-5 py-2.5 text-slate-500 text-[12px]">{fmtDate(l.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {tab === 'review' && (
            <div>
              {msg && <div className="mb-4 px-4 py-2 bg-green-500/10 border border-green-500/30 rounded-xl text-sm text-green-400">{msg}</div>}
              <div className="rounded-2xl border border-slate-700/30 bg-slate-900/60 overflow-hidden">
                <div className="px-5 py-3 border-b border-slate-700/30 flex items-center justify-between">
                  <h2 className="text-sm font-medium text-slate-300">用户管理</h2>
                  <span className="text-xs text-slate-500">共 {apps.length} 条</span>
                </div>
                {loading ? (
                  <div className="p-8 text-center text-slate-500 text-sm">加载中...</div>
                ) : apps.length === 0 ? (
                  <div className="p-8 text-center text-slate-500 text-sm">暂无用户</div>
                ) : (
                  <div className="divide-y divide-slate-800">
                    {apps.map((a) => (
                      <div key={a.id} className="px-5 py-4 flex items-center justify-between gap-4">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-sm font-medium text-slate-200">{a.username}</span>
                            <span className="text-xs text-slate-500">{a.email}</span>
                            {a.status === 'pending' && <span className="text-[10px] px-1.5 py-0.5 rounded bg-yellow-500/10 text-yellow-400">待审核</span>}
                            {a.status === 'active' && a.role === 'test' && <span className="text-[10px] px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-400">内测成员</span>}
                            {a.status === 'active' && a.role === 'user' && <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400">普通用户</span>}
                            {a.status === 'rejected' && <span className="text-[10px] px-1.5 py-0.5 rounded bg-red-500/10 text-red-400">已驳回</span>}
                          </div>
                          {a.applyReason && <p className="text-xs text-slate-400 truncate">理由：{a.applyReason}</p>}
                          <p className="text-xs text-slate-600 mt-1">{fmtDate(a.createdAt)}</p>
                        </div>
                        {a.status === 'pending' ? (
                          <div className="flex gap-2 shrink-0">
                            <button onClick={() => review(a.id, 'approve', 'user')} className="px-3 py-1.5 text-xs font-medium bg-emerald-600/80 hover:bg-emerald-500 text-white rounded-lg transition-colors">通过-用户</button>
                            <button onClick={() => review(a.id, 'approve', 'test')} className="px-3 py-1.5 text-xs font-medium bg-blue-600/80 hover:bg-blue-500 text-white rounded-lg transition-colors">通过-内测</button>
                            <button onClick={() => review(a.id, 'reject')} className="px-3 py-1.5 text-xs font-medium bg-red-600/20 hover:bg-red-600/40 text-red-400 rounded-lg transition-colors">驳回</button>
                          </div>
                        ) : (
                          <span className="text-xs text-slate-500">已处理</span>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
