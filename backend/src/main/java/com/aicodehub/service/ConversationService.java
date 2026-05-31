package com.aicodehub.service;

import com.aicodehub.entity.Conversation;
import com.aicodehub.entity.Message;
import com.aicodehub.mapper.ConversationMapper;
import com.aicodehub.mapper.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final CacheConsistencyService cacheConsistency;

    private static final int MAX_CONTEXT = 20;

    private void evictConversationList(Long conversationId) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c != null) {
            cacheConsistency.evict("conversations_user", c.getUserId());
        }
    }

    @Cacheable(value = "conversation", key = "#id")
    public Conversation getById(Long id) {
        return conversationMapper.selectById(id);
    }

    @CacheEvict(value = {"conversations_user", "conversation"}, key = "#userId")
    public Conversation create(Long userId, String model, String firstPrompt) {
        Conversation c = new Conversation();
        c.setUserId(userId);
        c.setModel(model);
        String cleaned = firstPrompt
            .replaceAll("[#*>_`~|]", "")
            .replaceAll("\\s+", " ")
            .trim();
        String title = cleaned.length() > 40 ? cleaned.substring(0, 40) + "..." : cleaned;
        c.setTitle(title);
        c.setSlug(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        conversationMapper.insert(c);
        return c;
    }

    public Conversation getBySlug(String slug) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>().eq(Conversation::getSlug, slug));
    }

    @Cacheable(value = "conversations_user", key = "#userId")
    public List<Conversation> listByUser(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
            .eq(Conversation::getUserId, userId)
            .orderByDesc(Conversation::getUpdatedAt));
    }

    @CacheEvict(value = {"conversations_user", "conversation", "messages_ctx"}, key = "#userId")
    public void delete(Long id, Long userId) {
        Conversation c = conversationMapper.selectById(id);
        if (c != null && c.getUserId().equals(userId)) {
            conversationMapper.deleteById(id);
            messageMapper.delete(new LambdaQueryWrapper<Message>().eq(Message::getConversationId, id));
        }
    }

    public void saveMessage(Long conversationId, String role, String content) {
        saveMessage(conversationId, role, content, null, null);
    }

    @CacheEvict(value = "messages_ctx", key = "#conversationId")
    public void saveMessage(Long conversationId, String role, String content, Integer inputTokens, Integer outputTokens) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        m.setInputTokens(inputTokens);
        m.setOutputTokens(outputTokens);
        messageMapper.insert(m);
        // Touch conversation's updated_at to reflect latest activity
        Conversation c = conversationMapper.selectById(conversationId);
        if (c != null) {
            conversationMapper.updateById(c);
        }
        evictConversationList(conversationId);
    }

    @Cacheable(value = "messages_ctx", key = "#conversationId")
    public List<Message> getContext(Long conversationId) {
        List<Message> all = messageMapper.selectList(new LambdaQueryWrapper<Message>()
            .eq(Message::getConversationId, conversationId)
            .orderByAsc(Message::getCreatedAt));
        int size = all.size();
        if (size > MAX_CONTEXT) {
            return all.subList(size - MAX_CONTEXT, size);
        }
        return all;
    }

    public List<Message> getMessages(Long conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
            .eq(Message::getConversationId, conversationId)
            .orderByAsc(Message::getCreatedAt));
    }

    @CacheEvict(value = "conversation", key = "#id")
    public void updateTitle(Long id, String title) {
        Conversation c = conversationMapper.selectById(id);
        if (c != null) {
            c.setTitle(title);
            conversationMapper.updateById(c);
        }
    }
}
