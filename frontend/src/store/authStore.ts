import { create } from 'zustand';

interface AuthStore {
  token: string | null;
  refreshToken: string | null;
  role: string | null;
  username: string | null;
  avatar: string | null;
  isLoggedIn: boolean;
  isAdmin: boolean;
  login: (token: string, refreshToken: string, role: string, username: string, avatar?: string) => void;
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

function persist(auth: Record<string, string | null>) {
  localStorage.setItem('aicodehub-auth', JSON.stringify(auth));
}

export const useAuthStore = create<AuthStore>((set) => {
  const saved = loadAuth();
  return {
    token: saved.token ?? null,
    refreshToken: saved.refreshToken ?? null,
    role: saved.role ?? null,
    username: saved.username ?? null,
    avatar: saved.avatar ?? null,
    isLoggedIn: !!saved.token,
    isAdmin: saved.role === 'admin',
    login: (token, refreshToken, role, username, avatar) => {
      const auth = { token, refreshToken, role, username, avatar: avatar || saved.avatar || null };
      persist(auth);
      set({ token, refreshToken, role, username, avatar: avatar || saved.avatar || null, isLoggedIn: true, isAdmin: role === 'admin' });
    },
    setProfile: (username, avatar) => {
      const prev = loadAuth();
      const auth = { ...prev, username, avatar };
      persist(auth);
      set({ username, avatar });
    },
    logout: () => {
      localStorage.removeItem('aicodehub-auth');
      set({ token: null, refreshToken: null, role: null, username: null, avatar: null, isLoggedIn: false, isAdmin: false });
    },
  };
});

export async function apiFetch(url: string, options?: RequestInit): Promise<Response> {
  let res = await fetch(url, options);
  if (res.status === 401) {
    const saved = loadAuth();
    if (saved.refreshToken) {
      const refreshRes = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: saved.refreshToken }),
      });
      if (refreshRes.ok) {
        const data = await refreshRes.json();
        const auth = { ...saved, token: data.data.token };
        persist(auth);
        // Retry original request with new token
        const headers = new Headers(options?.headers);
        headers.set('Authorization', `Bearer ${data.data.token}`);
        res = await fetch(url, { ...options, headers });
      } else {
        localStorage.removeItem('aicodehub-auth');
        window.location.href = '/chat';
      }
    }
  }
  return res;
}
