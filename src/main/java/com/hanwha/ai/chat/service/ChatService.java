package com.hanwha.ai.chat.service;

import com.hanwha.ai.chat.dto.ChatRequest;
import com.hanwha.ai.chat.dto.ChatResponse;

public interface ChatService {
    ChatResponse chat(ChatRequest request);
}
