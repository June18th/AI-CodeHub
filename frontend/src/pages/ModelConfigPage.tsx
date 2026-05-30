import { useState, useEffect } from 'react';
import { useAuthStore } from '../store/authStore';
import { useThemeStore } from '../store/themeStore';

const PROVIDERS = ['deepseek', 'qwen', 'zhipu', 'openai', 'gpt'];

export default function ModelConfigPage() {
  const token = useAuthStore((s) => s.token);
  const dark = useThemeStore((s) => s.theme === 'dark');
  const [configs, setConfigs] = useState<{id:number;provider:string;configName:string;apiUrl:string;apiKey:string;model:string;temperature:number;capabilities:string;isDefault:number}[]>([]);
  const [edit, setEdit] = useState<Partial<{id:number;provider:string;configName:string;apiUrl:string;apiKey:string;model:string;temperature:number;capabilities:string}>>({provider:'deepseek',configName:'',apiUrl:'',apiKey:'',model:'',temperature:0.7,capabilities:''});
  const [editingId, setEditingId] = useState<number | null>(null);

  const fetchAll = () => {
    fetch('/api/v1/model-configs', {headers:{Authorization:`Bearer ${token}`}}).then(r=>r.json()).then(d=>setConfigs(d.data??[]));
  };
  useEffect(()=>{fetchAll();},[]);

  const save = async () => {
    const url = editingId ? `/api/v1/model-configs/${editingId}` : '/api/v1/model-configs';
    const method = editingId ? 'PUT' : 'POST';
    await fetch(url, {method, headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`}, body:JSON.stringify(edit)});
    setEditingId(null); setEdit({provider:'deepseek',configName:'',apiUrl:'',apiKey:'',model:'',temperature:0.7,capabilities:''});
    fetchAll();
  };

  const del = async (id: number) => { await fetch(`/api/v1/model-configs/${id}`, {method:'DELETE',headers:{Authorization:`Bearer ${token}`}}); fetchAll(); };
  const setDefault = async (id: number) => { await fetch(`/api/v1/model-configs/${id}/default`, {method:'PUT',headers:{Authorization:`Bearer ${token}`}}); fetchAll(); };
  const startEdit = (c: typeof configs[0]) => { setEditingId(c.id); setEdit({...c}); };

  return (
    <div className={`flex flex-col h-screen ${dark ? 'bg-[#0b0f14]' : 'bg-white'}`}>
      <div className="fixed inset-0 -z-10 pointer-events-none"><div className={`absolute inset-0 ${dark ? 'bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-slate-900 via-[#0b0f14] to-[#0b0f14]' : 'bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-slate-50 via-white to-white'}`}/></div>
      <header className={`shrink-0 border-b ${dark?'border-slate-800/40 bg-[#0b0f14]/70':'border-slate-200/60 bg-white/70'} backdrop-blur-2xl`}>
        <div className="max-w-5xl mx-auto flex items-center justify-between px-6 h-16">
          <div className="flex items-center gap-4"><a href="/copilot" className={`text-[13px] font-medium ${dark?'text-slate-400 hover:text-white':'text-slate-500 hover:text-slate-700'}`}>← 返回</a><span className={`font-semibold ${dark?'text-slate-200':'text-slate-800'}`}>模型配置管理</span></div>
        </div>
      </header>
      <div className="flex-1 overflow-y-auto"><div className="max-w-5xl mx-auto px-6 py-8 space-y-6">
        {/* Form */}
        <div className={`rounded-2xl border p-5 ${dark?'bg-slate-900/60 border-slate-700/30':'bg-white border-slate-200 shadow-sm'}`}>
          <h3 className={`text-sm font-semibold mb-3 ${dark?'text-slate-200':'text-slate-800'}`}>{editingId ? '编辑配置' : '新建配置'}</h3>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="text-[11px] text-slate-400">配置名称</label><input value={edit.configName} onChange={e=>setEdit({...edit,configName:e.target.value})} className={`w-full px-2 py-1.5 rounded-lg border text-[12px] focus:outline-none focus:ring-1 focus:ring-blue-400 ${dark?'bg-slate-800 border-slate-700 text-slate-200':'bg-slate-50 border-slate-200'}`}/></div>
            <div><label className="text-[11px] text-slate-400">供应商</label><select value={edit.provider} onChange={e=>setEdit({...edit,provider:e.target.value})} className={`w-full px-2 py-1.5 rounded-lg border text-[12px] ${dark?'bg-slate-800 border-slate-700 text-slate-200':'bg-slate-50 border-slate-200'}`}>{PROVIDERS.map(p=><option key={p} value={p}>{p}</option>)}</select></div>
            <div><label className="text-[11px] text-slate-400">API 地址</label><input value={edit.apiUrl} onChange={e=>setEdit({...edit,apiUrl:e.target.value})} placeholder="https://api.deepseek.com/v1" className={`w-full px-2 py-1.5 rounded-lg border text-[12px] focus:outline-none focus:ring-1 focus:ring-blue-400 ${dark?'bg-slate-800 border-slate-700 text-slate-200':'bg-slate-50 border-slate-200'}`}/></div>
            <div><label className="text-[11px] text-slate-400">API 密钥</label><input value={edit.apiKey} onChange={e=>setEdit({...edit,apiKey:e.target.value})} type="password" className={`w-full px-2 py-1.5 rounded-lg border text-[12px] focus:outline-none focus:ring-1 focus:ring-blue-400 ${dark?'bg-slate-800 border-slate-700 text-slate-200':'bg-slate-50 border-slate-200'}`}/></div>
            <div><label className="text-[11px] text-slate-400">模型名称</label><input value={edit.model} onChange={e=>setEdit({...edit,model:e.target.value})} placeholder="deepseek-chat" className={`w-full px-2 py-1.5 rounded-lg border text-[12px] focus:outline-none focus:ring-1 focus:ring-blue-400 ${dark?'bg-slate-800 border-slate-700 text-slate-200':'bg-slate-50 border-slate-200'}`}/></div>
            <div><label className="text-[11px] text-slate-400">温度</label><input value={edit.temperature} onChange={e=>setEdit({...edit,temperature:parseFloat(e.target.value)||0.7})} type="number" min={0} max={2} step={0.1} className={`w-full px-2 py-1.5 rounded-lg border text-[12px] ${dark?'bg-slate-800 border-slate-700 text-slate-200':'bg-slate-50 border-slate-200'}`}/></div>
            <div><label className="text-[11px] text-slate-400">能力标签</label><input value={edit.capabilities} onChange={e=>setEdit({...edit,capabilities:e.target.value})} placeholder="text,function_calling" className={`w-full px-2 py-1.5 rounded-lg border text-[12px] focus:outline-none focus:ring-1 focus:ring-blue-400 ${dark?'bg-slate-800 border-slate-700 text-slate-200':'bg-slate-50 border-slate-200'}`}/></div>
          </div>
          <div className="flex gap-2 mt-3">
            <button onClick={save} className="px-4 py-2 text-[12px] font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors">{editingId?'更新':'创建'}</button>
            {editingId && <button onClick={()=>{setEditingId(null);setEdit({provider:'deepseek',configName:'',apiUrl:'',apiKey:'',model:'',temperature:0.7,capabilities:''});}} className="px-4 py-2 text-[12px] text-slate-500 border rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800">取消</button>}
          </div>
        </div>

        {/* List */}
        <div className={`rounded-2xl border overflow-hidden ${dark?'bg-slate-900/60 border-slate-700/30':'bg-white border-slate-200 shadow-sm'}`}>
          <div className={`px-5 py-3 border-b ${dark?'border-slate-700/30':'border-slate-200'}`}><h3 className={`text-base font-semibold ${dark?'text-slate-200':'text-slate-800'}`}>已有配置 ({configs.length})</h3></div>
          {configs.length===0 ? <div className={`p-8 text-center text-sm ${dark?'text-slate-500':'text-slate-400'}`}>暂无配置</div> :
          <div className={`divide-y ${dark?'divide-slate-800':'divide-slate-100'}`}>{configs.map(c=>(
            <div key={c.id} className="px-5 py-3 flex items-center gap-4 text-sm">
              <span className={`font-semibold ${dark?'text-slate-200':'text-slate-800'}`}>{c.configName}</span>
              {c.isDefault===1&&<span className="text-xs px-2 py-0.5 rounded bg-amber-100 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 font-medium">默认</span>}
              <span className={`${dark?'text-slate-400':'text-slate-500'}`}>{c.provider}</span>
              <span className={`${dark?'text-slate-400':'text-slate-500'}`}>{c.model}</span>
              <span className={`flex-1 truncate ${dark?'text-slate-500':'text-slate-400'}`}>{c.apiUrl}</span>
              {c.capabilities&&<span className="text-xs text-slate-400">{c.capabilities}</span>}
              <button onClick={()=>setDefault(c.id)} className="text-sm text-amber-500 hover:text-amber-400 font-medium shrink-0">默认</button>
              <button onClick={()=>startEdit(c)} className="text-sm text-blue-500 hover:text-blue-400 font-medium shrink-0">编辑</button>
              <button onClick={()=>del(c.id)} className="text-sm text-red-400 hover:text-red-300 font-medium shrink-0">删除</button>
            </div>
          ))}</div>}
        </div>
      </div></div>
    </div>
  );
}
