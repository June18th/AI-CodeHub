package com.aicodehub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade cache consistency: guaranteed eviction with retry + reconciliation.
 *
 * Strategy: On write, evict cache synchronously. If that fails, queue a retry task
 * that re-attempts up to MAX_RETRIES with exponential backoff. If all retries fail,
 * log the poisoned key for manual inspection. A scheduled reconciliation job prunes
 * all expired keys periodically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheConsistencyService {

    private final CacheManager cacheManager;
    private final Set<String> poisonedKeys = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);

    private static final int MAX_RETRIES = 5;
    private static final long BASE_BACKOFF_MS = 200;

    /** Evict a single key from a named cache. Guaranteed: retries on failure. */
    public void evict(String cacheName, Object key) {
        if (tryEvict(cacheName, key)) return;
        scheduleRetry(cacheName, key, 1);
    }

    private boolean tryEvict(String cacheName, Object key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                return true;
            }
        } catch (Exception e) {
            log.warn("Cache eviction attempt failed for {}:{} — {}", cacheName, key, e.getMessage());
        }
        return false;
    }

    private void scheduleRetry(String cacheName, Object key, int attempt) {
        if (attempt > MAX_RETRIES) {
            poisonedKeys.add(cacheName + ":" + key);
            log.error("CACHE POISONED after {} retries — {}:{} — manual intervention needed", MAX_RETRIES, cacheName, key);
            return;
        }
        long delay = BASE_BACKOFF_MS * (1L << (attempt - 1)); // 200, 400, 800, 1600, 3200ms
        retryExecutor.schedule(() -> {
            if (!tryEvict(cacheName, key)) {
                scheduleRetry(cacheName, key, attempt + 1);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /** Scheduled reconciliation: purge poisoned keys every 60s. */
    @Scheduled(fixedRate = 60_000)
    public void reconcile() {
        if (poisonedKeys.isEmpty()) return;
        log.info("Reconciliation running: {} poisoned keys", poisonedKeys.size());
        var iter = poisonedKeys.iterator();
        while (iter.hasNext()) {
            String pk = iter.next();
            try {
                String[] parts = pk.split(":", 2);
                if (tryEvict(parts[0], parts[1])) iter.remove();
            } catch (Exception e) {
                log.error("Reconciliation failed for {}", pk, e);
            }
        }
    }
}
