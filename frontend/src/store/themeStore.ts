import { create } from 'zustand';

type Theme = 'dark' | 'light';

interface ThemeStore {
  theme: Theme;
  toggle: () => void;
}

const saved = (localStorage.getItem('aicodehub-theme') as Theme) || 'dark';

export const useThemeStore = create<ThemeStore>((set) => ({
  theme: saved,
  toggle: () =>
    set((s) => {
      const next = s.theme === 'dark' ? 'light' : 'dark';
      localStorage.setItem('aicodehub-theme', next);
      document.documentElement.classList.toggle('dark', next === 'dark');
      return { theme: next };
    }),
}));

// Apply initial theme
if (typeof document !== 'undefined') {
  document.documentElement.classList.toggle('dark', saved === 'dark');
}
