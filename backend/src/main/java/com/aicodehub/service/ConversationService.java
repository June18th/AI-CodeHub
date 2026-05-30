package com.aicodehub.service;

import com.aicodehub.entity.Conversation;
import com.aicodehub.entity.Message;
import com.aicodehub.mapper.ConversationMapper;
import com.aicodehub.mapper.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    private static final int MAX_CONTEXT = 20;

    public Conversation create(Long userId, String model, String firstPrompt) {
        Conversation c = new Conversation();
        c.setUserId(userId);
        c.setModel(model);
        String cleaned = firstPrompt.replaceAll("\\s+", " ").trim();
        String title = cleaned.length() > 40 ? cleaned.substring(0, 40) + "..." : cleaned;
        c.setTitle(title);
        conversationMapper.insert(c);
        return c;
    }

    public List<Conversation> listByUser(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
            .eq(Conversation::getUserId, userId)
            .orderByDesc(Conversation::getUpdatedAt));
    }

    public void delete(Long id, Long userId) {
        Conversation c = conversationMapper.selectById(id);
        if (c != null && c.getUserId().equals(userId)) {
            conversationMapper.deleteById(id);
            messageMapper.delete(new LambdaQueryWrapper<Message>().eq(Message::getConversationId, id));
        }
    }

    public void saveMessage(Long conversationId, String role, String content) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        messageMapper.insert(m);
    }

    /**
     * Get the last N messages as context for the LLM call.
     * Returns at most MAX_CONTEXT messages, oldest first.
     */
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

    public void updateTitle(Long id, String title) {
        Conversation c = conversationMapper.selectById(id);
        if (c != null) {
            c.setTitle(title);
            conversationMapper.updateById(c);
        }
    }
}
