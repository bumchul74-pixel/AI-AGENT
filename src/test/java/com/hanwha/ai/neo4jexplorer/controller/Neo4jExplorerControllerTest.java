package com.hanwha.ai.neo4jexplorer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodeDetailResponse;
import com.hanwha.ai.neo4jexplorer.service.Neo4jExplorerService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class Neo4jExplorerControllerTest {
    private final Neo4jExplorerService service = mock(Neo4jExplorerService.class);
    private final Neo4jExplorerController controller = new Neo4jExplorerController(service);

    @Test
    void returnsNotFoundWithoutThrowingThroughGlobalExceptionHandler() {
        when(service.findDetail("missing")).thenReturn(Optional.empty());
        ResponseEntity<Neo4jNodeDetailResponse> response = controller.detail("missing");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void returnsNodeDetail() {
        Neo4jNodeDetailResponse detail = new Neo4jNodeDetailResponse(
                "4:abc:1", List.of("Method"), "findAll", Map.of("name", "findAll"), 0, List.of());
        when(service.findDetail("4:abc:1")).thenReturn(Optional.of(detail));
        ResponseEntity<Neo4jNodeDetailResponse> response = controller.detail("4:abc:1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(detail);
    }
}