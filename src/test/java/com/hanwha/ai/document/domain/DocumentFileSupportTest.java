package com.hanwha.ai.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentFileSupportTest {

    @Test
    void identifiesJavaTestSourcesByPathAndConventionalFileName() {
        assertThat(DocumentFileSupport.isTestJavaSourceFile(
                "sample/src/test/java/com/example/UserService.java")).isTrue();
        assertThat(DocumentFileSupport.isTestJavaSourceFile(
                "sample/testcase/com/example/UserScenario.java")).isTrue();
        assertThat(DocumentFileSupport.isTestJavaSourceFile(
                "sample/src/integrationTest/java/com/example/UserServiceIT.java")).isTrue();
        assertThat(DocumentFileSupport.isTestJavaSourceFile("TestUserService.java")).isTrue();
        assertThat(DocumentFileSupport.isTestJavaSourceFile("UserServiceTests.java")).isTrue();
        assertThat(DocumentFileSupport.isTestJavaSourceFile("UserServiceTestCase.java")).isTrue();
    }

    @Test
    void keepsProductionJavaSourcesAndNonJavaFiles() {
        assertThat(DocumentFileSupport.isTestJavaSourceFile(
                "sample/src/main/java/com/example/UserService.java")).isFalse();
        assertThat(DocumentFileSupport.isTestJavaSourceFile("Latest.java")).isFalse();
        assertThat(DocumentFileSupport.isTestJavaSourceFile("src/test/resources/schema.sql")).isFalse();
    }
}
