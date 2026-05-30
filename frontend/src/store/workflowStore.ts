import { create } from 'zustand';
import { Node, Edge } from '@xyflow/react';

export interface WorkflowState {
  nodes: Node[];
  edges: Edge[];
  selectedNode: Node | null;
  currentWorkflowId: number | null;
  setNodes: (nodes: Node[]) => void;
  setEdges: (edges: Edge[]) => void;
  setSelectedNode: (node: Node | null) => void;
  setCurrentWorkflowId: (id: number | null) => void;
  addNode: (node: Node) => void;
  updateNode: (id: string, data: Record<string, unknown>) => void;
  deleteNode: (id: string) => void;
  clear: () => void;
}

export const useWorkflowStore = create<WorkflowState>((set) => ({
  nodes: [],
  edges: [],
  selectedNode: null,
  currentWorkflowId: null,
  setNodes: (nodes) => set({ nodes }),
  setEdges: (edges) => set({ edges }),
  setSelectedNode: (node) => set({ selectedNode: node }),
  setCurrentWorkflowId: (id) => set({ currentWorkflowId: id }),
  addNode: (node) => set((state) => ({ nodes: [...state.nodes, node] })),
  updateNode: (id, data) => set((state) => {
    const updated = state.nodes.map((node) =>
      node.id === id ? { ...node, data: { ...node.data, ...data } } : node
    );
    return {
      nodes: updated,
      selectedNode: state.selectedNode?.id === id
        ? updated.find((n) => n.id === id) ?? null : state.selectedNode
    };
  }),
  deleteNode: (id) => set((state) => ({
    nodes: state.nodes.filter((n) => n.id !== id),
    edges: state.edges.filter((e) => e.source !== id && e.target !== id),
  })),
  clear: () => set({ nodes: [], edges: [], selectedNode: null, currentWorkflowId: null }),
}));
