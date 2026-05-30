import { BrowserRouter, Routes, Route } from 'react-router-dom';
import ChatInterface from './pages/ChatInterface';
import AdminPanel from './pages/AdminPanel';
import CopilotWorkspace from './pages/CopilotWorkspace';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ChatInterface />} />
        <Route path="/admin" element={<AdminPanel />} />
        <Route path="/copilot" element={<CopilotWorkspace />} />
      </Routes>
    </BrowserRouter>
  );
}
