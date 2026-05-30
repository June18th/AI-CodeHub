import { create } from 'zustand';

interface AuthStore {
  token: string | null;
  role: string | null;
  username: string | null;
  avatar: string | null;
  isLoggedIn: boolean;
  isAdmin: boolean;
  login: (token: string, role: string, username: string, avatar?: string) => void;
  setProfile: (username: string, avatar: string) => void;
  logout: () => void;
}

function loadAuth() {
  try {
    const saved = localStorage.getItem('aicodehub-auth');
    if (saved) return JSON.parse(saved);
  } catch {}
  return {};
}

export const useAuthStore = create<AuthStore>((set) => {
  const saved = loadAuth();
  return {
    token: saved.token ?? null,
    role: saved.role ?? null,
    username: saved.username ?? null,
    avatar: saved.avatar ?? null,
    isLoggedIn: !!saved.token,
    isAdmin: saved.role === 'admin',
    login: (token, role, username, avatar) => {
      const auth = { token, role, username, avatar: avatar || saved.avatar || null };
      localStorage.setItem('aicodehub-auth', JSON.stringify(auth));
      set({ token, role, username, avatar: avatar || saved.avatar || null, isLoggedIn: true, isAdmin: role === 'admin' });
    },
    setProfile: (username, avatar) => {
      const prev = loadAuth();
      const auth = { ...prev, username, avatar };
      localStorage.setItem('aicodehub-auth', JSON.stringify(auth));
      set({ username, avatar });
    },
    logout: () => {
      localStorage.removeItem('aicodehub-auth');
      set({ token: null, role: null, username: null, avatar: null, isLoggedIn: false, isAdmin: false });
    },
  };
});
