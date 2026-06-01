package com.aicodehub.service;

import com.aicodehub.entity.OrgTag;
import com.aicodehub.mapper.OrgTagMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrgTagService {

    private final OrgTagMapper orgTagMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    private static final String CACHE_KEY = "orgtag:all";
    private static final Duration TTL = Duration.ofHours(24);
    private volatile Map<Long, OrgTag> tagCache = Collections.emptyMap();

    @PostConstruct
    public void init() {
        refresh();
    }

    public synchronized void refresh() {
        List<OrgTag> all = orgTagMapper.selectList(null);
        Map<Long, OrgTag> map = new LinkedHashMap<>();
        for (OrgTag t : all) map.put(t.getId(), t);
        tagCache = Collections.unmodifiableMap(map);
        try {
            redis.opsForValue().set(CACHE_KEY, json.writeValueAsString(all), TTL);
        } catch (JsonProcessingException ignored) {}
        log.info("OrgTag cache refreshed: {} tags", all.size());
    }

    /** Resolve comma-separated tag names to IDs */
    public List<Long> resolveTagIds(String orgTagsStr) {
        if (orgTagsStr == null || orgTagsStr.isBlank()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String name : orgTagsStr.split(",")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            for (OrgTag t : tagCache.values()) {
                if (trimmed.equals(t.getName())) { ids.add(t.getId()); break; }
            }
        }
        return ids;
    }

    /** Recursively collect parent tags to get effective org tags */
    public Set<Long> getEffectiveTagIds(String orgTagsStr) {
        Set<Long> effective = new LinkedHashSet<>();
        if (orgTagsStr == null || orgTagsStr.isBlank()) return effective;
        for (String name : orgTagsStr.split(",")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            OrgTag tag = findByName(trimmed);
            if (tag != null) collectParents(tag.getId(), effective);
        }
        return effective;
    }

    /** Get effective tag names (for display and ES terms filter) */
    public Set<String> getEffectiveTagNames(String orgTagsStr) {
        Set<String> names = new LinkedHashSet<>();
        for (Long id : getEffectiveTagIds(orgTagsStr)) {
            OrgTag t = tagCache.get(id);
            if (t != null) names.add(t.getName());
        }
        return names;
    }

    public OrgTag findByName(String name) {
        for (OrgTag t : tagCache.values()) {
            if (t.getName().equals(name)) return t;
        }
        return null;
    }

    public OrgTag findById(Long id) {
        return tagCache.get(id);
    }

    private void collectParents(Long tagId, Set<Long> result) {
        if (tagId == null || !result.add(tagId)) return;
        OrgTag tag = tagCache.get(tagId);
        if (tag != null && tag.getParentId() != null) {
            collectParents(tag.getParentId(), result);
        }
    }
}
