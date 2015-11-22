package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.plugins.git.GitHubRepository;
import com.atlassian.bamboo.plugins.git.GitRepository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.repository.RepositoryDefinitionManager;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.utils.BambooUrl;
import com.atlassian.bamboo.utils.SystemProperty;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractGitHubStatusAction {

    private static final Logger log = LoggerFactory.getLogger(AbstractGitHubStatusAction.class);

    private static final String gitHubEndpoint =
            new SystemProperty(false, "atlassian.bamboo.github.api.base.url",
                    "ATLASSIAN_BAMBOO_GITHUB_API_BASE_URL").getValue("https://api.github.com");

    private final EncryptionService encryptionService;
    private final BambooUrl bambooUrl;
    private final RepositoryDefinitionManager repositoryDefinitionManager;
    private final CachedPlanManager cachedPlanManager;

    AbstractGitHubStatusAction(AdministrationConfigurationAccessor adminConfigAccessor,
                               EncryptionService encryptionService,
                               RepositoryDefinitionManager repositoryDefinitionManager,
                               CachedPlanManager cachedPlanManager) {
        this.encryptionService = encryptionService;
        bambooUrl = new BambooUrl(adminConfigAccessor);
        this.repositoryDefinitionManager = repositoryDefinitionManager;
        this.cachedPlanManager = cachedPlanManager;
    }

    void updateStatus(GHCommitState status, StageExecution stageExecution) {
        ChainExecution chainExecution = stageExecution.getChainExecution();
        PlanKey planKey = chainExecution.getPlanResultKey().getPlanKey();
        final ImmutablePlan plan = cachedPlanManager.getPlanByKey(planKey);
        assert plan != null;
        final ImmutablePlan masterPlan = plan.hasMaster() ? plan.getMaster() : plan;

        // Repositories are configured on the master plan. We also send status updates for branch plans if the
        // repository URL for the branch plan matches a configured repository on the master plan.
        List<GitHubRepository> configuredRepos = configuredGitHubReposFor(masterPlan);

        List <RepositoryDefinition> planRepoDefinitions = repositoryDefinitionManager
                .getRepositoryDefinitionsForPlan(plan);
        for (RepositoryDefinition planRepoDefinition : planRepoDefinitions) {
            final GitHubRepository planRepo = Narrow.downTo(planRepoDefinition.getRepository(), GitHubRepository.class);
            if (planRepo != null) {
                boolean isConfigured = Iterables.any(configuredRepos, new Predicate<GitHubRepository>() {
                    @Override
                    public boolean apply(GitHubRepository configuredRepository) {
                        final GitRepository configuredGitRepo = configuredRepository.getGitRepository();
                        final GitRepository planGitRepo = planRepo.getGitRepository();
                        if (configuredGitRepo != null && planGitRepo != null) {
                            return StringUtils.equals(configuredGitRepo.getRepositoryUrl(),
                                    planGitRepo.getRepositoryUrl());
                        } else {
                            return false;
                        }
                    }
                });

                if (isConfigured) {
                    String sha = chainExecution.getBuildChanges().getVcsRevisionKey(planRepoDefinition.getId());
                    if (sha == null) {
                        return;
                    }

                    String url = bambooUrl.withBaseUrlFromConfiguration(
                            "/browse/" + chainExecution.getPlanResultKey());

                    setStatus(status, sha, url, planRepo.getUsername(),
                            encryptionService.decrypt(planRepo.getEncryptedPassword()),
                            planRepo.getRepository(), stageExecution.getName());
                }
            }

        }
    }

    private static void setStatus(GHCommitState status, String sha, String url, String user,
                                  String pass, String repo, String context) {
        try {
            GitHub gitHub = GitHub.connectToEnterprise(gitHubEndpoint, user, pass);
            GHRepository repository = gitHub.getRepository(repo);
            sha = repository.getCommit(sha).getSHA1();
            repository.createCommitStatus(sha, status, url, null, context);
            log.info("GitHub status for commit {} ({}) set to {}.", sha, context, status);
        } catch (IOException ex) {
            log.error("Failed to update GitHub status", ex);
        }
    }

    private List<GitHubRepository> configuredGitHubReposFor(final ImmutablePlan plan) {
        String customConfigValue = plan.getBuildDefinition().getCustomConfiguration()
                .get(Configuration.CONFIG_KEY);
        if (customConfigValue != null) {
            final List<Long> configuredRepoIds = Configuration.toList(customConfigValue);
            final List<RepositoryDefinition> allPlanRepoDefinitions = repositoryDefinitionManager
                    .getRepositoryDefinitionsForPlan(plan);

            final ArrayList<GitHubRepository> configuredRepos = Lists.newArrayList();
            for (RepositoryDefinition repositoryDefinition : allPlanRepoDefinitions) {
                if (configuredRepoIds.contains(repositoryDefinition.getId())) {
                    GitHubRepository repository = Narrow.downTo(repositoryDefinition.getRepository(),
                            GitHubRepository.class);
                    if (repository != null) {
                        configuredRepos.add(repository);
                    }
                }
            }

            return configuredRepos;
        } else {
            // By default, all GitHub repos are enabled.
            return Configuration.ghReposFor(plan);
        }
    }

}
