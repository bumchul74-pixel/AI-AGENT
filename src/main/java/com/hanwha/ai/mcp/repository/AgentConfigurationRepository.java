package com.hanwha.ai.mcp.repository;

import com.hanwha.ai.mcp.config.AgentConfigurationVersion;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AgentConfigurationRepository {
    Optional<AgentConfigurationVersion> findActive();

    Optional<AgentConfigurationVersion> findByVersionKey(String versionKey);

    void insert(AgentConfigurationVersion version);

    void archiveActive();

    void activate(String versionKey, LocalDateTime activatedAt);
}