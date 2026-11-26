package com.autotestfix.backend.discovery;

import java.util.List;

public record TestDiscoveryResult(
        String repositoryPath,
        String buildTool,
        String testingFramework,
        int totalTestFiles,
        List<String> testFiles,
        String status
) {
}