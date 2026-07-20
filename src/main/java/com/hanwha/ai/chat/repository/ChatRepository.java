package com.hanwha.ai.chat.repository;

import com.hanwha.ai.chat.domain.ChatConversation;
import com.hanwha.ai.chat.domain.ChatMessage;
import com.hanwha.ai.chat.mapper.ChatMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ChatRepository {
    private final ChatMapper mapper;

    public ChatRepository() {
        this.mapper = null;
    }

    @Autowired
    public ChatRepository(ChatMapper mapper) {
        this.mapper = mapper;
    }

    public ChatConversation createConversation(String title) {
        ChatConversation conversation = new ChatConversation();
        conversation.setTitle(title);
        if (mapper != null) {
            mapper.insertConversation(conversation);
            return mapper.findConversationById(conversation.getId());
        }
        return conversation;
    }

    public ChatConversation findConversationById(Long id) {
        return mapper == null || id == null ? null : mapper.findConversationById(id);
    }

    public List<ChatConversation> findConversations() {
        return mapper == null ? List.of() : mapper.findConversations();
    }

    public boolean deleteConversation(Long id) {
        return mapper != null && id != null && mapper.deleteConversation(id) > 0;
    }

    public void save(ChatMessage message) {
        if (mapper == null) {
            return;
        }
        mapper.insertMessage(message);
        mapper.touchConversation(message.getConversationId());
    }

    public List<ChatMessage> findMessages(Long conversationId) {
        return mapper == null ? List.of() : mapper.findMessages(conversationId);
    }

    public ChatMessage findMessageById(Long id) {
        return mapper == null || id == null ? null : mapper.findMessageById(id);
    }

    public List<ChatMessage> findRecentMessages(Long conversationId, int limit) {
        return mapper == null ? List.of() : mapper.findRecentMessages(conversationId, limit);
    }
}
