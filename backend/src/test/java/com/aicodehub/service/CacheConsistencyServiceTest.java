package com.aicodehub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class CacheConsistencyServiceTest {

    private CacheManager cacheManager;
    private Cache cache;
    private CacheConsistencyService service;

    @BeforeEach
    void setUp() {
        cacheManager = mock(CacheManager.class);
        cache = mock(Cache.class);
        service = new CacheConsistencyService(cacheManager);
    }

    @Test
    void evict_shouldCallCacheEvict() {
        when(cacheManager.getCache("testCache")).thenReturn(cache);
        service.evict("testCache", "key1");
        verify(cache).evict("key1");
    }

    @Test
    void evict_shouldNotThrowWhenCacheNotFound() {
        when(cacheManager.getCache("missing")).thenReturn(null);
        assertDoesNotThrow(() -> service.evict("missing", "key1"));
    }

    @Test
    void evict_shouldNotThrowWhenRedisDown() {
        when(cacheManager.getCache("testCache")).thenThrow(new RuntimeException("Redis down"));
        assertDoesNotThrow(() -> service.evict("testCache", "key1"));
    }
}
