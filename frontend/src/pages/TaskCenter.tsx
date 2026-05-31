import { useState, useEffect, useCallback } from 'react';
import { useAuthStore } from '../store/authStore';
import { useThemeStore } from '../store/themeStore';
import { fmtDate } from '../utils/format';

interface AgentTask {
  id: number; prompt: string; modelType: string; status: string;
  result?: string; errorMsg?: string; createdAt: string; updatedAt?: string;
}

export default function TaskCenter() {
  const token = useAuthStore(s => s.token);
  const dark = useThemeStore(s => s.theme === 'dark');
  const [tasks, setTasks] = useState<AgentTask[]>([]);
  const [prompt, setPrompt] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const fetchTasks = useCallback(() => {
    fetch('/api/v1/tasks', { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.json()).then(d => setTasks(d.data ?? []));
  }, [token]);

  useEffect(() => { fetchTasks(); const id = setInterval(fetchTasks, 5000); return () => clearInterval(id); }, [fetchTasks]);

  const submit = async () => {
    if (!prompt.trim()) return;
    setSubmitting(true);
    await fetch('/api/v1/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token!}` }, body: JSON.stringify({ prompt }) });
    setPrompt(''); setSubmitting(false); fetchTasks();
  };

  const statusBadge = (s: string) => {
    const m: Record<string, string> = {
      pending: dark ? 'bg-yellow-500/10 text-yellow-400' : 'bg-yellow-50 text-yellow-600',
      running: dark ? 'bg-blue-500/10 text-blue-400' : 'bg-blue-50 text-blue-600',
      done: dark ? 'bg-emerald-500/10 text-emerald-400' : 'bg-emerald-50 text-emerald-600',
      failed: dark ? 'bg-red-500/10 text-red-400' : 'bg-red-50 text-red-600',
    };
    return <span className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${m[s] || ''}`}>{s}</span>;
  };

  return (
    <div className={`flex flex-col h-screen ${dark ? 'bg-[#0b0f14]' : 'bg-white'}`}>
      <div className="fixed inset-0 -z-10 pointer-events-none"><div className={`absolute inset-0 ${dark ? 'bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-900 via-[#0b0f14] to-[#0b0f14]' : 'bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-50 via-white to-white'}`}/></div>
      <header className={`shrink-0 border-b ${dark ? 'border-slate-800/40 bg-[#0b0f14]/70' : 'border-slate-200/60 bg-white/70'} backdrop-blur-2xl`}>
        <div className="max-w-4xl mx-auto flex items-center justify-between px-6 h-16">
          <div className="flex items-center gap-4">
            <a href="/copilot" className={`text-[13px] ${dark ? 'text-slate-400 hover:text-white' : 'text-slate-500 hover:text-slate-700'} transition-colors`}>← 返回</a>
            <span className={`font-semibold ${dark ? 'text-slate-200' : 'text-slate-800'}`}>任务中心</span>
          </div>
        </div>
      </header>
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto px-6 py-8 space-y-6">
          {/* Submit form */}
          <div className={`rounded-2xl border p-4 ${dark ? 'border-slate-700/30 bg-slate-900/60' : 'border-slate-200 bg-white'}`}>
            <textarea value={prompt} onChange={e => setPrompt(e.target.value)} placeholder="输入任务描述，提交后后台执行..." rows={3}
              className={`w-full px-4 py-3 rounded-xl border text-sm resize-none ${dark ? 'bg-slate-800 border-slate-700 text-slate-200 placeholder-slate-500' : 'bg-slate-50 border-slate-200 placeholder-slate-400'}`}/>
            <div className="flex justify-end mt-3">
              <button onClick={submit} disabled={submitting || !prompt.trim()}
                className="px-5 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-xl transition-colors disabled:opacity-50">
                {submitting ? '提交中...' : '提交任务'}
              </button>
            </div>
          </div>
          {/* Task list */}
          {tasks.length === 0 ? (
            <div className={`text-center text-sm ${dark ? 'text-slate-500' : 'text-slate-400'} py-12`}>暂无任务</div>
          ) : (
            <div className="space-y-3">
              {tasks.map(t => (
                <div key={t.id} className={`rounded-2xl border p-4 ${dark ? 'border-slate-700/30 bg-slate-900/60' : 'border-slate-200 bg-white'}`}>
                  <div className="flex items-start justify-between gap-4 mb-2">
                    <p className={`text-sm font-medium flex-1 ${dark ? 'text-slate-200' : 'text-slate-800'}`}>{t.prompt}</p>
                    {statusBadge(t.status)}
                  </div>
                  {t.result && <p className={`text-[13px] mt-2 whitespace-pre-wrap ${dark ? 'text-slate-400' : 'text-slate-600'}`}>{t.result}</p>}
                  {t.errorMsg && <p className="text-[13px] mt-2 text-red-400">{t.errorMsg}</p>}
                  <p className={`text-xs mt-2 ${dark ? 'text-slate-600' : 'text-slate-400'}`}>{fmtDate(t.createdAt)}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
