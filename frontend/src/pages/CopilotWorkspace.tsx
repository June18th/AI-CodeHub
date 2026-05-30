export default function CopilotWorkspace() {
  return (
    <div className="flex flex-col h-screen bg-slate-950">
      <div className="fixed inset-0 -z-10">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-900 via-slate-950 to-slate-950" />
      </div>
      <div className="flex-1 flex items-center justify-center px-4">
        <div className="text-center">
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-cyan-500 to-blue-600 flex items-center justify-center mx-auto mb-6 shadow-xl shadow-cyan-500/20">
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                d="M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5" />
            </svg>
          </div>
          <h2 className="text-lg font-semibold text-slate-300 mb-2">Copilot 工作台</h2>
          <p className="text-sm text-slate-500">AI Agent 核心能力建设中...</p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            {['Workflow', 'Tool Calling', 'Memory', 'RAG', 'Multi-Agent'].map((m) => (
              <span key={m} className="px-3 py-1.5 text-xs text-slate-400 bg-slate-800/60 border border-slate-700/40 rounded-xl">
                {m}
              </span>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
