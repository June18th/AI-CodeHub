import { create } from 'zustand';

interface AuthStore {
  token: string | null;
  role: string | null;
  username: string | null;
  isLoggedIn: boolean;
  isAdmin: boolean;
  login: (token: string, role: string, username: string) => void;
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
    isLoggedIn: !!saved.token,
    isAdmin: saved.role === 'admin',
    login: (token, role, username) => {
      const auth = { token, role, username };
      localStorage.setItem('aicodehub-auth', JSON.stringify(auth));
      set({ token, role, username, isLoggedIn: true, isAdmin: role === 'admin' });
    },
    logout: () => {
      localStorage.removeItem('aicodehub-auth');
      set({ token: null, role: null, username: null, isLoggedIn: false, isAdmin: false });
    },
  };
});
