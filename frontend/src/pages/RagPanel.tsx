import { useState, useEffect, useRef, useCallback } from 'react';
import { useAuthStore } from '../store/authStore';
import { useThemeStore } from '../store/themeStore';
import { fmtDate } from '../utils/format';

interface Doc {
  id: number; filename: string; fileType: string; status: string; createdAt: string;
}

export default function RagPanel() {
  const token = useAuthStore((s) => s.token);
  const theme = useThemeStore((s) => s.theme);
  const dark = theme === 'dark';
  const [docs, setDocs] = useState<Doc[]>([]);
  const [filename, setFilename] = useState('');
  const [content, setContent] = useState('');
  const [uploading, setUploading] = useState(false);
  const [msg, setMsg] = useState('');
  const [dragOver, setDragOver] = useState(false);
  const [tab, setTab] = useState<'file' | 'text'>('file');
  const [previewDoc, setPreviewDoc] = useState<{ id: number; filename: string; content: string } | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const fetchDocs = () => {
    fetch('/api/v1/documents', { headers: { Authorization: `Bearer ${token}` } })
      .then((r) => r.json()).then((d) => setDocs(d.data ?? []));
  };
  useEffect(() => { fetchDocs(); }, []);

  const uploadContent = async (name: string, body: string) => {
    setUploading(true);
    const res = await fetch('/api/v1/documents', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token!}` },
      body: JSON.stringify({ filename: name, fileType: name.split('.').pop() || 'txt', content: body }),
    });
    const d = await res.json();
    if (d.code === 200) { setMsg('上传成功'); setFilename(''); setContent(''); fetchDocs(); }
    else setMsg(d.message ?? '上传失败');
    setUploading(false);
  };

  const handleTextUpload = async () => {
    if (!filename.trim() || !content.trim()) return;
    await uploadContent(filename.trim(), content.trim());
  };

  const handleFile = useCallback(async (file: File) => {
    const form = new FormData();
    form.append('file', file);
    setUploading(true);
    const res = await fetch('/api/v1/documents/file', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token!}` },
      body: form,
    });
    const d = await res.json();
    if (d.code === 200) { setMsg('上传成功'); fetchDocs(); }
    else setMsg(d.message ?? '上传失败');
    setUploading(false);
  }, [token]);

  const del = async (id: number) => {
    await fetch(`/api/v1/documents/${id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } });
    fetchDocs();
  };

  const preview = async (id: number, filename: string) => {
    const res = await fetch(`/api/v1/documents/${id}/content`, { headers: { Authorization: `Bearer ${token}` } });
    const d = await res.json();
    if (d.code === 200) {
      const chunks = (d.data ?? []) as { index: number; content: string }[];
      setPreviewDoc({ id, filename, content: chunks.map((c) => c.content).join('\n\n') });
    }
  };

  return (
    <div className={`flex flex-col h-screen ${dark ? 'bg-slate-950' : 'bg-white'}`}>
      <div className="fixed inset-0 -z-10 pointer-events-none">
        <div className={`absolute inset-0 ${dark
          ? 'bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-900 via-slate-950 to-slate-950'
          : 'bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-50 via-white to-white'}`} />
      </div>

      <header className={`shrink-0 border-b ${dark ? 'border-slate-800/80 bg-slate-950/70' : 'border-slate-200 bg-white/70'} backdrop-blur-xl`}>
        <div className="max-w-4xl mx-auto flex items-center justify-between px-5 h-14">
          <div className="flex items-center gap-3">
            <a href="/" className={`text-sm font-medium ${dark ? 'text-slate-400 hover:text-white' : 'text-slate-500 hover:text-slate-700'} transition-colors`}>← 返回对话</a>
            <span className={`text-base font-semibold ${dark ? 'text-slate-200' : 'text-slate-800'}`}>知识库</span>
          </div>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto px-5 py-8 space-y-6">
          {/* Upload Area */}
          <div className={`rounded-2xl border p-6 ${dark ? 'bg-slate-900/60 border-slate-700/30' : 'bg-white border-slate-200 shadow-sm'}`}>
            <h3 className={`text-base font-semibold mb-4 ${dark ? 'text-slate-200' : 'text-slate-800'}`}>上传文档</h3>

            {/* Tab switcher */}
            <div className="flex gap-1 mb-4 bg-slate-100 dark:bg-slate-800/60 rounded-xl p-1">
              <button onClick={() => setTab('file')}
                className={`flex-1 py-2 text-sm font-medium rounded-lg transition-all ${tab === 'file' ? 'bg-white dark:bg-slate-700 text-slate-800 dark:text-white shadow-sm' : 'text-slate-500'}`}>
                📁 文件上传
              </button>
              <button onClick={() => setTab('text')}
                className={`flex-1 py-2 text-sm font-medium rounded-lg transition-all ${tab === 'text' ? 'bg-white dark:bg-slate-700 text-slate-800 dark:text-white shadow-sm' : 'text-slate-500'}`}>
                ✏️ 文本输入
              </button>
            </div>

            {tab === 'file' ? (
              <div
                onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
                onDragLeave={() => setDragOver(false)}
                onDrop={(e) => { e.preventDefault(); setDragOver(false); const f = e.dataTransfer.files[0]; if (f) handleFile(f); }}
                onClick={() => fileRef.current?.click()}
                className={`border-2 border-dashed rounded-2xl p-10 text-center cursor-pointer transition-all ${
                  dragOver
                    ? 'border-blue-400 bg-blue-50 dark:bg-blue-500/10'
                    : dark ? 'border-slate-700 hover:border-slate-500' : 'border-slate-300 hover:border-slate-400'
                }`}
              >
                <input ref={fileRef} type="file" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFile(f); }}
                  accept=".txt,.md,.json,.csv,.html,.xml,.yaml,.yml,.log" />
                <svg className={`w-10 h-10 mx-auto mb-3 ${dark ? 'text-slate-600' : 'text-slate-400'}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                    d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5" />
                </svg>
                <p className={`text-sm font-medium mb-1 ${dark ? 'text-slate-300' : 'text-slate-700'}`}>
                  拖拽文件到此处或点击上传
                </p>
                <p className={`text-xs ${dark ? 'text-slate-500' : 'text-slate-400'}`}>
                  支持 TXT、Markdown、JSON、CSV、HTML、XML、YAML、LOG
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                <input value={filename} onChange={(e) => setFilename(e.target.value)}
                  placeholder="文档名称"
                  className={`w-full px-4 py-2.5 rounded-xl border text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 ${
                    dark ? 'bg-slate-800 border-slate-700 text-slate-200 placeholder:text-slate-500' : 'bg-slate-50 border-slate-200 text-slate-800'
                  }`} />
                <textarea value={content} onChange={(e) => setContent(e.target.value)}
                  placeholder="粘贴文档内容..." rows={8}
                  className={`w-full px-4 py-2.5 rounded-xl border text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 resize-none ${
                    dark ? 'bg-slate-800 border-slate-700 text-slate-200 placeholder:text-slate-500' : 'bg-slate-50 border-slate-200 text-slate-800'
                  }`} />
                <button onClick={handleTextUpload} disabled={uploading}
                  className="px-5 py-2.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-xl transition-colors disabled:opacity-50">
                  {uploading ? '上传中...' : '上传'}
                </button>
              </div>
            )}

            {msg && (
              <p className={`text-sm mt-3 ${msg.includes('成功') ? 'text-green-500' : 'text-red-400'}`}>{msg}</p>
            )}
          </div>

          {/* Doc List */}
          <div className={`rounded-2xl border overflow-hidden ${dark ? 'bg-slate-900/60 border-slate-700/30' : 'bg-white border-slate-200 shadow-sm'}`}>
            <div className={`px-5 py-3 border-b ${dark ? 'border-slate-700/30' : 'border-slate-200'}`}>
              <h3 className={`text-sm font-medium ${dark ? 'text-slate-300' : 'text-slate-700'}`}>已有文档 ({docs.length})</h3>
            </div>
            {docs.length === 0 ? (
              <div className={`p-10 text-center text-sm ${dark ? 'text-slate-500' : 'text-slate-400'}`}>
                暂无文档，上传后可用 Agent 模式检索
              </div>
            ) : (
              <div className={`divide-y ${dark ? 'divide-slate-800' : 'divide-slate-100'}`}>
                {docs.map((d) => (
                  <div key={d.id} className="px-5 py-3.5 flex items-center justify-between gap-4">
                    <div className="min-w-0 flex items-center gap-3">
                      <span className="text-xl">📄</span>
                      <div>
                        <div className={`text-sm font-medium ${dark ? 'text-slate-200' : 'text-slate-800'}`}>{d.filename}</div>
                        <div className={`text-xs mt-0.5 ${dark ? 'text-slate-500' : 'text-slate-400'}`}>
                          {fmtDate(d.createdAt)} · {d.fileType}
                          {d.status === 'ready' && <span className="ml-2 text-green-500">● 就绪</span>}
                          {d.status === 'processing' && <span className="ml-2 text-amber-500">● 处理中</span>}
                        </div>
                      </div>
                    </div>
                    <button onClick={() => preview(d.id, d.filename)}
                      className={`text-sm ${dark ? 'text-slate-500 hover:text-blue-400' : 'text-slate-400 hover:text-blue-500'} transition-colors shrink-0`}
                      title="预览">
                      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" /></svg>
                    </button>
                    <button onClick={() => del(d.id)}
                      className={`text-sm ${dark ? 'text-slate-500 hover:text-red-400' : 'text-slate-400 hover:text-red-500'} transition-colors shrink-0`}
                      title="删除">
                      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Preview Modal */}
      {previewDoc && (
        <div className="fixed inset-0 z-50 flex items-center justify-center" onClick={() => setPreviewDoc(null)}>
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />
          <div className={`relative w-full max-w-3xl max-h-[85vh] rounded-2xl border shadow-2xl flex flex-col animate-fadeIn ${
            dark ? 'bg-slate-900 border-slate-700/50' : 'bg-white border-slate-200'
          }`} onClick={(e) => e.stopPropagation()}>
            <div className={`flex items-center justify-between px-6 py-4 border-b ${dark ? 'border-slate-700/50' : 'border-slate-200'}`}>
              <h3 className={`text-base font-semibold ${dark ? 'text-slate-200' : 'text-slate-800'}`}>{previewDoc.filename}</h3>
              <button onClick={() => setPreviewDoc(null)} className={`text-lg ${dark ? 'text-slate-400 hover:text-white' : 'text-slate-500 hover:text-slate-700'}`}>&times;</button>
            </div>
            <div className="overflow-y-auto px-6 py-5">
              <pre className={`text-sm leading-7 whitespace-pre-wrap font-sans ${dark ? 'text-slate-300' : 'text-slate-700'}`}>{previewDoc.content}</pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
