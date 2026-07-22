package com.hanwha.ai.sourcegraph.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.generation.domain.GenerationHistory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaSourceGraphAnalyzerTest {
    private final JavaSourceGraphAnalyzer analyzer = new JavaSourceGraphAnalyzer();

    @Test
    void logicalTypeUidIsStableAcrossFilesInTheSameProject() {
        SourceGraphResponse first = analyze(
                "document:10",
                "src/main/java/com/example/UserService.java"
        );
        SourceGraphResponse second = analyze(
                "document:11",
                "generated/com/example/UserService.java"
        );

        assertThat(first.nodes()).anyMatch(node ->
                node.id().equals("type:commerce:com.example.UserService"));
        assertThat(second.nodes()).anyMatch(node ->
                node.id().equals("type:commerce:com.example.UserService"));
        assertThat(first.nodes()).anyMatch(node ->
                node.id().equals("file:commerce:src/main/java/com/example/UserService.java"));
        assertThat(second.nodes()).anyMatch(node ->
                node.id().equals("file:commerce:generated/com/example/UserService.java"));
    }

    @Test
    void methodUidUsesDeclaringFqnAndNormalizedSignature() {
        SourceGraphResponse graph = analyze(
                "document:10",
                ".\\src\\main\\java\\com\\example\\UserService.java"
        );

        assertThat(graph.nodes()).anyMatch(node ->
                node.id().equals(
                        "method:commerce:com.example.UserService:findUser(Long,java.util.List<String>)"
                ));
    }

    @Test
    void createsOntologyHierarchyChunkEvidenceAndRequiredMetadata() {
        SourceGraphResponse graph = analyzer.analyzeJavaSource(new JavaSourceGraphIngestRequest(
                "document:10",
                "UserService.java",
                "package com.example; public class UserService {}",
                "commerce",
                "backend",
                "src/main/java/com/example/UserService.java",
                "abc123",
                List.of("document:10:chunk:0")
        ));

        assertThat(graph.nodes()).extracting(node -> node.label()).contains(
                "Project", "Module", "Package", "SourceFile", "JavaType", "Document", "Chunk"
        );
        assertThat(graph.relationships()).anyMatch(relationship ->
                relationship.sourceId().equals("project:commerce")
                        && relationship.targetId().equals("module:commerce:backend")
                        && relationship.type().equals("CONTAINS"));
        assertThat(graph.relationships()).anyMatch(relationship ->
                relationship.sourceId().equals("document:10:chunk:0")
                        && relationship.targetId().equals("type:commerce:com.example.UserService")
                        && relationship.type().equals("DESCRIBES"));

        List<String> requiredMetadata = List.of(
                "projectId", "sourceKey", "fileHash", "analysisVersion", "validFrom",
                "validTo", "confidence", "extractor", "evidenceChunkIds", "createdAt"
        );
        assertThat(graph.nodes()).allSatisfy(node ->
                assertThat(node.properties()).containsKeys(requiredMetadata.toArray(String[]::new)));
        assertThat(graph.nodes()).allSatisfy(node ->
                assertThat(node.properties()).doesNotContainKey("sourceContent"));
        assertThat(graph.relationships()).allSatisfy(relationship ->
                assertThat(relationship.properties()).containsKeys(requiredMetadata.toArray(String[]::new)));
    }

    @Test
    void extractsFieldsEndpointsCallsAndDtoUsage() {
        SourceGraphResponse graph = analyzer.analyzeJavaSource(new JavaSourceGraphIngestRequest(
                "document:20",
                "UserController.java",
                """
                        package com.example;

                        @RequestMapping("/users")
                        public class UserController {
                            private UserService userService;

                            @GetMapping("/{id}")
                            public UserResponse getUser(Long id) {
                                return loadUser(id);
                            }

                            private UserResponse loadUser(Long id) {
                                return null;
                            }
                        }
                        """,
                "commerce",
                "backend",
                "src/main/java/com/example/UserController.java",
                "hash20"
        ));

        assertThat(graph.nodes()).extracting(node -> node.label())
                .contains("Field", "ApiEndpoint", "Method");
        assertThat(graph.relationships()).extracting(relationship -> relationship.type())
                .contains("HAS_FIELD", "HANDLED_BY", "CALLS", "USES_DTO");
        assertThat(graph.nodes()).anyMatch(node ->
                node.id().equals("endpoint:commerce:GET:/users/{id}"));
    }

    @Test
    void extractsMapperSqlTablesColumnsAndReadRelations() {
        SourceGraphResponse graph = analyzer.analyzeJavaSource(new JavaSourceGraphIngestRequest(
                "document:30",
                "UserMapper.java",
                """
                        package com.example;

                        @Mapper
                        public interface UserMapper {
                            @Select("SELECT id, name FROM app.users WHERE id = #{id}")
                            UserDto findById(Long id);
                        }
                        """,
                "commerce",
                "backend",
                "src/main/java/com/example/UserMapper.java",
                "hash30"
        ));

        assertThat(graph.nodes()).extracting(node -> node.label())
                .contains("SqlStatement", "DatabaseTable", "DatabaseColumn");
        assertThat(graph.nodes()).anyMatch(node ->
                node.id().equals("table:commerce:app:users"));
        assertThat(graph.nodes()).anyMatch(node ->
                node.id().equals("column:commerce:app:users:id"));
        assertThat(graph.relationships()).extracting(relationship -> relationship.type())
                .contains("EXECUTES", "READS_FROM", "MAPS_TO", "HAS_COLUMN");
    }

    @Test
    void linksGenerationToActualRagStandardRuleEvidence() throws Exception {
        GenerationHistory history = new GenerationHistory();
        history.setId(40L);
        history.setTargetType("UserService");
        history.setProjectStructure("commerce");
        history.setGeneratedCode("package com.example; public class UserService {}");
        history.setRagDocumentsJson(new ObjectMapper().writeValueAsString(
                List.of("[source: standard.md] Controller standard")
        ));

        SourceGraphResponse graph = analyzer.analyze(history);

        assertThat(graph.nodes()).extracting(node -> node.label()).contains("StandardRule");
        assertThat(graph.nodes()).filteredOn(node -> node.label().equals("StandardRule"))
                .allSatisfy(node -> assertThat(node.properties()).doesNotContainKey("content"));
        assertThat(graph.relationships()).anyMatch(relationship ->
                relationship.sourceId().equals("generation:40")
                        && relationship.type().equals("BASED_ON"));
    }

    @Test
    void storesExplicitRuleConformanceAndViolationResults() {
        SourceGraphResponse graph = analyzer.analyzeJavaSource(new JavaSourceGraphIngestRequest(
                "document:50",
                "UserService.java",
                "package com.example; public class UserService {}",
                "commerce",
                "backend",
                "src/main/java/com/example/UserService.java",
                "hash50",
                List.of("chunk:50:0"),
                List.of("service-layer-rule"),
                List.of("transaction-rule")
        ));

        assertThat(graph.nodes()).anyMatch(node ->
                node.id().equals("rule:commerce:service-layer-rule"));
        assertThat(graph.nodes()).anyMatch(node ->
                node.id().equals("rule:commerce:transaction-rule"));
        assertThat(graph.relationships()).extracting(relationship -> relationship.type())
                .contains("CONFORMS_TO", "VIOLATES");
    }

    @Test
    void analyzesSpringAnnotationsExternalCallsAndControllerRepositoryViolation() {
        SourceGraphResponse graph = analyzer.analyzeJavaSource(new JavaSourceGraphIngestRequest(
                "document:60",
                "UserController.java",
                """
                        package com.example;
                        @RestController
                        @RequestMapping("/users")
                        public class UserController {
                            private UserRepository userRepository;
                            @GetMapping("/{id}")
                            @Transactional
                            public UserDto getUser(Long id) {
                                return userRepository.findById(id);
                            }
                        }
                        """,
                "commerce", "backend", "src/main/java/com/example/UserController.java", "hash60"
        ));

        assertThat(graph.nodes()).anyMatch(node ->
                node.id().equals("method:commerce:com.example.UserController:getUser(Long)")
                        && Boolean.TRUE.equals(node.properties().get("transactional")));
        assertThat(graph.relationships()).anyMatch(relationship ->
                relationship.sourceId().equals("method:commerce:com.example.UserController:getUser(Long)")
                        && relationship.targetId().equals(
                        "method:commerce:com.example.UserRepository:findById(Long)")
                        && relationship.type().equals("CALLS"));
        assertThat(graph.relationships()).anyMatch(relationship ->
                relationship.sourceId().equals("type:commerce:com.example.UserController")
                        && relationship.targetId().equals(
                        "rule:commerce:controller-must-not-call-repository")
                        && relationship.type().equals("VIOLATES"));
    }

    @Test
    void evaluatesServiceImplContractAndMapsDtoToDomain() {
        SourceGraphResponse conforming = analyzer.analyzeJavaSource(new JavaSourceGraphIngestRequest(
                "document:70", "UserServiceImpl.java",
                """
                        package com.example;
                        @Service
                        public class UserServiceImpl implements UserService {
                            @Transactional
                            public UserDto toDto(User user) { return null; }
                        }
                        interface UserService {}
                        class User {}
                        class UserDto {}
                        """,
                "commerce", "backend", "src/main/java/com/example/UserServiceImpl.java", "hash70"
        ));
        SourceGraphResponse violating = analyzer.analyzeJavaSource(new JavaSourceGraphIngestRequest(
                "document:71", "OrderServiceImpl.java",
                "package com.example; public class OrderServiceImpl {}",
                "commerce", "backend", "src/main/java/com/example/OrderServiceImpl.java", "hash71"
        ));

        assertThat(conforming.relationships()).anyMatch(relationship ->
                relationship.sourceId().equals("type:commerce:com.example.UserServiceImpl")
                        && relationship.targetId().equals(
                        "rule:commerce:service-impl-must-implement-service")
                        && relationship.type().equals("CONFORMS_TO"));
        assertThat(conforming.relationships()).anyMatch(relationship ->
                relationship.sourceId().equals("type:commerce:com.example.UserDto")
                        && relationship.targetId().equals("type:commerce:com.example.User")
                        && relationship.type().equals("MAPS_TO"));
        assertThat(violating.relationships()).anyMatch(relationship ->
                relationship.sourceId().equals("type:commerce:com.example.OrderServiceImpl")
                        && relationship.type().equals("VIOLATES"));
    }

    private SourceGraphResponse analyze(String source, String filePath) {
        return analyzer.analyzeJavaSource(new JavaSourceGraphIngestRequest(
                source,
                "UserService.java",
                """
                        package com.example;

                        public class UserService {
                            public String findUser(Long id, java.util.List<String> names) {
                                return "";
                            }
                        }
                        """,
                "commerce",
                "backend",
                filePath,
                "abc123"
        ));
    }
}
