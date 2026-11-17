package com.autotestfix.backend.repository;

import java.util.List;

public record RepositoryAnalysis(
        String repositoryPath,
        String projectType,
        String buildTool,
        List<String> buildFiles,
        List<String> sourceDirectories,
        List<String> testDirectories
) {
}