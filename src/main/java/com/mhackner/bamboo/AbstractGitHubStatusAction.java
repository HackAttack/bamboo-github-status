package com.mhackner.bamboo;

import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.chains.BuildExecution;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.branches.MergeResultState;
import com.atlassian.bamboo.chains.branches.MergeResultSummary;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.PlanHelper;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plugins.git.GitHubRepository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.utils.BambooUrl;
import com.atlassian.bamboo.utils.SystemProperty;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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

    void updateStatus(StageExecution stageExecution) {
        GHCommitState status = statusOf(stageExecution);
        ChainExecution chainExecution = stageExecution.getChainExecution();
        PlanResultKey planResultKey = chainExecution.getPlanResultKey();
        PlanKey planKey = planResultKey.getPlanKey();
        ImmutableChain chain = (ImmutableChain) planManager.getPlanByKey(planKey);

        for (RepositoryDefinition repo : Configuration.ghReposFrom(chain)) {
            if (shouldUpdateRepo(chain, repo)) {
                String sha = chainExecution.getBuildChanges().getVcsRevisionKey(repo.getId());
                if (sha != null) {
                    GitHubRepository ghRepo = (GitHubRepository) repo.getRepository();
                    setStatus(ghRepo, status, sha, planResultKey.getKey(), stageExecution.getName());
                }
            }
        }
    }

    void updateStatusForMerge(ChainResultsSummary resultsSummary, ChainExecution chainExecution) {
        MergeResultSummary mergeResultSummary = resultsSummary.getMergeResult();
        if (mergeResultSummary == null) {
            log.debug("No merge summary present; skipping status update for merge");
            return;
        } else if (mergeResultSummary.getMergeState() != MergeResultState.SUCCESS) {
            log.info("Merge state was {}; skipping status update for merge", mergeResultSummary.getMergeState());
            return;
        }

        PlanResultKey planResultKey = chainExecution.getPlanResultKey();
        PlanKey planKey = planResultKey.getPlanKey();
        ImmutableChain chain = (ImmutableChain) planManager.getPlanByKey(planKey);
        if (chain == null) {
            return;
        }

        RepositoryDefinition defaultRepo = PlanHelper.getDefaultRepositoryDefinition(chain);
        if (defaultRepo == null || !shouldUpdateRepo(chain, defaultRepo)) {
            return;
        }
        GitHubRepository ghRepo = (GitHubRepository) defaultRepo.getRepository();

        String sha = mergeResultSummary.getMergeResultVcsKey();
        if (sha == null) {
            return;
        }

        for (StageExecution stageExecution : chainExecution.getStages()) {
            GHCommitState status = statusOf(stageExecution);
            setStatus(ghRepo, status, sha, planResultKey.getKey(), stageExecution.getName());
        }
    }

    private static GHCommitState statusOf(StageExecution stageExecution) {
        if (stageExecution.isCompleted()) {
            if (stageExecution.isSuccessful()) {
                return GHCommitState.SUCCESS;
            } else if (Iterables.any(stageExecution.getBuilds(), new Predicate<BuildExecution>() {
                @Override
                public boolean apply(BuildExecution input) {
                    return input.getBuildState() == BuildState.UNKNOWN;
                }
            })) {
                return GHCommitState.ERROR;
            } else {
                return GHCommitState.FAILURE;
            }
        } else {
            return GHCommitState.PENDING;
        }
    }

    private static boolean shouldUpdateRepo(ImmutableChain chain, final RepositoryDefinition repo) {
        String config = chain.getBuildDefinition().getCustomConfiguration()
                .get(Configuration.CONFIG_KEY);
        if (config == null) {
            return Configuration.DEFAULT_REPO_PREDICATE.apply(repo);
        } else {
            RepositoryDefinition repoToCheck = chain.hasMaster()
                    ? Iterables.find(
                            Configuration.ghReposFrom(chain.getMaster()),
                            new Predicate<RepositoryDefinition>() {
                                @Override
                                public boolean apply(RepositoryDefinition input) {
                                    return input.getName().equals(repo.getName());
                                }
                            })
                    : repo;

            return Configuration.toList(config).contains(repoToCheck.getId());
        }
    }

    private void setStatus(GitHubRepository repo, GHCommitState status, String sha,
                           String planResultKey, String context) {
        String url = bambooUrl.withBaseUrlFromConfiguration("/browse/" + planResultKey);
        try {
            GitHub gitHub = GitHub.connectToEnterprise(gitHubEndpoint, repo.getUsername(),
                    encryptionService.decrypt(repo.getEncryptedPassword()));
            GHRepository repository = gitHub.getRepository(repo.getRepository());
            sha = repository.getCommit(sha).getSHA1();
            repository.createCommitStatus(sha, status, url, null, context);
            log.info("GitHub status for commit {} ({}) set to {}.", sha, context, status);
        } catch (IOException ex) {
            log.error("Failed to update GitHub status", ex);
        }
    }

}
