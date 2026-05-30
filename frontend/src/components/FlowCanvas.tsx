import { useCallback, useEffect } from 'react';
import {
  ReactFlow, Background, Controls, MiniMap, Node, NodeProps, Connection,
  Handle, Position, addEdge, useNodesState, useEdgesState, MarkerType,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useWorkflowStore } from '../store/workflowStore';

/* ── types ── */
type WorkflowNodeData = {
  label?: string; type?: string; provider?: string; model?: string; skillName?: string;
  inputParams?: unknown[]; outputParams?: unknown[];
  maxSteps?: number; agentStrategy?: string; tools?: string[];
  knowledgeBaseId?: string; knowledgeBaseName?: string;
  conditions?: Array<{ id: string; field?: string; operator?: string; value?: string }>;
};
type WorkflowCardNode = Node<WorkflowNodeData, 'workflow'>;

const EDGE_STYLE = { stroke: '#8b9bb4', strokeWidth: 2 };
const EDGE_MARKER = { type: MarkerType.ArrowClosed, width: 20, height: 20 };

const nodeMeta: Record<string, { icon: string; tone: string }> = {
  input: { icon: 'IN', tone: 'bg-emerald-500' },
  output: { icon: 'OUT', tone: 'bg-violet-500' },
  llm: { icon: 'AI', tone: 'bg-blue-500' },
  react_agent: { icon: 'AI', tone: 'bg-blue-500' },
  openai: { icon: 'AI', tone: 'bg-blue-500' },
  deepseek: { icon: 'DS', tone: 'bg-blue-500' },
  qwen: { icon: 'QW', tone: 'bg-blue-500' },
  zhipu: { icon: 'ZP', tone: 'bg-blue-500' },
  tts: { icon: 'TTS', tone: 'bg-amber-500' },
  weather: { icon: 'WX', tone: 'bg-amber-500' },
  tool: { icon: 'TL', tone: 'bg-amber-500' },
  condition: { icon: 'IF', tone: 'bg-orange-500' },
};
const getMeta = (t?: string) => nodeMeta[t || ''] || { icon: 'FN', tone: 'bg-slate-500' };

/* ── custom node card (PaiAgent style) ── */
const WorkflowNodeCard = ({ data, selected }: NodeProps<WorkflowCardNode>) => {
  const d = data as WorkflowNodeData;
  const type = d.type || '';
  const meta = getMeta(type);
  const inputCount = Array.isArray(d.inputParams) ? d.inputParams.length : 0;
  const outputCount = Array.isArray(d.outputParams) ? d.outputParams.length : 0;
  const modelLabel = d.model || d.provider || d.skillName || type;
  const isCondition = type === 'condition';
  const conditions = isCondition && Array.isArray(d.conditions) ? d.conditions : [];
  const handleCount = isCondition ? conditions.length + 1 : 0;

  return (
    <div className={`rounded-xl border-2 shadow-lg bg-white dark:bg-slate-800 min-w-[160px] ${selected ? 'border-blue-400 ring-2 ring-blue-400/20' : 'border-slate-200 dark:border-slate-600'}`}>
      {type !== 'input' && <Handle type="target" position={Position.Top} className="!w-2.5 !h-2.5 !bg-slate-400 !border-2 !border-white dark:!border-slate-800" />}
      {/* header */}
      <div className="flex items-center gap-2 p-3 pb-1.5">
        <div className={`w-7 h-7 rounded-lg ${meta.tone} flex items-center justify-center text-[10px] font-bold text-white shrink-0`}>{meta.icon}</div>
        <div className="min-w-0">
          <div className="text-[12px] font-semibold text-slate-800 dark:text-slate-200 truncate">{d.label || type || '未命名'}</div>
          <div className="text-[10px] text-slate-400 truncate">{modelLabel}</div>
        </div>
      </div>
      {/* conditions */}
      {isCondition && conditions.length > 0 && (
        <div className="px-3 pb-2 space-y-0.5">
          {conditions.map((c, i) => (
            <div key={c.id} className="flex items-center gap-1.5 text-[10px]">
              <span className="w-4 h-4 rounded-full bg-emerald-500 text-white text-[9px] flex items-center justify-center font-bold">{i + 1}</span>
              <span className="text-slate-600 dark:text-slate-400">{c.field} {c.operator} {c.value}</span>
            </div>
          ))}
          <div className="flex items-center gap-1.5 text-[10px]">
            <span className="w-4 h-4 rounded-full bg-slate-400 text-white text-[9px] flex items-center justify-center font-bold">D</span>
            <span className="text-slate-400">否则</span>
          </div>
        </div>
      )}
      {/* footer */}
      <div className="flex items-center justify-between px-3 pb-2.5 text-[10px] text-slate-400 border-t border-slate-100 dark:border-slate-700 pt-1.5">
        <span>{type || 'node'}</span>
        <span>{inputCount} in / {outputCount} out</span>
      </div>
      {/* handles */}
      {type === 'output' ? null : isCondition ? (
        <>
          {conditions.map((c, i) => (
            <Handle key={c.id} type="source" position={Position.Bottom} id={c.id}
              className="!w-2.5 !h-2.5 !bg-emerald-400 !border-2 !border-white dark:!border-slate-800"
              style={{ left: `${((i + 1) / (handleCount + 1)) * 100}%` }} />
          ))}
          <Handle type="source" position={Position.Bottom} id="default"
            className="!w-2.5 !h-2.5 !bg-slate-400 !border-2 !border-white dark:!border-slate-800"
            style={{ left: `${(handleCount / (handleCount + 1)) * 100}%` }} />
        </>
      ) : (
        <Handle type="source" position={Position.Bottom} className="!w-2.5 !h-2.5 !bg-slate-400 !border-2 !border-white dark:!border-slate-800" />
      )}
    </div>
  );
};
const nodeTypes = { workflow: WorkflowNodeCard };

/* ── FlowCanvas ── */
export default function FlowCanvas({ onNodeClick }: { onNodeClick: (node: Node) => void }) {
  const { nodes: storeNodes, edges: storeEdges, setNodes: setStoreNodes, setEdges: setStoreEdges } = useWorkflowStore();
  const [nodes, setNodes, onNodesChange] = useNodesState(storeNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(storeEdges);

  useEffect(() => { setNodes(storeNodes); }, [storeNodes, setNodes]);
  useEffect(() => { setEdges(storeEdges); }, [storeEdges, setEdges]);

  const handleNodesChange = useCallback((changes: any) => { onNodesChange(changes); setTimeout(() => setNodes((ns) => { setStoreNodes(ns); return ns; }), 0); }, [onNodesChange, setNodes, setStoreNodes]);
  const handleEdgesChange = useCallback((changes: any) => { onEdgesChange(changes); setTimeout(() => setEdges((es) => { setStoreEdges(es); return es; }), 0); }, [onEdgesChange, setEdges, setStoreEdges]);

  const handleConnect = useCallback((conn: Connection) => {
    setEdges((eds) => {
      const updated = addEdge({ ...conn, type: 'smoothstep', style: EDGE_STYLE, markerEnd: EDGE_MARKER }, eds);
      setStoreEdges(updated); return updated;
    });
  }, [setEdges, setStoreEdges]);

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    const type = e.dataTransfer.getData('application/reactflow-type');
    const label = e.dataTransfer.getData('application/reactflow-label') || type;
    if (!type) return;
    const rect = (e.target as HTMLElement).closest('.react-flow')?.getBoundingClientRect();
    const pos = rect ? { x: e.clientX - rect.left - 80, y: e.clientY - rect.top - 30 } : { x: 100, y: 100 };
    const nn: Node = { id: `${type}-${Date.now()}`, type: 'workflow', position: pos, data: { label, type } };
    setNodes((ns) => { const u = [...ns, nn]; setStoreNodes(u); return u; });
  }, [setNodes, setStoreNodes]);

  return (
    <div className="h-full w-full">
      <ReactFlow nodes={nodes} edges={edges}
        onNodesChange={handleNodesChange} onEdgesChange={handleEdgesChange}
        onConnect={handleConnect} onDrop={onDrop} onDragOver={(e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; }}
        onNodeClick={(_, node) => onNodeClick(node)}
        nodeTypes={nodeTypes} defaultViewport={{ x: 0, y: 0, zoom: 0.85 }}
        defaultEdgeOptions={{ type: 'smoothstep', style: EDGE_STYLE, markerEnd: EDGE_MARKER }}
        proOptions={{ hideAttribution: true }}
      >
        <Background color="#d8dee9" gap={18} size={1.2} />
        <Controls />
        <MiniMap pannable zoomable nodeColor={(n) => getMeta(String(n.data?.type || '')).tone.includes('emerald') ? '#22c55e' : '#64748b'} />
      </ReactFlow>
    </div>
  );
}
