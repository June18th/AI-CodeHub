import { useThemeStore } from '../store/themeStore';
import { useAuthStore } from '../store/authStore';

const BASE_AGENTS = [
  { title: 'AI 对话', desc: '多模型流式对话 · Agent工具调用 · RAG检索增强', icon: '💬', href: '/' },
  { title: '知识库', desc: '文档上传 · 向量化 · 语义检索', icon: '📚', href: '/rag' },
  { title: '管理后台', desc: '用户审核 · 权限管理 · 运营监控', icon: '⚙️', href: '/admin', adminOnly: true },
];
const CAPABILITIES = [
  { name: 'Workflow', desc: 'DAG任务编排 · 条件分支 · 并行执行', status: 'live', icon: '⚡' },
  { name: 'Tool Calling', desc: '计算器 · 天气查询 · 知识检索', status: 'live', icon: '🔧' },
  { name: 'Memory', desc: '对话上下文 · 长期记忆 · Redis缓存', status: 'live', icon: '🧠' },
  { name: 'RAG', desc: '千问Embedding · ES向量检索', status: 'live', icon: '🔍' },
  { name: 'Multi-Agent', desc: '多Agent协作 · 任务分配', status: 'soon', icon: '🤖' },
  { name: 'Runtime', desc: '任务调度 · 上下文管理 · 状态追踪', status: 'soon', icon: '⏱️' },
];

export default function CopilotOverview() {
  const dark = useThemeStore((s) => s.theme === 'dark');
  const isAdmin = useAuthStore((s) => s.isAdmin);
  const AGENTS = BASE_AGENTS.filter(a => !a.adminOnly || isAdmin);
  return (
    <div className={`flex flex-col h-screen ${dark ? 'bg-[#0b0f14]' : 'bg-white'}`}>
      <div className="fixed inset-0 -z-10 pointer-events-none overflow-hidden">
        <div className={`absolute inset-0 ${dark ? 'bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-slate-900 via-[#0b0f14] to-[#0b0f14]' : 'bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-slate-50 via-white to-white'}`} />
      </div>
      <header className={`shrink-0 border-b ${dark ? 'border-slate-800/40 bg-[#0b0f14]/70' : 'border-slate-200/60 bg-white/70'} backdrop-blur-2xl`}>
        <div className="max-w-6xl mx-auto flex items-center justify-between px-6 h-16">
          <div className="flex items-center gap-6">
            <a href="/" className={`text-[13px] font-medium ${dark ? 'text-slate-400 hover:text-white' : 'text-slate-500 hover:text-slate-700'} transition-colors`}>← 返回</a>
            <span className={`font-semibold ${dark ? 'text-slate-200' : 'text-slate-800'}`}>Copilot 工作台</span>
          </div>
          <a href="/copilot/workflow" className="flex items-center gap-1.5 px-4 py-1.5 text-[13px] font-semibold text-white bg-violet-600 hover:bg-violet-700 rounded-lg transition-colors shadow-sm">⚡ 工作流编辑器</a>
        </div>
      </header>
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-6xl mx-auto px-6 py-10 space-y-8">
          <div><h2 className={`text-lg font-bold mb-4 ${dark ? 'text-slate-100' : 'text-slate-800'}`}>快速入口</h2><div className="flex gap-4">{AGENTS.map((a) => (<a key={a.title} href={a.href} className={`flex-1 rounded-2xl border p-5 transition-all duration-200 hover:shadow-lg ${dark ? 'bg-slate-900/60 border-slate-700/30 hover:border-slate-600' : 'bg-white border-slate-200 hover:border-slate-300'}`}><div className="flex items-center gap-3"><span className="text-2xl">{a.icon}</span><div><h3 className={`text-sm font-semibold ${dark ? 'text-slate-200' : 'text-slate-800'}`}>{a.title}</h3><p className={`text-[12px] ${dark ? 'text-slate-500' : 'text-slate-400'}`}>{a.desc}</p></div></div></a>))}</div></div>
          <div><h2 className={`text-lg font-bold mb-4 ${dark ? 'text-slate-100' : 'text-slate-800'}`}>Agent 核心能力</h2><div className="grid grid-cols-3 gap-4">{CAPABILITIES.map((c) => (<div key={c.name} className={`rounded-2xl border p-4 flex items-center gap-3 ${dark ? 'bg-slate-900/60 border-slate-700/30' : 'bg-white border-slate-200'}`}><span className="text-xl">{c.icon}</span><div className="min-w-0"><div className="flex items-center gap-2 mb-0.5"><h3 className={`text-sm font-semibold ${dark ? 'text-slate-200' : 'text-slate-800'}`}>{c.name}</h3><span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-md ${c.status === 'live' ? 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' : 'bg-amber-50 dark:bg-amber-500/10 text-amber-600 dark:text-amber-400'}`}>{c.status === 'live' ? '上线' : '规划'}</span></div><p className={`text-[12px] truncate ${dark ? 'text-slate-500' : 'text-slate-400'}`}>{c.desc}</p></div></div>))}</div></div>
          <div className={`rounded-2xl border p-5 ${dark ? 'bg-slate-900/60 border-slate-700/30' : 'bg-white border-slate-200 shadow-sm'}`}>
            <h2 className={`text-lg font-bold mb-3 ${dark ? 'text-slate-100' : 'text-slate-800'}`}>模型配置</h2>
            <p className={`text-[13px] mb-3 ${dark ? 'text-slate-400' : 'text-slate-500'}`}>统一管理各模型供应商的 API 地址、密钥、模型参数</p>
            <a href="/model-config" className="inline-flex px-4 py-2 text-[13px] font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors">管理模型配置 →</a>
          </div>
        </div>
      </div>
    </div>
  );
}
