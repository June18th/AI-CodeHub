import { useState, useEffect } from 'react';
import { useAuthStore } from '../store/authStore';
import { useThemeStore } from '../store/themeStore';
import { fmtDate } from '../utils/format';

export default function TokenAnalytics() {
  const token = useAuthStore(s => s.token);
  const dark = useThemeStore(s => s.theme === 'dark');
  const [logPage, setLogPage] = useState(1);
  const [allLogs, setAllLogs] = useState<any[]>([]);
  useEffect(() => {
    fetch('/api/v1/dashboard/logs?page=1&size=200', { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.json()).then(d => setAllLogs(d.data?.records || []));
  }, [token]);

  const withBreakdown = allLogs.filter((l: any) => l.tokenBreakdown);
  const pages = Math.ceil(withBreakdown.length / 10);
  const pageRecords = withBreakdown.slice((logPage - 1) * 10, logPage * 10);

  const totals = withBreakdown.reduce((acc: any, l: any) => {
    try { const b = JSON.parse(l.tokenBreakdown); acc.user += b.user_prompt||0; acc.sys += b.system_prompt||0; acc.mem += b.memory_context||0; acc.hist += b.history||0; acc.out += b.output||0; } catch {}
    return acc;
  }, { user: 0, sys: 0, mem: 0, hist: 0, out: 0 });
  const total = totals.user + totals.sys + totals.mem + totals.hist + totals.out || 1;
  const bars = [
    { label: '用户输入', value: totals.user, color: '#3b82f6' },
    { label: '系统提示', value: totals.sys, color: '#8b5cf6' },
    { label: '长期记忆', value: totals.mem, color: '#06b6d4' },
    { label: '历史对话', value: totals.hist, color: '#f59e0b' },
    { label: '模型输出', value: totals.out, color: '#10b981' },
  ];

  return (
    <div className={`flex flex-col h-screen ${dark ? 'bg-[#0b0f14]' : 'bg-white'}`}>
      <div className="fixed inset-0 -z-10 pointer-events-none"><div className={`absolute inset-0 ${dark?'bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-900 via-[#0b0f14] to-[#0b0f14]':'bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-50 via-white to-white'}`}/></div>
      <header className={`shrink-0 border-b ${dark?'border-slate-800/40 bg-[#0b0f14]/70':'border-slate-200/60 bg-white/70'} backdrop-blur-2xl`}>
        <div className="max-w-4xl mx-auto flex items-center px-6 h-16 gap-4">
          <a href="/admin" className={`text-[13px] ${dark?'text-slate-400 hover:text-white':'text-slate-500 hover:text-slate-700'} transition-colors`}>← 返回</a>
          <span className={`font-semibold ${dark?'text-slate-200':'text-slate-800'}`}>Token 分析</span>
        </div>
      </header>
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto px-6 py-8 space-y-6">
          <div className={`rounded-2xl border p-5 ${dark?'border-slate-700/30 bg-slate-900/60':'border-slate-200 bg-white'}`}>
            <h3 className={`text-sm font-semibold mb-4 ${dark?'text-slate-300':'text-slate-700'}`}>Token 消耗分布（总计 {total.toLocaleString()}）</h3>
            <div className="space-y-2">
              {bars.map(b => (
                <div key={b.label} className="flex items-center gap-3 text-xs">
                  <span className={`w-20 ${dark?'text-slate-400':'text-slate-500'}`}>{b.label}</span>
                  <div className="flex-1 h-5 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
                    <div className="h-full rounded-full transition-all" style={{ width: `${(b.value/total)*100}%`, backgroundColor: b.color }}/>
                  </div>
                  <span className="w-16 text-right font-mono">{b.value.toLocaleString()}</span>
                  <span className="w-12 text-right text-slate-500">{Math.round(b.value/total*100)}%</span>
                </div>
              ))}
            </div>
          </div>

          <div className={`rounded-2xl border overflow-hidden ${dark?'border-slate-700/30 bg-slate-900/60':'border-slate-200 bg-white'}`}>
            <div className={`px-5 py-3 border-b ${dark?'border-slate-700/30':'border-slate-100'} flex items-center justify-between`}>
              <h3 className={`text-sm font-semibold ${dark?'text-slate-300':'text-slate-700'}`}>最近请求明细</h3>
              <span className="text-xs text-slate-500">共 {withBreakdown.length} 条有分析数据（第 {(logPage-1)*10+1}-{Math.min(logPage*10,withBreakdown.length)} 条）</span>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-[13px]">
                <thead><tr className={`text-left ${dark?'text-slate-500 border-slate-800':'text-slate-400 border-slate-100'} border-b`}><th className="px-3 py-2 w-8">#</th><th className="px-5 py-2">时间</th><th className="px-5 py-2">用户输入</th><th className="px-5 py-2">系统</th><th className="px-5 py-2">记忆</th><th className="px-5 py-2">历史</th><th className="px-5 py-2">输出</th></tr></thead>
                <tbody>
                  {pageRecords.map((l: any, i: number) => {
                    let b: any = {};
                    try { b = JSON.parse(l.tokenBreakdown); } catch {}
                    return <tr key={l.id} className={`border-b ${dark?'border-slate-800/50':'border-slate-100'}`}>
                      <td className="px-3 py-2.5 text-slate-600 text-xs">{(logPage-1)*10+i+1}</td>
                      <td className={`px-5 py-2.5 ${dark?'text-slate-500':'text-slate-400'}`}>{fmtDate(l.createdAt)}</td>
                      <td className="px-5 py-2.5 text-slate-400">{b.user_prompt||'-'}</td>
                      <td className="px-5 py-2.5 text-slate-400">{b.system_prompt||'-'}</td>
                      <td className="px-5 py-2.5 text-slate-400">{b.memory_context||'-'}</td>
                      <td className="px-5 py-2.5 text-slate-400">{b.history||'-'}</td>
                      <td className="px-5 py-2.5 text-emerald-400">{b.output||'-'}</td>
                    </tr>;
                  })}
                </tbody>
              </table>
            </div>
            {pages >= 1 && (
              <div className="flex items-center justify-center gap-2 px-5 py-3 border-t dark:border-slate-700/30 border-slate-100">
                <button onClick={() => setLogPage(p => Math.max(1, p - 1))} disabled={logPage <= 1}
                  className="px-3 py-1 text-xs rounded-lg bg-slate-800 text-slate-400 hover:text-white disabled:opacity-30">上一页</button>
                <span className="text-xs text-slate-500">{logPage} / {pages}</span>
                <button onClick={() => setLogPage(p => Math.min(pages, p + 1))} disabled={logPage >= pages}
                  className="px-3 py-1 text-xs rounded-lg bg-slate-800 text-slate-400 hover:text-white disabled:opacity-30">下一页</button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
