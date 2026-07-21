package com.hanwha.ai.chat.controller;

import com.hanwha.ai.chat.domain.ChatMessage;
import com.hanwha.ai.chat.dto.ChatRequest;
import com.hanwha.ai.chat.dto.ChatResponse;
import com.hanwha.ai.chat.dto.ChatConversationResponse;
import com.hanwha.ai.chat.dto.ChatMessageResponse;
import com.hanwha.ai.chat.dto.ChatConversationProjectRequest;
import com.hanwha.ai.chat.dto.ChatProjectRequest;
import com.hanwha.ai.chat.dto.ChatProjectResponse;
import com.hanwha.ai.chat.service.ChatService;
import com.hanwha.ai.chat.service.SecureCodingChatService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final SecureCodingChatService secureCodingChatService;

    public ChatController(ChatService chatService, SecureCodingChatService secureCodingChatService) {
        this.chatService = chatService;
        this.secureCodingChatService = secureCodingChatService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatResponse secureCodingChat(
            @RequestParam("message") String message,
            @RequestParam(value = "conversationId", required = false) Long conversationId,
            @RequestPart("file") MultipartFile file
    ) {
        return secureCodingChatService.scan(message, conversationId, file);
    }

    @GetMapping("/messages/{messageId}/attachment")
    public ResponseEntity<byte[]> attachment(@PathVariable Long messageId) {
        ChatMessage message = chatService.findAttachment(messageId);
        String disposition = ContentDisposition.attachment()
                .filename(message.getAttachmentName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/x-java-source"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(message.getAttachmentContent());
    }

    @GetMapping("/conversations")
    public List<ChatConversationResponse> conversations() {
        return chatService.findConversations();
    }

    @GetMapping("/projects")
    public List<ChatProjectResponse> projects() {
        return chatService.findProjects();
    }

    @PostMapping("/projects")
    public ChatProjectResponse createProject(@RequestBody ChatProjectRequest request) {
        return chatService.createProject(request.name());
    }

    @PatchMapping("/projects/{projectId}")
    public ChatProjectResponse renameProject(
            @PathVariable Long projectId,
            @RequestBody ChatProjectRequest request
    ) {
        return chatService.renameProject(projectId, request.name());
    }

    @PatchMapping("/conversations/{conversationId}/project")
    public void moveConversation(
            @PathVariable Long conversationId,
            @RequestBody ChatConversationProjectRequest request
    ) {
        chatService.moveConversation(conversationId, request.projectId());
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessageResponse> messages(@PathVariable Long conversationId) {
        return chatService.findMessages(conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public void deleteConversation(@PathVariable Long conversationId) {
        chatService.deleteConversation(conversationId);
    }
}
