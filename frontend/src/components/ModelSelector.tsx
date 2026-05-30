import { useState, useRef, useEffect } from 'react';
import type { ModelOption } from '../types/chat';

const MODELS: (ModelOption & { icon: string; desc: string; color: string })[] = [
  { value: 'deepseek', label: 'DeepSeek', icon: '🔷', desc: '1M 上下文 · 免费 · 国产标杆', color: '#3B82F6' },
  { value: 'gpt', label: 'GPT-4', icon: '🟢', desc: 'OpenAI 旗舰 · 推理能力强', color: '#10B981' },
  { value: 'zhipu', label: '智谱 GLM', icon: '🔵', desc: '清华系 · 国产多模态', color: '#6366F1' },
  { value: 'qwen', label: '通义千问', icon: '🟠', desc: '阿里云 · 多模态对话', color: '#F97316' },
  { value: 'openai', label: 'OpenAI 兼容', icon: '🟣', desc: '接入任意兼容接口', color: '#EC4899' },
];

interface Props { value: string; onChange: (v: string) => void; }

export default function ModelSelector({ value, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const current = MODELS.find((m) => m.value === value) ?? MODELS[0];

  useEffect(() => {
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2 h-8 px-3 rounded-xl border border-slate-200 dark:border-slate-700/50
                   bg-white dark:bg-slate-800/50 hover:border-slate-300 dark:hover:border-slate-600
                   transition-all text-[13px] font-medium text-slate-700 dark:text-slate-200 shadow-sm"
      >
        <span className="text-base">{current.icon}</span>
        {current.label}
        <svg className={`w-3.5 h-3.5 text-slate-400 transition-transform duration-200 ${open ? 'rotate-180' : ''}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>

      {open && (
        <div className="absolute bottom-full left-0 mb-2 w-72 rounded-2xl border border-slate-200 dark:border-slate-700/50
                        bg-white dark:bg-slate-900 shadow-2xl shadow-black/5 dark:shadow-black/30 z-50 overflow-hidden animate-fadeIn">
          <div className="px-4 py-3 border-b border-slate-100 dark:border-slate-800">
            <p className="text-[11px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">选择模型</p>
          </div>
          <div className="p-2 space-y-0.5">
            {MODELS.map((m) => (
              <button
                key={m.value}
                onClick={() => { onChange(m.value); setOpen(false); }}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-left transition-all ${
                  m.value === value
                    ? 'bg-blue-50 dark:bg-blue-500/10 ring-1 ring-blue-200 dark:ring-blue-500/20'
                    : 'hover:bg-slate-50 dark:hover:bg-slate-800/50'
                }`}
              >
                <span className="w-9 h-9 rounded-xl flex items-center justify-center text-lg shrink-0" style={{ backgroundColor: m.color + '15' }}>
                  {m.icon}
                </span>
                <div className="min-w-0">
                  <div className={`text-[13px] font-semibold ${m.value === value ? 'text-blue-700 dark:text-blue-300' : 'text-slate-700 dark:text-slate-200'}`}>
                    {m.label}
                  </div>
                  <div className="text-[11px] text-slate-400 dark:text-slate-500 mt-0.5">{m.desc}</div>
                </div>
                {m.value === value && (
                  <svg className="w-4 h-4 text-blue-500 ml-auto shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                    <path d="M20 6L9 17l-5-5" />
                  </svg>
                )}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
