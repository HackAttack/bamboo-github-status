package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plugins.git.GitHubRepository;
import com.atlassian.bamboo.plugins.git.GitRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.utils.BambooUrl;
import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.bamboo.vcs.configuration.PlanRepositoryDefinition;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class AbstractGitHubStatusAction {

    private static final Logger log = LoggerFactory.getLogger(AbstractGitHubStatusAction.class);

    private static final String gitHubEndpoint =
            new SystemProperty(false, "atlassian.bamboo.github.api.base.url",
                    "ATLASSIAN_BAMBOO_GITHUB_API_BASE_URL").getValue("https://api.github.com");

    private final EncryptionService encryptionService;
    private final BambooUrl bambooUrl;
    private final PlanManager planManager;

    AbstractGitHubStatusAction(AdministrationConfigurationAccessor adminConfigAccessor,
                               EncryptionService encryptionService,
                               PlanManager planManager) {
        this.encryptionService = encryptionService;
        bambooUrl = new BambooUrl(adminConfigAccessor);
        this.planManager = planManager;
    }

    void updateStatus(GHCommitState status, StageExecution stageExecution) {
        ChainExecution chainExecution = stageExecution.getChainExecution();
        PlanResultKey planResultKey = chainExecution.getPlanResultKey();
        PlanKey planKey = planResultKey.getPlanKey();
        ImmutableChain chain = (ImmutableChain) planManager.getPlanByKey(planKey);

        for (PlanRepositoryDefinition repo : Configuration.getPlanRepositories(chain)) {

            if (shouldUpdateRepo(chain, repo)) {
                String sha = chainExecution.getBuildChanges().getVcsRevisionKey(repo.getId());
                if (sha != null) {
                    setStatus(repo.asLegacyData().getRepository(), status, sha, planResultKey.getKey(), stageExecution.getName());
                }
            } else {
                log.debug("Should not update repo: {}", repo.getName());
            }
        }
    }

    private boolean shouldUpdateRepo(ImmutableChain chain, final PlanRepositoryDefinition repo) {

        PlanRepositoryDefinition repoToCheck = chain.hasMaster()
                ? Iterables.find(
                Configuration.getPlanRepositories(chain.getMaster()),
                new Predicate<PlanRepositoryDefinition>() {
                    @Override
                    public boolean apply(PlanRepositoryDefinition input) {
                        boolean result = input.getName().equals(repo.getName());
                        if (result) {
                            try {
                                result = isTargetGithubRepository(input);
                            } catch (MalformedURLException ex) {
                                throw new RuntimeException("Failed checking repository definition hostname", ex);
                            }
                        }
                        return result;
                    }
                })
                : repo;
        return Configuration.isRepositorySelected(chain.getBuildDefinition().getCustomConfiguration(), repoToCheck.getId());
    }

    private void setStatus(Repository repo, GHCommitState status, String sha,
                           String planResultKey, String context) {
        String url = bambooUrl.withBaseUrlFromConfiguration("/browse/" + planResultKey);
        try {
            String password;
            String username;
            String repositoryUrl;
            if (repo instanceof GitHubRepository) {
                GitHubRepository gitHubRepository = (GitHubRepository) repo;
                try {
                    password = gitHubRepository.getClass().getDeclaredMethod("getPassword").invoke(gitHubRepository).toString();
                } catch (NoSuchMethodException ex) {
                    password = encryptionService.decrypt(
                            gitHubRepository.getClass().getDeclaredMethod("getEncryptedPassword").invoke(gitHubRepository).toString());
                }
                username = gitHubRepository.getUsername();
                repositoryUrl = gitHubRepository.getRepository();
            } else {
                GitRepository gitRepository = (GitRepository) repo;
                password = gitRepository.getAccessData().getPassword();
                username = gitRepository.getAccessData().getUsername();
                repositoryUrl = gitRepository.getAccessData().getRepositoryUrl();
                repositoryUrl = getRelativePath(repositoryUrl);
            }

            log.info(String.format("Connecting to github ... username = %s, password = %s, repositoryUrl = %s",
                    username, password, repositoryUrl));

            GitHub gitHub = GitHub.connectToEnterprise(gitHubEndpoint, username, password);
            GHRepository repository = gitHub.getRepository(repositoryUrl);
            sha = repository.getCommit(sha).getSHA1();
            repository.createCommitStatus(sha, status, url, null, context);
            log.info("GitHub status for commit {} ({}) set to {}.", sha, context, status);
        } catch (Exception ex) {
            log.error("Failed to update GitHub status", ex);
        }
    }

    private static String getRelativePath(String url) throws MalformedURLException {

        String path = new URL(url).getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path.replace(".git", "");
    }

    private boolean isTargetGithubRepository(PlanRepositoryDefinition repositoryDefinition) throws MalformedURLException {

        Repository repository = repositoryDefinition.asLegacyData().getRepository();
        if (repository instanceof GitRepository) {
            GitRepository gitRepository = (GitRepository) repository;
            URL repositoryUrl = new URL(gitRepository.getAccessData().getRepositoryUrl());
            URL githubUrl = new URL(gitHubEndpoint);
            return repositoryUrl.getHost().toLowerCase().equals(githubUrl.getHost().toLowerCase());
        } else {
            return true;
        }
    }
}
