package com.hanwha.ai.chat.mapper;

import com.hanwha.ai.chat.domain.ChatMessage;

public interface ChatMapper {
    void save(ChatMessage message);
}
