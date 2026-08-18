package com.hanwha.ai.neo4jexplorer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodeDetailResponse;
import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodePageResponse;
import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodeSummary;
import com.hanwha.ai.neo4jexplorer.repository.Neo4jExplorerRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class Neo4jExplorerServiceTest {
    private final Neo4jExplorerRepository repository = mock(Neo4jExplorerRepository.class);
    private final Neo4jExplorerService service = new Neo4jExplorerService(repository);

    @Test
    void returnsZeroBasedPageMetadataAndNormalizesFilters() {
        Neo4jNodeSummary node = new Neo4jNodeSummary("4:abc:1", List.of("Method"), "findAll", 7, 3);
        when(repository.count("Method", "find")).thenReturn(121L);
        when(repository.findPage("Method", "find", 100, 100)).thenReturn(List.of(node));

        Neo4jNodePageResponse result = service.findNodes(" Method ", " FIND ", 1, 200);

        assertThat(result.content()).containsExactly(node);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalElements()).isEqualTo(121);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).isTrue();
        verify(repository).findPage("Method", "find", 100, 100);
    }

    @Test
    void usesDefaultsAndDoesNotQueryContentPastLastPage() {
        when(repository.count("", "")).thenReturn(1L);
        Neo4jNodePageResponse result = service.findNodes(null, null, 3, 0);
        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(3);
        assertThat(result.size()).isEqualTo(30);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.last()).isTrue();
    }

    @Test
    void trimsDetailIdAndRejectsBlankId() {
        Neo4jNodeDetailResponse detail = new Neo4jNodeDetailResponse(
                "4:abc:1", List.of("Method"), "findAll", Map.of("name", "findAll"), 0, List.of());
        when(repository.findDetail("4:abc:1")).thenReturn(Optional.of(detail));
        assertThat(service.findDetail(" 4:abc:1 ")).contains(detail);
        assertThat(service.findDetail("  ")).isEmpty();
    }
}