package com.hanwha.ai.chat.repository;

import com.hanwha.ai.chat.domain.ChatMessage;
import org.springframework.stereotype.Repository;

@Repository
public class ChatRepository {
    public void save(ChatMessage message) {
        // MyBatis mapper integration will be connected when chat history schema is defined.
    }
}
