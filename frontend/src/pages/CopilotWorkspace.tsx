import React, { useState, useEffect, useCallback } from 'react';
import { ReactFlowProvider, type Node as RFNode } from '@xyflow/react';
import { useAuthStore } from '../store/authStore';
import { useThemeStore } from '../store/themeStore';
import { useWorkflowStore } from '../store/workflowStore';
import FlowCanvas from '../components/FlowCanvas';
import NodePanel from '../components/NodePanel';
import { fmtDate } from '../utils/format';

function getRefParams(nodes: RFNode[], edges: { source: string; target: string }[], currentNodeId: string): string[] {
  const upstreamIds = edges.filter((e) => e.target === currentNodeId).map((e) => e.source);
  const params: string[] = [];
  upstreamIds.forEach((uid) => {
    const n = nodes.find((x) => x.id === uid);
    if (!n) return;
    const outputs = (n.data as Record<string,unknown>).outputParams as Array<{name:string}> | undefined;
    const label = (n.data as Record<string,unknown>).label as string || n.id;
    if (outputs && outputs.length > 0) {
      outputs.forEach((p) => params.push(label + '.' + p.name));
    } else {
      params.push(label + '.output');
    }
  });
  return params;
}

export default function CopilotWorkspace() {
  const dark = useThemeStore((s) => s.theme === 'dark');
  const store = useWorkflowStore();
  const token = useAuthStore((s) => s.token);
  const [wfs, setWfs] = useState<{id:number;name:string;definition?:string;createdAt?:string}[]>([]);
  const [wfName, setWfName] = useState('未命名工作流');
  const [showWfList, setShowWfList] = useState(false);
  const [activeId, setActiveId] = useState<number|null>(null);
  const [output, setOutput] = useState('');
  const [debugOutput, setDebugOutput] = useState('');
  const [running, setRunning] = useState(false);
  const [wfInput, setWfInput] = useState('');
  const [debugInput, setDebugInput] = useState('');
  const [debugOpen, setDebugOpen] = useState(false);
  const [modelConfigs, setModelConfigs] = useState<{id:number;provider:string;configName:string;apiUrl:string;apiKey?:string;model:string;temperature:number;isDefault:number}[]>([]);

  const fetchWfs = useCallback(() => { if(!token)return; fetch('/api/v1/workflows',{headers:{Authorization:'Bearer '+token}}).then(r=>r.json()).then(d=>setWfs(d.data??[])); }, [token]);
  const fetchMCs = useCallback(() => { if(!token)return; fetch('/api/v1/model-configs',{headers:{Authorization:'Bearer '+token}}).then(r=>r.json()).then(d=>setModelConfigs(d.data??[])); }, [token]);
  useEffect(()=>{fetchWfs();fetchMCs();},[token]);

  const save = async () => { if(!token)return; const def=JSON.stringify(store.nodes.map(n=>{ const d=n.data as Record<string,unknown>; return {...d, id:d.label||n.id, dependsOn:store.edges.filter(e=>e.target===n.id).map(e=>e.source)}; })); const isUpd=activeId!=null; const res=await fetch(isUpd?'/api/v1/workflows/'+activeId:'/api/v1/workflows',{method:isUpd?'PUT':'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify({name:wfName,definition:def})}); const d=await res.json(); if(d.code===200){setActiveId(d.data.id);fetchWfs();} };
  const loadWf = (id:number) => { const wf=wfs.find(w=>w.id===id); if(!wf)return; setActiveId(id);setWfName(wf.name); if(wf.definition){try{const steps=JSON.parse(wf.definition); const ns=steps.map((s:Record<string,unknown>,i:number)=>{ const {id,type,prompt,dependsOn,...rest}=s; return {id:s.id as string,type:'workflow',position:{x:80+(i%3)*300,y:60+Math.floor(i/3)*150},data:{label:id||(s.id as string),type:type||'llm',prompt:prompt||'',dependsOn:dependsOn||[],...rest}}; }); const es=steps.flatMap((s:Record<string,unknown>)=>(s.dependsOn as string[]||[]).map((dep:string)=>({id:dep+'->'+s.id,source:dep,target:s.id,type:'smoothstep',style:{stroke:'#8b9bb4',strokeWidth:2},markerEnd:{type:'arrowclosed' as const,width:20,height:20}}))); store.setNodes(ns);store.setEdges(es);}catch{}} };
  const runWf = async (input?: string, setFn?: React.Dispatch<React.SetStateAction<string>>) => { if(!activeId)return;const sFn=setFn||setOutput;sFn('');setRunning(true);const inp=input??'';const qs=inp?('&input='+encodeURIComponent(inp)):'';const r=await fetch('/api/v1/workflows/'+activeId+'/run?params=%7B%7D'+qs,{headers:{Authorization:'Bearer '+token}});const reader=r.body?.getReader();if(!reader){setRunning(false);return;}const dec=new TextDecoder();let buf='';while(true){const{value,done}=await reader.read();if(done)break;buf+=dec.decode(value,{stream:true});const lines=buf.split('\n');buf=lines.pop()??'';for(const l of lines)if(l.startsWith('data:')){const t=l.slice(5).trim();if(t==='[DONE]'||t.startsWith('FINAL:'))continue;if(t.startsWith('[TOKEN]')){sFn(o=>o+'\n'+t+'\n');continue;}if(t.startsWith('--- ')){sFn(o=>o+'\n\n'+t+'\n'+'='.repeat(30)+'\n');continue;}sFn(o=>o+t);}}setRunning(false); };

  const selNode = store.selectedNode;
  const selData = (selNode?.data||{}) as Record<string,unknown>;
  const isLLM = selData.type==='llm'||selData.type==='deepseek'||selData.type==='qwen'||selData.type==='zhipu'||selData.type==='openai';

  return (
    <div className={'flex flex-col h-screen '+(dark?'bg-[#0b0f14] text-slate-200':'bg-white text-slate-800')}>
      <div className="fixed inset-0 -z-10 pointer-events-none"><div className={'absolute inset-0 '+(dark?'bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-slate-900 via-[#0b0f14] to-[#0b0f14]':'bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-slate-50 via-white to-white')}/></div>
      <header className={'shrink-0 border-b '+(dark?'border-slate-800/40 bg-[#0b0f14]/70':'border-slate-200/60 bg-white/70')+' backdrop-blur-2xl'}>
        <div className="flex items-center justify-between px-6 h-16">
          <div className="flex items-center gap-6"><a href="/copilot" className={'text-sm font-medium '+(dark?'text-slate-400 hover:text-white':'text-slate-500 hover:text-slate-700')+' transition-colors'}>返回概览</a><span className="font-semibold text-base">工作流编辑器</span></div>
        </div>
      </header>
      <div className="flex-1 overflow-hidden">
        <ReactFlowProvider><div className="flex flex-col h-full">
          <div className={'flex items-center gap-2 px-3 py-2 shrink-0 border-b relative '+(dark?'border-slate-700/50':'border-slate-200')}>
            {/* Load workflow button */}
            <button onClick={()=>setShowWfList(!showWfList)} className={'flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-sm font-medium shrink-0 transition-colors '+(dark?'bg-slate-800 border-slate-700 text-slate-200 hover:bg-slate-700':'bg-slate-50 border-slate-200 text-slate-700 hover:bg-slate-100')} title="加载工作流">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"/></svg>
              {activeId ? (wfs.find(w=>w.id===activeId)?.name||'未命名') : '加载'}
            </button>
            {/* New workflow */}
            <button onClick={()=>{store.clear();setActiveId(null);setWfName('未命名工作流');}} className={'px-3 py-1.5 text-sm font-semibold rounded-lg border transition-colors shrink-0 '+(dark?'border-slate-700 text-blue-400 hover:bg-blue-500/10':'border-slate-200 text-blue-600 hover:bg-blue-50')} title="新建工作流">+ 新建</button>
            <input value={wfName} onChange={e=>setWfName(e.target.value)} placeholder="工作流名称" className={'w-40 px-3 py-1.5 rounded-lg border text-sm shrink-0 '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/>
            <button onClick={save} className="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors shrink-0">保存</button>
            <input value={wfInput} onChange={e=>setWfInput(e.target.value)} placeholder="输入问题后点运行..." className={'w-1/2 px-3 py-1.5 rounded-lg border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/>
            <button onClick={()=>runWf(wfInput,setOutput)} disabled={running||!activeId} className="px-3 py-1.5 text-sm font-semibold text-white bg-emerald-600 hover:bg-emerald-500 rounded-lg transition-colors shrink-0 disabled:opacity-50">运行</button>
            <button onClick={()=>setDebugOpen(!debugOpen)} className={'px-3 py-1.5 text-sm font-semibold rounded-lg transition-colors shrink-0 '+(debugOpen?'bg-amber-600 text-white':'bg-slate-100 dark:bg-slate-800 text-slate-500 hover:text-white')}>调试</button>

            {/* Workflow list dropdown */}
            {showWfList && <div className="fixed inset-0 z-40" onClick={()=>setShowWfList(false)}/>}
            {showWfList && (
              <div className={'absolute top-full left-28 mt-1 w-96 rounded-xl border shadow-2xl z-50 overflow-hidden animate-fadeIn '+(dark?'bg-slate-900 border-slate-700':'bg-white border-slate-200')}>
                <div className={'px-4 py-2 flex items-center justify-between border-b '+(dark?'border-slate-700':'border-slate-200')}>
                  <span className="text-xs font-semibold text-slate-400 uppercase">工作流列表</span>
                  <button onClick={()=>setShowWfList(false)} className="text-slate-400 hover:text-white text-sm">×</button>
                </div>
                <div className="max-h-80 overflow-y-auto">
                  {wfs.length===0 ? <div className="p-6 text-center text-sm text-slate-400">暂无工作流，点击 + 新建 创建</div> :
                    wfs.map(w=>(
                      <div key={w.id} className={'flex items-center justify-between px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors border-b last:border-0 '+(dark?'border-slate-800':'border-slate-100')}>
                        <div className="min-w-0 flex-1">
                          <div className="text-sm font-medium text-slate-800 dark:text-slate-200 truncate">{w.name}</div>
                          <div className="text-[11px] text-slate-400 mt-0.5">{fmtDate(w.createdAt)}</div>
                        </div>
                        <div className="flex items-center gap-1.5 shrink-0 ml-3">
                          <button onClick={()=>{loadWf(w.id);setShowWfList(false);}} className="px-2.5 py-1 text-[11px] font-semibold text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-500/10 rounded-lg transition-colors">加载</button>
                          <button onClick={async()=>{await fetch('/api/v1/workflows/'+w.id,{method:'DELETE',headers:{Authorization:'Bearer '+token}});fetchWfs();if(activeId===w.id){store.clear();setActiveId(null);setWfName('未命名工作流');}}} className="px-2 py-1 text-[11px] font-medium text-red-400 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors">删除</button>
                        </div>
                      </div>
                    ))
                  }
                </div>
              </div>
            )}
          </div>
          <div className="flex-1 flex min-h-0">
            <div className="w-[300px] shrink-0"><NodePanel onDragStart={(e,type,label)=>{e.dataTransfer.setData('application/reactflow-type',type);e.dataTransfer.setData('application/reactflow-label',label);e.dataTransfer.effectAllowed='move';}}/></div>
            <div className="flex-1"><FlowCanvas onNodeClick={n=>store.setSelectedNode(n)}/></div>
            {selNode?(
              <div className={'w-[420px] shrink-0 border-l overflow-y-auto '+(dark?'border-slate-700/50 bg-slate-900/60':'border-slate-200 bg-white')}>
                <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-700/50 flex items-center justify-between"><div><p className="text-xs text-slate-400">Inspector</p><h3 className="text-sm font-semibold">节点配置</h3></div><button onClick={()=>store.setSelectedNode(null)} className="text-slate-400 hover:text-white text-lg">×</button></div>
                <div className="p-4 space-y-4">
                  <div className={'flex items-center gap-3 p-3 rounded-xl '+(dark?'bg-slate-800':'bg-slate-50')}><div className="w-10 h-10 rounded-xl bg-blue-500 flex items-center justify-center text-xs font-bold text-white shrink-0">{String(selData.label||'?').slice(0,2).toUpperCase()}</div><div className="min-w-0"><p className="text-sm font-semibold truncate">{String(selData.label||selNode.id)}</p><p className="text-xs text-slate-400 truncate">{String(selData.type||'node')} · {selNode.id}</p></div></div>

                  {selData.type==='input'&&(<div className="space-y-3"><div><label className="text-xs text-slate-400 block mb-1">变量名</label><input value="user_input" disabled className={'w-full px-3 py-2 rounded-lg border text-sm opacity-60 '+(dark?'bg-slate-800 border-slate-700':'bg-slate-100 border-slate-200')}/></div><div><label className="text-xs text-slate-400 block mb-1">变量类型</label><input value="String" disabled className={'w-full px-3 py-2 rounded-lg border text-sm opacity-60 '+(dark?'bg-slate-800 border-slate-700':'bg-slate-100 border-slate-200')}/></div></div>)}

                  {selData.type==='output'&&(<div className="space-y-4"><div><div className="flex items-center justify-between mb-2"><label className="text-xs text-slate-400 font-medium">输出配置</label><button onClick={()=>{const arr=[...(selData.outputParams as Array<{name:string;type:string;value:string;ref:string}>||[])];arr.push({name:'',type:'output',value:'',ref:''});store.updateNode(selNode.id,{outputParams:arr});}} className="text-xs text-blue-500 hover:text-blue-400">+ 添加参数</button></div>{(selData.outputParams as Array<{name:string;type:string;value:string;ref:string}>||[]).map((p,i)=>(<div key={i} className="p-3 rounded-lg border border-slate-200 dark:border-slate-700 space-y-2"><div className="flex gap-2"><input value={p.name} onChange={e=>{const arr=[...selData.outputParams as Array<{name:string;type:string;value:string;ref:string}>||[]];arr[i]={...arr[i],name:e.target.value};store.updateNode(selNode.id,{outputParams:arr});}} placeholder="参数名" className={'flex-1 px-2 py-1.5 rounded border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/><select value={p.type||'output'} onChange={e=>{const arr=[...selData.outputParams as Array<{name:string;type:string;value:string;ref:string}>||[]];arr[i]={...arr[i],type:e.target.value};store.updateNode(selNode.id,{outputParams:arr});}} className={'w-20 px-2 py-1.5 rounded border text-xs '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}><option value="output">输出</option><option value="input">输入</option><option value="reference">引用</option></select><button onClick={()=>{const arr=[...selData.outputParams as Array<{name:string;type:string;value:string;ref:string}>||[]];arr.splice(i,1);store.updateNode(selNode.id,{outputParams:arr});}} className="text-red-400 hover:text-red-300 text-sm shrink-0">×</button></div>{p.type==='reference'?<select value={p.ref} onChange={e=>{const arr=[...selData.outputParams as Array<{name:string;type:string;value:string;ref:string}>||[]];arr[i]={...arr[i],ref:e.target.value};store.updateNode(selNode.id,{outputParams:arr});}} className={'w-full px-2 py-1.5 rounded border text-xs '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}><option value="">选择上游参数</option>{getRefParams(store.nodes,store.edges,selNode.id).map(r=>(<option key={r} value={r}>{r}</option>))}</select>:p.type==='input'?<input value={p.value} onChange={e=>{const arr=[...selData.outputParams as Array<{name:string;type:string;value:string;ref:string}>||[]];arr[i]={...arr[i],value:e.target.value};store.updateNode(selNode.id,{outputParams:arr});}} placeholder="输入值" className={'w-full px-2 py-1.5 rounded border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/>:<p className="text-xs text-slate-400">自动透传上游节点结果</p>}</div>))}{((selData.outputParams as Array<unknown>||[]).length===0)&&<p className="text-xs text-slate-400 text-center py-3">点击上方按钮创建输出参数</p>}<div className="border-t border-slate-200 dark:border-slate-700 pt-3"><label className="text-xs text-slate-400 block mb-1">回答模板</label><textarea value={String(selData.responseContent||'')} onChange={e=>store.updateNode(selNode.id,{responseContent:e.target.value})} rows={4} placeholder="{{参数名}} 引用上面的输出参数" className={'w-full px-3 py-2 rounded-lg border text-sm focus:outline-none focus:ring-1 focus:ring-blue-400 resize-none font-mono '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/></div></div></div>)}

                  {isLLM&&(<div className="space-y-4">
                    <div><label className="text-xs text-slate-400 block mb-1 font-medium">模型配置</label><select value={String(selData.configId||'manual')} onChange={e=>{const v=e.target.value;if(v==='manual'){store.updateNode(selNode.id,{configId:undefined});return;}const cfg=modelConfigs.find(c=>c.id===Number(v));if(cfg)store.updateNode(selNode.id,{configId:cfg.id,provider:cfg.provider,model:cfg.model,apiUrl:cfg.apiUrl,apiKey:cfg.apiKey,temperature:cfg.temperature});}} className={'w-full px-3 py-2 rounded-lg border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}><option value="manual">手动输入</option>{modelConfigs.map(c=>(<option key={c.id} value={c.id}>{c.configName} ({c.provider}/{c.model}){c.isDefault===1?' (默认)':''}</option>))}</select><a href="/model-config" className="text-xs text-blue-500 hover:text-blue-400 mt-1 inline-block">管理模型配置</a></div>
                    {!(selData.configId as number)&&(<div className="space-y-2 p-3 rounded-lg border border-dashed border-slate-300 dark:border-slate-600"><input value={String(selData.provider||'')} onChange={e=>store.updateNode(selNode.id,{provider:e.target.value})} placeholder="供应商 (deepseek/qwen/openai...)" className={'w-full px-3 py-2 rounded border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/><input value={String(selData.model||'')} onChange={e=>store.updateNode(selNode.id,{model:e.target.value})} placeholder="模型名 (deepseek-chat)" className={'w-full px-3 py-2 rounded border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/><input value={String(selData.temperature||'0.7')} onChange={e=>store.updateNode(selNode.id,{temperature:parseFloat(e.target.value)||0.7})} type="number" min={0} max={2} step={0.1} placeholder="温度 (0-2)" className={'w-full px-3 py-2 rounded border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/></div>)}
                    {(selData.configId as number)&&<div className={'text-xs p-3 rounded-lg '+(dark?'bg-slate-800':'bg-slate-50')}>供应商: {String(selData.provider||'')} / 模型: {String(selData.model||'')} / 温度: {String(selData.temperature||'0.7')}</div>}
                    <div><div className="flex items-center justify-between mb-2"><label className="text-xs text-slate-400 font-medium">输入参数</label><button onClick={()=>{const arr=[...selData.inputParams as Array<{name:string;value:string;ref:string}>||[]];arr.push({name:'',value:'',ref:''});store.updateNode(selNode.id,{inputParams:arr});}} className="text-xs text-blue-500 hover:text-blue-400">+ 添加</button></div>{(selData.inputParams as Array<{name:string;value:string;ref:string}>||[]).map((p,i)=>(<div key={i} className="flex gap-2 mb-2"><input value={p.name} onChange={e=>{const arr=[...selData.inputParams as Array<{name:string;value:string;ref:string}>||[]];arr[i]={...arr[i],name:e.target.value};store.updateNode(selNode.id,{inputParams:arr});}} placeholder="参数名" className={'flex-1 px-2 py-1.5 rounded border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/><select value={p.ref?'ref':'val'} onChange={e=>{const arr=[...selData.inputParams as Array<{name:string;value:string;ref:string}>||[]];if(e.target.value==='ref'){arr[i]={...arr[i],ref:' '};}else{arr[i]={...arr[i],ref:''};}store.updateNode(selNode.id,{inputParams:arr});}} className={'w-16 px-1 py-1.5 rounded border text-xs '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}><option value="val">输入</option><option value="ref">引用</option></select>{p.ref?<select value={p.ref} onChange={e=>{const arr=[...selData.inputParams as Array<{name:string;value:string;ref:string}>||[]];arr[i]={...arr[i],ref:e.target.value};store.updateNode(selNode.id,{inputParams:arr});}} className={'flex-1 px-2 py-1.5 rounded border text-xs '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}>{getRefParams(store.nodes,store.edges,selNode.id).map(r=>(<option key={r} value={r}>{r}</option>))}</select>:<input value={p.value} onChange={e=>{const arr=[...selData.inputParams as Array<{name:string;value:string;ref:string}>||[]];arr[i]={...arr[i],value:e.target.value};store.updateNode(selNode.id,{inputParams:arr});}} placeholder="值" className={'flex-1 px-2 py-1.5 rounded border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/>}</div>))}</div>
                    <div><div className="flex items-center justify-between mb-2"><label className="text-xs text-slate-400 font-medium">输出参数</label><button onClick={()=>{const arr=[...selData.outputParams as Array<{name:string;description:string}>||[]];arr.push({name:'',description:''});store.updateNode(selNode.id,{outputParams:arr});}} className="text-xs text-blue-500 hover:text-blue-400">+ 添加</button></div>{(selData.outputParams as Array<{name:string;description:string}>||[]).map((p,i)=>(<div key={i} className="flex gap-2 mb-2"><input value={p.name} onChange={e=>{const arr=[...selData.outputParams as Array<{name:string;description:string}>||[]];arr[i]={...arr[i],name:e.target.value};store.updateNode(selNode.id,{outputParams:arr});}} placeholder="变量名" className={'flex-1 px-2 py-1.5 rounded border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/><input value={p.description} onChange={e=>{const arr=[...selData.outputParams as Array<{name:string;description:string}>||[]];arr[i]={...arr[i],description:e.target.value};store.updateNode(selNode.id,{outputParams:arr});}} placeholder="描述" className={'flex-1 px-2 py-1.5 rounded border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/></div>))}</div>
                    <div className="border-t border-slate-200 dark:border-slate-700 pt-3"><label className="text-xs text-slate-400 block mb-1 font-medium">提示词模板</label><textarea value={String(selData.prompt||'')} onChange={e=>store.updateNode(selNode.id,{prompt:e.target.value})} rows={6} placeholder="示例: 你是一个AI助手。用户输入: {{user_input}} 请用JSON格式回复。" className={'w-full px-3 py-2 rounded-lg border text-sm focus:outline-none focus:ring-1 focus:ring-blue-400 resize-none font-mono leading-relaxed '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/>{((selData.inputParams as Array<{name:string}>||[]).length>0)&&(<div className="flex flex-wrap gap-1.5 mt-2"><span className="text-xs text-slate-400">参数:</span>{(selData.inputParams as Array<{name:string}>||[]).map(p=>(<button key={p.name} onClick={()=>{store.updateNode(selNode.id,{prompt:(String(selData.prompt||'')+'{{'+p.name+'}}')});}} className="text-xs px-2 py-0.5 rounded-md bg-blue-50 dark:bg-blue-500/10 text-blue-600 dark:text-blue-400 hover:bg-blue-100 dark:hover:bg-blue-500/20 transition-colors font-mono cursor-pointer">{'{{'+p.name+'}}'}</button>))}</div>)}</div>
                    <div><label className="text-xs text-slate-400 block mb-1">Agent 策略</label><select value={String(selData.agentStrategy||'none')} onChange={e=>store.updateNode(selNode.id,{agentStrategy:e.target.value})} className={'w-full px-3 py-2 rounded-lg border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}><option value="none">普通调用</option><option value="react">ReAct</option></select></div>
                  </div>)}

                  {selData.type==='weather'&&(<div className="space-y-3"><input value={String(selData.city||'')} onChange={e=>store.updateNode(selNode.id,{city:e.target.value})} placeholder="城市名称 (如 北京)" className={'w-full px-3 py-2 rounded-lg border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/><select value={String(selData.extensions||'base')} onChange={e=>store.updateNode(selNode.id,{extensions:e.target.value})} className={'w-full px-3 py-2 rounded-lg border text-sm '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}><option value="base">实时天气</option><option value="all">4天预报</option></select></div>)}
                  {selData.type==='condition'&&(<div className="space-y-3">{(selData.conditions as Array<{id:string;field:string;operator:string;value:string}>||[{id:'c0',field:'',operator:'eq',value:''}]).map((c,i)=>(<div key={c.id} className={'p-3 rounded-lg border '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}><input value={c.field} onChange={e=>{const arr=[...selData.conditions as Array<{id:string;field:string;operator:string;value:string}>||[]];arr[i]={...arr[i],field:e.target.value};store.updateNode(selNode.id,{conditions:arr});}} placeholder="字段名" className={'w-full px-2 py-1.5 rounded border text-sm mb-2 '+(dark?'bg-slate-700 border-slate-600':'bg-white border-slate-200')}/><select value={c.operator} onChange={e=>{const arr=[...selData.conditions as Array<{id:string;field:string;operator:string;value:string}>||[]];arr[i]={...arr[i],operator:e.target.value};store.updateNode(selNode.id,{conditions:arr});}} className={'w-full px-2 py-1.5 rounded border text-sm mb-2 '+(dark?'bg-slate-700 border-slate-600':'bg-white border-slate-200')}><option value="eq">= (等于)</option><option value="neq">!= (不等于)</option><option value="gt">&gt; (大于)</option><option value="lt">&lt; (小于)</option><option value="contains">包含</option></select><input value={c.value} onChange={e=>{const arr=[...selData.conditions as Array<{id:string;field:string;operator:string;value:string}>||[]];arr[i]={...arr[i],value:e.target.value};store.updateNode(selNode.id,{conditions:arr});}} placeholder="值" className={'w-full px-2 py-1.5 rounded border text-sm '+(dark?'bg-slate-700 border-slate-600':'bg-white border-slate-200')}/></div>))}<button onClick={()=>{const arr=[...selData.conditions as Array<{id:string;field:string;operator:string;value:string}>||[]];arr.push({id:'c'+arr.length,field:'',operator:'eq',value:''});store.updateNode(selNode.id,{conditions:arr});}} className="w-full py-2 text-sm text-blue-500 border border-dashed border-blue-300 dark:border-blue-700 rounded-lg hover:bg-blue-50 dark:hover:bg-blue-500/10">+ 添加条件</button></div>)}

                  <div className="border-t border-slate-200 dark:border-slate-700 pt-3"><label className="text-xs text-slate-400 block mb-1">节点名称</label><input value={String(selData.label||'')} onChange={e=>store.updateNode(selNode.id,{label:e.target.value})} className={'w-full px-3 py-2 rounded-lg border text-sm focus:outline-none focus:ring-1 focus:ring-blue-400 '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/></div>
                  <button onClick={save} className="w-full py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors mt-3">保存配置</button>
                </div>
              </div>
            ):(
              <div className={'w-[420px] shrink-0 border-l flex items-center justify-center '+(dark?'border-slate-700/50 bg-slate-900/60':'border-slate-200 bg-white')}><div className="text-center"><div className="w-10 h-10 rounded-full bg-slate-200 dark:bg-slate-700 flex items-center justify-center text-slate-400 mx-auto mb-2 text-lg">+</div><p className="text-sm text-slate-400">点击画布节点查看配置</p></div></div>
            )}
          </div>
          <div className={'border-t p-4 h-40 overflow-y-auto shrink-0 '+(dark?'border-slate-700/50 bg-slate-900/60':'border-slate-200 bg-white')}><div className="flex items-center justify-between mb-2"><span className="text-sm font-semibold text-slate-500">执行输出</span>{output&&<button onClick={()=>setOutput('')} className="text-xs text-slate-400 hover:text-white">清除</button>}</div><pre className={'text-sm leading-6 whitespace-pre-wrap font-mono '+(dark?'text-slate-300':'text-slate-700')}>{output||'点击 ▶ 运行 查看工作流执行结果'}</pre></div>
        </div>
        {/* Debug Drawer */}
        {debugOpen && (
          <div className={'fixed right-0 top-0 bottom-0 w-[380px] z-50 flex flex-col shadow-2xl border-l animate-fadeIn '+(dark?'bg-slate-900 border-slate-700':'bg-white border-slate-200')}>
            <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200 dark:border-slate-700">
              <h3 className="text-sm font-semibold">调试面板</h3>
              <button onClick={()=>setDebugOpen(false)} className="text-slate-400 hover:text-white text-lg">×</button>
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              <div>
                <label className="text-xs text-slate-400 block mb-1 font-medium">Test Input</label>
                <textarea value={debugInput} onChange={e=>setDebugInput(e.target.value)} rows={4} placeholder="请输入测试文本，例如：你今天有进步么？" className={'w-full px-3 py-2 rounded-lg border text-sm resize-none focus:outline-none focus:ring-1 focus:ring-blue-400 '+(dark?'bg-slate-800 border-slate-700':'bg-slate-50 border-slate-200')}/>
              </div>
              <button onClick={()=>runWf(debugInput,setDebugOutput)} disabled={running||!activeId} className="w-full py-2.5 text-sm font-semibold text-white bg-emerald-600 hover:bg-emerald-500 rounded-lg transition-colors disabled:opacity-50">执行工作流</button>
              <div>
                <h4 className="text-xs font-semibold text-slate-400 mb-2 uppercase tracking-wider">Logs</h4>
                <div className={'rounded-lg border p-3 h-96 overflow-y-auto font-mono text-xs leading-relaxed whitespace-pre-wrap '+(dark?'bg-slate-950 border-slate-700 text-slate-300':'bg-slate-50 border-slate-200 text-slate-700')}>{debugOutput||'暂无日志，点击上方按钮执行工作流'}</div>
              </div>
            </div>
          </div>
        )}
        </ReactFlowProvider>
      </div>
    </div>
  );
}
