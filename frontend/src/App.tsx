import { BrowserRouter, Routes, Route } from 'react-router-dom';
import ChatInterface from './pages/ChatInterface';
import AdminPanel from './pages/AdminPanel';
import CopilotOverview from './pages/CopilotOverview';
import CopilotWorkspace from './pages/CopilotWorkspace';
import RagPanel from './pages/RagPanel';
import ModelConfigPage from './pages/ModelConfigPage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ChatInterface />} />
        <Route path="/admin" element={<AdminPanel />} />
        <Route path="/copilot" element={<CopilotOverview />} />
        <Route path="/copilot/workflow" element={<CopilotWorkspace />} />
        <Route path="/rag" element={<RagPanel />} />
        <Route path="/model-config" element={<ModelConfigPage />} />
      </Routes>
    </BrowserRouter>
  );
}
