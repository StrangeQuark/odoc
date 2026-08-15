package com.strangequark.odoc.github;

interface GithubRepositoryClient {
    GithubFetchedRepository fetchPublicRepository(String owner, String repository);
}
