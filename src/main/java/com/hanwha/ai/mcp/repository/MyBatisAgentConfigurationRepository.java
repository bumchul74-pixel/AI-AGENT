package com.hanwha.ai.mcp.repository;

import com.hanwha.ai.mcp.config.AgentConfigurationVersion;
import com.hanwha.ai.mcp.mapper.AgentConfigurationMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAgentConfigurationRepository implements AgentConfigurationRepository {
    private final AgentConfigurationMapper mapper;

    public MyBatisAgentConfigurationRepository(AgentConfigurationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AgentConfigurationVersion> findActive() {
        return Optional.ofNullable(mapper.findActive());
    }

    @Override
    public Optional<AgentConfigurationVersion> findByVersionKey(String versionKey) {
        return Optional.ofNullable(mapper.findByVersionKey(versionKey));
    }

    @Override
    public void insert(AgentConfigurationVersion version) {
        mapper.insert(version);
    }

    @Override
    public void archiveActive() {
        mapper.archiveActive();
    }

    @Override
    public void activate(String versionKey, LocalDateTime activatedAt) {
        if (mapper.activate(versionKey, activatedAt) != 1) {
            throw new IllegalStateException("Agent configuration version was not activated: " + versionKey);
        }
    }
}