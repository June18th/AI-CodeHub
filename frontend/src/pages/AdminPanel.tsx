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
  const [logPage, setLogPage] = useState(1);
  const [logData, setLogData] = useState<{ records: any[]; total: number; pages: number }>({ records: [], total: 0, pages: 0 });
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

  const fetchLogs = (page: number) => {
    fetch(`/api/v1/dashboard/logs?page=${page}&size=10`, { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.json()).then(d => { if (d.code === 200) setLogData(d.data); });
  };

  useEffect(() => { fetchApps(); fetchDashboard(); }, []);
  useEffect(() => { fetchLogs(logPage); }, [logPage]);

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

  // Token debug info
  const auth = JSON.parse(localStorage.getItem('aicodehub-auth') || '{}');
  const atPayload = (() => { try { return JSON.parse(atob(auth.token?.split('.')[1]||'{}')); } catch { return {}; } })();
  const rtPayload = (() => { try { return JSON.parse(atob(auth.refreshToken?.split('.')[1]||'{}')); } catch { return {}; } })();
  const atExp = (() => { try { return new Date(atPayload.exp*1000).toLocaleString('zh-CN'); } catch { return '-'; } })();
  const rtExp = (() => { try { return new Date(rtPayload.exp*1000).toLocaleString('zh-CN'); } catch { return '-'; } })();

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
            <a href="/admin/logs"
              className="px-4 py-1.5 text-[13px] font-medium text-slate-400 hover:text-white rounded-lg transition-all">
              日志监控
            </a>
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
                  { label: '今日调用', value: fmt(dash.todayCalls), cls: 'text-sky-400' },
                  { label: '活跃用户', value: dash.activeUsers, cls: 'text-emerald-400' },
                  { label: 'Token 消耗', value: fmt(dash.todayTokens), cls: 'text-violet-400' },
                  { label: '平均延迟', value: dash.avgLatency + 'ms', cls: 'text-amber-400' },
                ].map((m) => (
                  <div key={m.label} className="rounded-2xl border border-slate-700/30 bg-slate-900/60 p-5">
                    <p className="text-[12px] text-slate-400 mb-2">{m.label}</p>
                    <p className={`text-2xl font-bold ${m.cls}`}>{m.value}</p>
                  </div>
                ))}
              </div>

              {/* Token debug info */}
              {auth.token && (
                <div className="rounded-2xl border border-slate-700/30 bg-slate-900/60 p-4">
                  <div className="grid grid-cols-4 gap-4 text-xs">
                    <div><span className="text-slate-500">Access <span className="text-amber-400">[{atPayload.type||'?'}]</span></span><p className="text-amber-400 font-mono mt-0.5">…{auth.token?.split('.').slice(1).join('.').slice(-20)||'-'}</p><p className="text-amber-400/70 mt-0.5">过期 {atExp}</p></div>
                    <div><span className="text-slate-500">Refresh <span className="text-emerald-400">[{rtPayload.type||'?'}]</span></span><p className="text-emerald-400 font-mono mt-0.5">…{auth.refreshToken?.split('.').slice(1).join('.').slice(-20)||'-'}</p><p className="text-emerald-400/70 mt-0.5">过期 {rtExp}</p></div>
                    <div><span className="text-slate-500">Role</span><p className="text-slate-300 mt-0.5">{auth.role||'-'} / {auth.username||'-'}</p></div>
                    <div><span className="text-slate-500">Session Redis</span><p className="text-slate-300 mt-0.5">Active</p></div>
                  </div>
                </div>
              )}

              {/* Charts row */}
              <div className="grid grid-cols-2 gap-4">
                {/* Hourly trend */}
                <div className="rounded-2xl border border-slate-700/30 bg-slate-900/60 p-5">
                  <h3 className="text-sm font-semibold text-slate-300 mb-4">24h 调用趋势</h3>
                  <svg viewBox="0 0 360 120" className="w-full h-32" preserveAspectRatio="none">
                    {/* Grid lines */}
                    {[20,40,60,80].map(y => <line key={y} x1="0" y1={110-y} x2="360" y2={110-y} stroke="#334155" strokeWidth="0.5"/>) }
                    {/* Area fill */}
                    <path d={(() => {
                      const pts = Array.from({length:24},(_,i)=>({x:(i+0.5)/24*360,y:110-((dash.hourlyTrend.find(h=>h.hour===i)?.cnt||0)/barMax*100)}));
                      if (pts.every(p=>p.y===110)) return '';
                      let d=`M${pts[0].x},110`;
                      pts.forEach(p=>d+=` L${p.x},${p.y}`);
                      d+=` L${pts[23].x},110 Z`;
                      return d;
                    })()} fill="url(#chartGrad)" opacity="0.3"/>
                    {/* Line */}
                    <polyline fill="none" stroke="#3b82f6" strokeWidth="1.5" strokeLinejoin="round"
                      points={Array.from({length:24},(_,i)=>{
                        const v=dash.hourlyTrend.find(h=>h.hour===i)?.cnt||0;
                        return `${(i+0.5)/24*360},${110-(v/barMax*100)}`;
                      }).join(' ')} />
                    {/* Value labels */}
                    {Array.from({length:24},(_,i)=>{
                      const v=dash.hourlyTrend.find(h=>h.hour===i)?.cnt||0;
                      if (v===0) return null;
                      return <text key={i} x={(i+0.5)/24*360} y={104-(v/barMax*100)} textAnchor="middle" fill="#94a3b8" fontSize="8">{v}</text>;
                    })}
                    <defs><linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#3b82f6"/><stop offset="100%" stopColor="#3b82f6" stopOpacity="0"/></linearGradient></defs>
                  </svg>
                  {/* Hour labels */}
                  <div className="flex justify-between mt-1">
                    {[0,2,4,6,8,10,12,14,16,18,20,22].map(h => <span key={h} className="text-[8px] text-slate-600">{h}h</span>)}
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
                <div className="px-5 py-3 border-b border-slate-700/30 flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-slate-300">最近调用日志</h3>
                  <span className="text-xs text-slate-500">共 {logData.total} 条</span>
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
                      {logData.records.map((l: any) => (
                        <tr key={l.id} className="border-b border-slate-800/50">
                          <td className="px-5 py-2.5 text-slate-300">{l.username || '游客'}</td>
                          <td className="px-5 py-2.5 text-slate-400">{l.model}</td>
                          <td className="px-5 py-2.5 text-slate-400">入 {l.inputTokens}　出 {l.outputTokens}</td>
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
                {/* Pagination */}
                {logData.pages > 1 && (
                  <div className="flex items-center justify-center gap-2 px-5 py-3 border-t border-slate-700/30">
                    <button onClick={() => setLogPage(p => Math.max(1, p - 1))} disabled={logPage <= 1}
                      className="px-3 py-1 text-xs rounded-lg bg-slate-800 text-slate-400 hover:text-white disabled:opacity-30 transition-colors">上一页</button>
                    <span className="text-xs text-slate-500">{logPage} / {logData.pages}</span>
                    <button onClick={() => setLogPage(p => Math.min(logData.pages, p + 1))} disabled={logPage >= logData.pages}
                      className="px-3 py-1 text-xs rounded-lg bg-slate-800 text-slate-400 hover:text-white disabled:opacity-30 transition-colors">下一页</button>
                  </div>
                )}
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
