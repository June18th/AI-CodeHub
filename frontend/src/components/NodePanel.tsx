import { useState } from 'react';
import { useThemeStore } from '../store/themeStore';

interface NodeDef { nodeType: string; displayName: string; icon: string; category: string; }

const BUILTIN_NODES: NodeDef[] = [
  { nodeType: 'input', displayName: '输入节点', icon: '📥', category: 'CONTROL' },
  { nodeType: 'output', displayName: '输出节点', icon: '📤', category: 'CONTROL' },
  { nodeType: 'llm', displayName: '大模型', icon: '💬', category: 'LLM' },
  { nodeType: 'deepseek', displayName: 'DeepSeek', icon: '🔷', category: 'LLM' },
  { nodeType: 'qwen', displayName: '通义千问', icon: '🔶', category: 'LLM' },
  { nodeType: 'zhipu', displayName: '智谱 GLM', icon: '🔵', category: 'LLM' },
  { nodeType: 'openai', displayName: 'OpenAI', icon: '🟢', category: 'LLM' },
  { nodeType: 'weather', displayName: '天气查询', icon: '🌤', category: 'TOOL' },
  { nodeType: 'tts', displayName: '语音合成', icon: '🔊', category: 'TOOL' },
  { nodeType: 'condition', displayName: '条件分支', icon: '🔀', category: 'CONTROL' },
];

interface Props { onDragStart: (e: React.DragEvent, type: string, label: string) => void; }

export default function NodePanel({ onDragStart }: Props) {
  const dark = useThemeStore((s) => s.theme === 'dark');
  const [open, setOpen] = useState<Record<string, boolean>>({ LLM: true, TOOL: true, CONTROL: true });
  const nodeTypes = [...BUILTIN_NODES];

  const llm = nodeTypes.filter((n) => n.category === 'LLM');
  const tool = nodeTypes.filter((n) => n.category === 'TOOL');
  const ctrl = nodeTypes.filter((n) => n.category === 'CONTROL');

  const sections = [
    { key: 'LLM', label: '大模型节点', items: llm, color: 'blue' },
    { key: 'TOOL', label: '工具节点', items: tool, color: 'amber' },
    { key: 'CONTROL', label: '控制节点', items: ctrl, color: 'violet' },
  ];

  const tone = (type: string) => {
    if (type === 'input') return 'bg-emerald-500';
    if (type === 'output') return 'bg-violet-500';
    if (type === 'condition') return 'bg-orange-500';
    return 'bg-blue-500';
  };

  return (
    <div className={`h-full flex flex-col overflow-hidden border-r ${dark ? 'border-slate-700/50 bg-slate-900/60' : 'border-slate-200 bg-white'}`}>
      <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-700/50">
        <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-200">节点库</h3>
        <p className="text-xs text-slate-400">拖拽节点到画布</p>
      </div>
      <div className="flex-1 overflow-y-auto p-3 space-y-2">
        {sections.map((sec) => (
          <div key={sec.key} className={'rounded-xl border '+(dark?'border-slate-700/50 bg-slate-900/40':'border-slate-200 bg-white')+' overflow-hidden'}>
            <button onClick={() => setOpen((o) => ({ ...o, [sec.key]: !o[sec.key] }))}
              className={'w-full flex items-center justify-between px-3 py-2.5 text-sm font-semibold '+(dark?'text-slate-300 hover:bg-slate-800/50':'text-slate-600 hover:bg-slate-50')+' transition-colors'}>
              <span>{sec.label}</span>
              <span className="flex items-center gap-2">
                <span className="text-xs px-2 py-0.5 rounded-full bg-blue-100 dark:bg-blue-500/10 text-blue-600 dark:text-blue-400 font-medium">{sec.items.length}</span>
                <svg className={`w-3.5 h-3.5 transition-transform ${open[sec.key] ? 'rotate-90' : ''}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="m9 18 6-6-6-6"/></svg>
              </span>
            </button>
            {open[sec.key] && (
              <div className="px-2 pb-2 space-y-0.5">
                {sec.items.length === 0 ? (
                  <p className="text-xs text-slate-400 text-center py-3">暂无节点</p>
                ) : sec.items.map((n) => (
                  <div key={n.nodeType} draggable
                    onDragStart={(e) => onDragStart(e, n.nodeType, n.displayName)}
                    className={'flex items-center gap-3 px-3 py-2.5 rounded-lg cursor-grab border border-transparent hover:border-slate-300 dark:hover:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-800/60 active:cursor-grabbing transition-all group '+(dark?'bg-slate-800/40':'bg-slate-50')}>
                    <div className={`w-8 h-8 rounded-lg ${tone(n.nodeType)} flex items-center justify-center text-xs font-bold text-white shrink-0`}>
                      {n.icon}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-sm font-medium text-slate-700 dark:text-slate-200 truncate">{n.displayName}</div>
                      <div className="text-xs text-slate-400 truncate">{n.nodeType}</div>
                    </div>
                    <span className="text-slate-300 dark:text-slate-600 opacity-0 group-hover:opacity-100 transition-opacity text-lg">⋮</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
      <div className="px-4 py-2 border-t border-slate-200 dark:border-slate-700/50 text-xs text-center text-slate-400">
        输入 · 模型 · 工具 · 控制 · 输出
      </div>
    </div>
  );
}
