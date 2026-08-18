package com.hanwha.ai.mcp.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanwha.ai.global.exception.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentRouterTest {
    private final AgentRouter router = AgentRouterTestFixture.router();

    @Test
    void createsToolRouteWithoutExecutingMcp() {
        AgentRoute route = router.route("scan_source fileName=Test.java source=\"class Test {}\"");

        assertThat(route.kind()).isEqualTo(AgentRoute.Kind.TOOL_CALL);
        assertThat(route.target()).isEqualTo("scan_source");
        assertThat(route.operation()).startsWith("tools/call scan_source arguments=");
        assertThat(route.arguments()).containsExactlyEntriesOf(Map.of(
                "fileName", "Test.java",
                "source", "class Test {}"
        ));
    }

    @Test
    void routesNaturalLanguageJavaQuestionToSourceOntology() {
        String message = "AuthController와 관련된 Java소스들을 MCP에서 조회해줘";

        assertThat(router.supports(message)).isTrue();
        AgentRoute route = router.route(message);

        assertThat(route.kind()).isEqualTo(AgentRoute.Kind.TOOL_CALL);
        assertThat(route.target()).isEqualTo("search_source_ontology");
        assertThat(route.arguments()).containsExactlyEntriesOf(Map.of("query", "AuthController"));
    }

    @Test
    void routesLocalProjectAnalysisWithoutSelectingMcpTool() {
        AgentRoute route = router.route("로컬 D:\\workspace\\management 의 프로젝트 구조를 분석해줘");

        assertThat(route.kind()).isEqualTo(AgentRoute.Kind.PROJECT_STRUCTURE_ANALYSIS);
        assertThat(route.target()).isEqualTo("D:\\workspace\\management");
        assertThat(route.operation()).isEqualTo("project-structure/analyze D:\\workspace\\management");
    }

    @Test
    void preservesRequiredArgumentValidation() {
        assertThatThrownBy(() -> router.route("scan_file tool을 실행해줘"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("scan_file", "path");
    }
}