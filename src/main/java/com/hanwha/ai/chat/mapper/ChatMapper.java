package com.hanwha.ai.chat.mapper;

import com.hanwha.ai.chat.domain.ChatConversation;
import com.hanwha.ai.chat.domain.ChatMessage;
import com.hanwha.ai.chat.domain.ChatProject;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMapper {
    void insertConversation(ChatConversation conversation);
    ChatConversation findConversationById(@Param("id") Long id);
    List<ChatConversation> findConversations();
    int updateConversationProject(
            @Param("conversationId") Long conversationId,
            @Param("projectId") Long projectId
    );
    int deleteConversation(@Param("id") Long id);
    void insertProject(ChatProject project);
    ChatProject findProjectById(@Param("id") Long id);
    List<ChatProject> findProjects();
    int updateProjectName(@Param("id") Long id, @Param("name") String name);
    void insertMessage(ChatMessage message);
    ChatMessage findMessageById(@Param("id") Long id);
    List<ChatMessage> findMessages(@Param("conversationId") Long conversationId);
    List<ChatMessage> findRecentMessages(
            @Param("conversationId") Long conversationId,
            @Param("limit") int limit
    );
    void touchConversation(@Param("id") Long id);
}
