import { useState, useEffect, useCallback, useRef } from 'react';
import { useThemeStore } from '../store/themeStore';

interface LogEntry {
  timestamp: string;
  line: string;
  level: string;
}

interface Stats {
  total: number; errors: number; warns: number; infos: number; debugs: number;
  errorRate: number;
}

const LOG_LEVELS = ['ALL', 'ERROR', 'WARN', 'INFO', 'DEBUG'] as const;
const TIME_RANGES = [
  { label: '15m', value: 15 },
  { label: '1h', value: 60 },
  { label: '2h', value: 120 },
  { label: '3h', value: 180 },
];

const levelBadge = (lvl: string) => {
  switch (lvl) {
    case 'ERROR': return 'bg-red-500 text-white shadow-sm shadow-red-500/25';
    case 'WARN': return 'bg-amber-500 text-white shadow-sm shadow-amber-500/25';
    case 'INFO': return 'bg-blue-500 text-white shadow-sm shadow-blue-500/25';
    case 'DEBUG': return 'bg-slate-500 text-white shadow-sm shadow-slate-500/25';
    default: return 'bg-slate-600 text-white';
  }
};

const statCardStyle = (dark: boolean, color: string): string => {
  const m: Record<string, string> = {
    total: dark ? 'bg-slate-900/60 border-slate-700/30' : 'bg-white border-slate-200',
    error: dark ? 'bg-red-500/5 border-red-500/20' : 'bg-red-50/50 border-red-200',
    warn: dark ? 'bg-amber-500/5 border-amber-500/20' : 'bg-amber-50/50 border-amber-200',
    info: dark ? 'bg-blue-500/5 border-blue-500/20' : 'bg-blue-50/50 border-blue-200',
    debug: dark ? 'bg-slate-800/50 border-slate-700/30' : 'bg-slate-50 border-slate-200',
  };
  return m[color] || m.total;
};

function detectLevel(line: string): string {
  const upper = line.toUpperCase();
  if (upper.includes('ERROR') || upper.includes('EXCEPTION')) return 'ERROR';
  if (upper.includes('WARN')) return 'WARN';
  if (upper.includes('DEBUG')) return 'DEBUG';
  if (upper.includes('INFO')) return 'INFO';
  return 'INFO';
}

function parseLokiTimestamp(tsNs: string): string {
  const ms = parseInt(tsNs) / 1_000_000;
  return new Date(ms).toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
}

async function queryLoki(query: string, rangeMinutes: number, limit?: number) {
  const endNs = String(Date.now() * 1_000_000);
  const startNs = String(Date.now() * 1_000_000 - rangeMinutes * 60 * 1_000_000_000);
  const resolved = query.replace('__range__', `${rangeMinutes}m`);
  const endpoint = limit != null ? 'query_range' : 'query';
  let url = `/loki/loki/api/v1/${endpoint}?query=${encodeURIComponent(resolved)}&start=${startNs}&end=${endNs}`;
  if (limit != null) url += `&limit=${limit}&direction=backward`;

  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

function extractCount(json: any): number {
  const value = json?.data?.result?.[0]?.value;
  if (value && value.length >= 2) return parseInt(value[1]) || 0;
  return 0;
}

export default function LogViewer() {
  const dark = useThemeStore((s) => s.theme === 'dark');
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [keyword, setKeyword] = useState('');
  const [level, setLevel] = useState<string>('ALL');
  const [range, setRange] = useState(120);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [stats, setStats] = useState<Stats>({ total: 0, errors: 0, warns: 0, infos: 0, debugs: 0, errorRate: 0 });
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      // Fetch stats via Loki metric queries
      const [totalR, errR, warnR, infoR, debugR] = await Promise.all([
        queryLoki('sum(count_over_time({job="ai-codehub-backend"}[__range__]))', range).catch(() => null),
        queryLoki('sum(count_over_time({job="ai-codehub-backend"} |= "ERROR"[__range__]))', range).catch(() => null),
        queryLoki('sum(count_over_time({job="ai-codehub-backend"} |= "WARN"[__range__]))', range).catch(() => null),
        queryLoki('sum(count_over_time({job="ai-codehub-backend"} |= "INFO"[__range__]))', range).catch(() => null),
        queryLoki('sum(count_over_time({job="ai-codehub-backend"} |= "DEBUG"[__range__]))', range).catch(() => null),
      ]);

      const total = extractCount(totalR);
      const errors = extractCount(errR);
      const warns = extractCount(warnR);
      const infos = extractCount(infoR);
      const debugs = extractCount(debugR);
      setStats({ total, errors, warns, infos, debugs, errorRate: total > 0 ? ((errors / total) * 100) : 0 });

      // Fetch log lines
      let query = '{job="ai-codehub-backend"}';
      if (keyword.trim()) query += ` |= \`${keyword.trim()}\``;
      if (level !== 'ALL') query += ` |= "${level}"`;

      const json = await queryLoki(query, range, 300);
      const entries: LogEntry[] = [];
      for (const stream of json.data?.result ?? []) {
        for (const [ts, line] of stream.values ?? []) {
          entries.push({
            timestamp: parseLokiTimestamp(ts),
            line: String(line),
            level: detectLevel(String(line)),
          });
        }
      }
      entries.sort((a, b) => b.timestamp.localeCompare(a.timestamp));
      setLogs(entries);
    } catch (e: any) {
      setError('查询失败: ' + (e.message ?? '未知错误'));
    } finally {
      setLoading(false);
    }
  }, [keyword, level, range]);

  useEffect(() => { fetchData(); }, [fetchData]);

  useEffect(() => {
    if (autoRefresh) {
      timerRef.current = setInterval(fetchData, 5000);
    }
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [autoRefresh, fetchData]);

  const statCards = [
    { label: '总日志', value: stats.total, color: 'total', fmt: (n: number) => n.toLocaleString() },
    { label: 'ERROR', value: stats.errors, color: 'error', fmt: (n: number) => n.toLocaleString() },
    { label: 'WARN', value: stats.warns, color: 'warn', fmt: (n: number) => n.toLocaleString() },
    { label: 'INFO', value: stats.infos, color: 'info', fmt: (n: number) => n.toLocaleString() },
    { label: 'DEBUG', value: stats.debugs, color: 'debug', fmt: (n: number) => n.toLocaleString() },
    { label: '错误率', value: stats.errorRate, color: stats.errorRate > 5 ? 'error' : 'total', fmt: (n: number) => n.toFixed(2) + '%' },
  ];

  const maxBar = Math.max(1, stats.errors, stats.warns, stats.infos, stats.debugs, stats.total * 0.1);
  const barConf = [
    { level: 'ERROR', count: stats.errors, color: dark ? 'bg-red-500/60' : 'bg-red-500' },
    { level: 'WARN', count: stats.warns, color: dark ? 'bg-amber-500/60' : 'bg-amber-500' },
    { level: 'INFO', count: stats.infos, color: dark ? 'bg-blue-500/60' : 'bg-blue-500' },
    { level: 'DEBUG', count: stats.debugs, color: dark ? 'bg-slate-500/60' : 'bg-slate-400' },
  ];

  const textColor = (lvl: string) => {
    switch (lvl) {
      case 'ERROR': return dark ? 'text-red-400' : 'text-red-600';
      case 'WARN': return dark ? 'text-amber-400' : 'text-amber-600';
      case 'DEBUG': return dark ? 'text-slate-500' : 'text-slate-400';
      default: return dark ? 'text-slate-400' : 'text-slate-600';
    }
  };

  const levelBg = (lvl: string) => {
    switch (lvl) {
      case 'ERROR': return dark ? 'bg-red-500/10' : 'bg-red-50';
      case 'WARN': return dark ? 'bg-amber-500/10' : 'bg-amber-50';
      default: return '';
    }
  };

  return (
    <div className={`flex flex-col h-screen ${dark ? 'bg-[#0b0f14]' : 'bg-slate-50'}`}>
      <div className="fixed inset-0 -z-10 pointer-events-none">
        <div className={`absolute inset-0 ${dark ? 'bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-900 via-[#0b0f14] to-[#0b0f14]' : 'bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-50 via-white to-white'}`} />
      </div>

      {/* Header */}
      <header className={`shrink-0 border-b ${dark ? 'border-slate-800/40 bg-[#0b0f14]/70' : 'border-slate-200/60 bg-white/70'} backdrop-blur-2xl z-10`}>
        <div className="flex items-center justify-between px-6 h-16">
          <div className="flex items-center gap-4">
            <a href="/admin" className={`text-sm font-medium ${dark ? 'text-slate-400 hover:text-white' : 'text-slate-500 hover:text-slate-700'}`}>← 返回</a>
            <span className={`font-semibold text-base ${dark ? 'text-white' : 'text-slate-900'}`}>日志监控</span>
            <span className={`text-xs ${dark ? 'text-slate-500' : 'text-slate-400'}`}>Loki · Grafana</span>
          </div>
          <div className="flex items-center gap-3">
            <a href="http://localhost:3100" target="_blank" rel="noopener"
               className={`text-xs font-medium px-3 py-1.5 rounded-lg border transition-colors ${dark ? 'border-slate-700 text-slate-400 hover:text-white hover:border-slate-600' : 'border-slate-200 text-slate-500 hover:text-slate-700'}`}>
              Grafana →
            </a>
            {error && <span className="text-xs text-red-400">{error}</span>}

            {/* Time range selector */}
            <div className={`flex rounded-lg p-0.5 gap-0.5 ${dark ? 'bg-slate-800' : 'bg-slate-100'}`}>
              {TIME_RANGES.map(t => (
                <button key={t.label} onClick={() => setRange(t.value)}
                  className={`px-2.5 py-1 text-xs font-medium rounded-md transition-all ${
                    range === t.value
                      ? (dark ? 'bg-slate-700 text-white' : 'bg-white text-slate-700 shadow-sm')
                      : (dark ? 'text-slate-500 hover:text-slate-300' : 'text-slate-400 hover:text-slate-600')
                  }`}>
                  {t.label}
                </button>
              ))}
            </div>

            {/* Level filter */}
            <div className={`flex rounded-lg p-0.5 gap-0.5 ${dark ? 'bg-slate-800/80' : 'bg-slate-200/60'}`}>
              {LOG_LEVELS.map(l => (
                <button key={l} onClick={() => setLevel(l)}
                  className={`px-3 py-1.5 text-xs font-semibold rounded-md transition-all duration-200 ${
                    level === l
                      ? levelBadge(l)
                      : dark
                        ? 'text-slate-400 hover:text-slate-200 hover:bg-slate-700/50'
                        : 'text-slate-500 hover:text-slate-700 hover:bg-white/60'
                  }`}>
                  {l}
                </button>
              ))}
            </div>

            <input value={keyword} onChange={e => setKeyword(e.target.value)} placeholder="搜索关键词..."
                   className={`px-3 py-1.5 rounded-lg border text-sm w-40 ${dark ? 'bg-slate-800 border-slate-700 text-slate-200 placeholder-slate-500' : 'bg-slate-50 border-slate-200 placeholder-slate-400'}`} />
            <label className="flex items-center gap-1.5 text-xs text-slate-500 cursor-pointer select-none">
              <input type="checkbox" checked={autoRefresh} onChange={e => setAutoRefresh(e.target.checked)} className="rounded" />
              自动
            </label>
            <button onClick={fetchData} disabled={loading}
                    className="px-3 py-1.5 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors disabled:opacity-50">
              {loading ? '...' : '刷新'}
            </button>
          </div>
        </div>
      </header>

      <div className="flex-1 overflow-auto p-4 space-y-4">
        {/* Stats Cards */}
        <div className="grid grid-cols-6 gap-3">
          {statCards.map(s => (
            <div key={s.label}
              className={`rounded-xl border px-4 py-3 ${statCardStyle(dark, s.color)}`}>
              <p className={`text-[11px] uppercase tracking-wide ${dark ? 'text-slate-500' : 'text-slate-400'} mb-1`}>{s.label}</p>
              <p className={`text-xl font-bold ${s.color === 'error' && s.value > 0 ? 'text-red-400' : dark ? 'text-white' : 'text-slate-900'}`}>
                {s.fmt(s.value)}
              </p>
            </div>
          ))}
        </div>

        {/* Distribution Bar Chart */}
        <div className={`rounded-xl border px-4 py-3 ${dark ? 'bg-slate-950/50 border-slate-800' : 'bg-white border-slate-200'}`}>
          <p className={`text-[11px] uppercase tracking-wide ${dark ? 'text-slate-500' : 'text-slate-400'} mb-3`}>日志级别分布</p>
          <div className="space-y-2">
            {barConf.map(b => (
              <div key={b.level} className="flex items-center gap-3">
                <span className={`text-xs font-medium w-12 ${textColor(b.level)}`}>{b.level}</span>
                <div className="flex-1 h-5 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
                  <div className={`h-full rounded-full transition-all duration-500 ${b.color}`}
                       style={{ width: `${Math.max(2, (b.count / maxBar) * 100)}%` }} />
                </div>
                <span className={`text-xs w-12 text-right ${dark ? 'text-slate-400' : 'text-slate-500'}`}>{b.count.toLocaleString()}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Log Stream */}
        <div className={`rounded-xl border overflow-hidden ${dark ? 'bg-slate-950 border-slate-800' : 'bg-white border-slate-200'}`}>
          <div className={`px-4 py-2 border-b ${dark ? 'border-slate-800' : 'border-slate-100'} flex items-center justify-between`}>
            <span className={`text-xs font-medium ${dark ? 'text-slate-400' : 'text-slate-500'}`}>日志流 · {logs.length} 条</span>
            {loading && <span className="text-xs text-slate-500 animate-pulse">刷新中...</span>}
          </div>
          {logs.length === 0 ? (
            <div className={`p-8 text-center text-sm ${dark ? 'text-slate-500' : 'text-slate-400'}`}>
              {loading ? '查询中...' : '暂无日志'}
            </div>
          ) : (
            <div className={`divide-y ${dark ? 'divide-slate-800/50' : 'divide-slate-100'}`}>
              {logs.map((entry, i) => (
                <div key={i} className={`flex gap-3 px-4 py-1.5 text-[12px] leading-5 font-mono ${levelBg(entry.level)} hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors`}>
                  <span className={`shrink-0 select-none ${dark ? 'text-slate-600' : 'text-slate-400'} w-[130px]`}>{entry.timestamp}</span>
                  <span className={`shrink-0 w-12 text-center text-[11px] font-semibold ${textColor(entry.level)}`}>{entry.level}</span>
                  <span className={`truncate ${textColor(entry.level)}`}>{entry.line}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
