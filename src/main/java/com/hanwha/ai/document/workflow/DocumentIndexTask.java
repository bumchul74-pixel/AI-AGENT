package com.hanwha.ai.document.workflow;

public interface DocumentIndexTask {
    default boolean supports(DocumentIndexContext context) {
        return true;
    }

    void execute(DocumentIndexContext context);

    default String name() {
        return getClass().getSimpleName();
    }
}
