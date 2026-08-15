package com.strangequark.odoc.github;

record GithubFetchedRepository(
        String owner,
        String name,
        String canonicalUrl,
        String description,
        String defaultBranch,
        int stars,
        String readmeContent,
        String readmePath) {}
