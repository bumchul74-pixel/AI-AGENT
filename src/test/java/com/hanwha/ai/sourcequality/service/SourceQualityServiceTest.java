package com.hanwha.ai.sourcequality.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.sourcequality.domain.SourceQualityMethod;
import com.hanwha.ai.sourcequality.domain.SourceQualityMethodDetail;
import com.hanwha.ai.sourcequality.domain.SourceQualitySnapshot;
import com.hanwha.ai.sourcequality.dto.SourceQualityDashboardResponse;
import com.hanwha.ai.sourcequality.mapper.SourceQualityMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SourceQualityServiceTest {
    private final SourceQualityGraphReader graphReader = mock(SourceQualityGraphReader.class);
    private final SourceQualityMapper mapper = mock(SourceQualityMapper.class);
    private final RagDocumentRepository documentRepository = mock(RagDocumentRepository.class);
    private final SourceQualityService service = new SourceQualityService(graphReader, mapper, documentRepository);

    @BeforeEach
    void setUp() {
        when(documentRepository.projectExists("commerce")).thenReturn(true);
        when(mapper.findSnapshots("commerce", 30)).thenReturn(List.of());
    }

    @Test
    void evaluatesDuplicateGroupsComplexityAndPersistsChangedSnapshot() {
        when(graphReader.findMethods("commerce")).thenReturn(List.of(
                method("m1", "exact", "structure", 12, 12, 8),
                method("m2", "exact", "structure", 10, 2, 3),
                method("m3", "unique", "unique-structure", 7, 1, 1)));

        SourceQualityDashboardResponse response = service.evaluate("commerce");

        assertThat(response.summary().totalMethodCount()).isEqualTo(3);
        assertThat(response.summary().duplicateMethodCount()).isEqualTo(2);
        assertThat(response.summary().duplicateGroupCount()).isEqualTo(2);
        assertThat(response.summary().duplicateRatio()).isEqualTo(66.67);
        assertThat(response.summary().highComplexityCount()).isEqualTo(1);
        assertThat(response.duplicateGroups()).extracting("type").containsExactly("EXACT", "STRUCTURAL");
        assertThat(response.highComplexityMethods()).extracting("methodUid").containsExactly("m1");
        assertThat(response.gate().status()).isEqualTo("FAIL");
        verify(mapper).insertSnapshot(any(SourceQualitySnapshot.class));
    }

    @Test
    void doesNotPersistSnapshotWhenSummaryDidNotChange() {
        SourceQualityMethod method = method("m1", "exact", "structure", 7, 1, 1);
        when(graphReader.findMethods("commerce")).thenReturn(List.of(method));
        SourceQualitySnapshot latest = new SourceQualitySnapshot();
        latest.setTotalMethodCount(1);
        latest.setDuplicateMethodCount(0);
        latest.setDuplicateGroupCount(0);
        latest.setDuplicateRatio(0);
        latest.setHighComplexityCount(0);
        latest.setMaxCyclomaticComplexity(1);
        latest.setMaxCognitiveComplexity(1);
        when(mapper.findLatestSnapshot("commerce")).thenReturn(latest);

        SourceQualityDashboardResponse response = service.evaluate("commerce");

        assertThat(response.gate().status()).isEqualTo("PASS");
        verify(mapper, never()).insertSnapshot(any());
    }

    @Test
    void returnsTheSelectedComplexMethodBody() {
        SourceQualityMethod method = method("m1", "exact", "structure", 12, 12, 25);
        when(graphReader.findMethodDetail("commerce", "m1")).thenReturn(Optional.of(
                new SourceQualityMethodDetail(method, "{ return invocation.proceed(); }")));

        var response = service.getMethodDetail("commerce", "m1");

        assertThat(response.methodUid()).isEqualTo("m1");
        assertThat(response.cyclomaticComplexity()).isEqualTo(12);
        assertThat(response.cognitiveComplexity()).isEqualTo(25);
        assertThat(response.methodBody()).contains("invocation.proceed");
    }

    @Test
    void returnsMethodBodiesOnlyForTheSelectedDuplicateGroup() {
        SourceQualityMethod first = method("m1", "exact", "structure", 7, 1, 1);
        SourceQualityMethod second = method("m2", "exact", "structure", 8, 2, 2);
        when(graphReader.findDuplicateMethods("commerce", "EXACT", "exact")).thenReturn(List.of(
                new SourceQualityMethodDetail(first, "{ return first(); }"),
                new SourceQualityMethodDetail(second, "{ return second(); }")));

        var response = service.getDuplicateGroup("commerce", "exact", "exact");

        assertThat(response.type()).isEqualTo("EXACT");
        assertThat(response.methodCount()).isEqualTo(2);
        assertThat(response.methods()).extracting("methodBody")
                .containsExactly("{ return first(); }", "{ return second(); }");
    }

    private SourceQualityMethod method(String uid, String hash, String structuralHash,
                                       int lines, int cyclomatic, int cognitive) {
        return new SourceQualityMethod(uid, "com.example.Sample", uid + "()",
                "src/Sample.java", 1, lines, lines, hash, structuralHash,
                cyclomatic, cognitive, 2, 0, 1, 0, cyclomatic - 1, 2);
    }
}
