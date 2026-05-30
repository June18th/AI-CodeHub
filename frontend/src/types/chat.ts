export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  model?: string;
  timestamp: number;
  inputTokens?: number;
  outputTokens?: number;
}

export interface ModelOption {
  value: string;
  label: string;
}
