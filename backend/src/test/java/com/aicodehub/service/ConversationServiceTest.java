package com.aicodehub.service;

import com.aicodehub.entity.Conversation;
import com.aicodehub.entity.Message;
import com.aicodehub.mapper.ConversationMapper;
import com.aicodehub.mapper.MessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationServiceTest {

    private ConversationMapper convMapper;
    private MessageMapper msgMapper;
    private CacheConsistencyService cacheConsistency;
    private MessageCacheService messageCache;
    private ConversationService service;

    @BeforeEach
    void setUp() {
        convMapper = mock(ConversationMapper.class);
        msgMapper = mock(MessageMapper.class);
        cacheConsistency = mock(CacheConsistencyService.class);
        messageCache = mock(MessageCacheService.class);
        service = new ConversationService(convMapper, msgMapper, cacheConsistency, messageCache);
        when(messageCache.getRecent(anyLong())).thenReturn(List.of());
    }

    @Test
    void create_shouldGenerateSlugAndInsert() {
        Conversation conv = service.create(1L, "deepseek", "Hello world this is a test message");
        assertNotNull(conv.getSlug());
        assertEquals(8, conv.getSlug().length());
        verify(convMapper).insert(any(Conversation.class));
    }

    @Test
    void getContext_shouldTruncateToMaxContext() {
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Message m = new Message();
            m.setId((long) i);
            m.setContent("msg " + i);
            msgs.add(m);
        }
        when(msgMapper.selectList(any())).thenReturn(msgs);

        var result = service.getContext(1L);
        assertEquals(10, result.size());
        assertEquals("msg 20", result.get(0).getContent()); // last 10 start at index 20
        assertEquals("msg 29", result.get(9).getContent());
    }

    @Test
    void saveMessage_shouldTouchUpdatedAtAndEvictCache() {
        Conversation conv = new Conversation();
        conv.setId(5L);
        conv.setUserId(1L);
        when(convMapper.selectById(5L)).thenReturn(conv);

        service.saveMessage(5L, "user", "hello", 10, 20);

        verify(msgMapper).insert(any(Message.class));
        verify(convMapper).updateById(any(Conversation.class));
        verify(cacheConsistency).evict("conversations_user", 1L);
    }

    @Test
    void delete_shouldDeleteConvAndMessages() {
        Conversation conv = new Conversation();
        conv.setId(3L);
        conv.setUserId(1L);
        when(convMapper.selectById(3L)).thenReturn(conv);

        service.delete(3L, 1L);

        verify(convMapper).deleteById(3L);
        verify(msgMapper).delete(any());
    }

    @Test
    void delete_shouldNotDeleteIfWrongUser() {
        Conversation conv = new Conversation();
        conv.setId(3L);
        conv.setUserId(2L);
        when(convMapper.selectById(3L)).thenReturn(conv);

        service.delete(3L, 1L);

        verify(convMapper, never()).deleteById(anyLong());
        verify(msgMapper, never()).delete(any());
    }
}
