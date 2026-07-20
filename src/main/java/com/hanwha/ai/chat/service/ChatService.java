package com.hanwha.ai.chat.service;

import com.hanwha.ai.chat.domain.ChatMessage;
import com.hanwha.ai.chat.dto.ChatRequest;
import com.hanwha.ai.chat.dto.ChatResponse;
import com.hanwha.ai.chat.dto.ChatConversationResponse;
import com.hanwha.ai.chat.dto.ChatMessageResponse;
import java.util.List;

public interface ChatService {
    ChatResponse chat(ChatRequest request);
    List<ChatConversationResponse> findConversations();
    List<ChatMessageResponse> findMessages(Long conversationId);
    ChatMessage findAttachment(Long messageId);
    void deleteConversation(Long conversationId);
}
