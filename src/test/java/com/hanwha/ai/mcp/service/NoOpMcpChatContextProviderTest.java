package com.hanwha.ai.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoOpMcpChatContextProviderTest {
    @Test
    void ignoresProjectStructureRequestWhenMcpClientIsDisabled() {
        NoOpMcpChatContextProvider provider = new NoOpMcpChatContextProvider();
        String message = "\ub85c\uceec D:\\workspace\\management \uc758 \ud504\ub85c\uc81d\ud2b8 \uad6c\uc870\ub97c \ubd84\uc11d\ud574\uc918";

        assertThat(provider.supports(message)).isFalse();
        assertThat(provider.resolveContext(message)).isEqualTo(List.of());
    }

    @Test
    void ignoresGeneralChatMessagesWhenMcpClientIsDisabled() {
        NoOpMcpChatContextProvider provider = new NoOpMcpChatContextProvider();

        assertThat(provider.supports("How should I create a controller?")).isFalse();
    }
}