package com.hanwha.ai.generation.service;

import java.util.List;

public interface ProjectStructureAnalyzer {
    String analyze(String projectPath, List<String> targetTypes);
}
