package com.hanwha.ai.neo4jexplorer.repository;

import com.hanwha.ai.neo4jexplorer.dto.Neo4jLabelGraphResponse;
import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodeDetailResponse;
import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodeSummary;
import java.util.List;
import java.util.Optional;

public interface Neo4jExplorerRepository {
    long count(String label, String keyword);
    List<Neo4jNodeSummary> findPage(String label, String keyword, int offset, int size);
    Optional<Neo4jNodeDetailResponse> findDetail(String elementId);
    Neo4jLabelGraphResponse findLabelGraph(int labelLimit, int relationshipLimit);
}