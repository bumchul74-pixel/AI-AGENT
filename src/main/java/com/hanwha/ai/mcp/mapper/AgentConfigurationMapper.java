package com.hanwha.ai.mcp.mapper;

import com.hanwha.ai.mcp.config.AgentConfigurationVersion;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentConfigurationMapper {
    AgentConfigurationVersion findActive();

    AgentConfigurationVersion findByVersionKey(@Param("versionKey") String versionKey);

    void insert(AgentConfigurationVersion version);

    int archiveActive();

    int activate(
            @Param("versionKey") String versionKey,
            @Param("activatedAt") LocalDateTime activatedAt
    );
}