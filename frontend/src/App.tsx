import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import ChatInterface from './pages/ChatInterface';
import AdminPanel from './pages/AdminPanel';
import CopilotOverview from './pages/CopilotOverview';
import CopilotWorkspace from './pages/CopilotWorkspace';
import RagPanel from './pages/RagPanel';
import ModelConfigPage from './pages/ModelConfigPage';
import LogViewer from './pages/LogViewer';
import TaskCenter from './pages/TaskCenter';
import TokenAnalytics from './pages/TokenAnalytics';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/chat" replace />} />
        <Route path="/chat" element={<ChatInterface />} />
        <Route path="/chat/:id" element={<ChatInterface />} />
        <Route path="/admin" element={<AdminPanel />} />
        <Route path="/admin/tokens" element={<TokenAnalytics />} />
        <Route path="/copilot" element={<CopilotOverview />} />
        <Route path="/copilot/workflow" element={<CopilotWorkspace />} />
        <Route path="/copilot/tasks" element={<TaskCenter />} />
        <Route path="/rag" element={<RagPanel />} />
        <Route path="/model-config" element={<ModelConfigPage />} />
        <Route path="/admin/logs" element={<LogViewer />} />
      </Routes>
    </BrowserRouter>
  );
}
