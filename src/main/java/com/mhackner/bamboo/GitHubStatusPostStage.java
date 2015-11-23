package com.mhackner.bamboo;

import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.chains.BuildExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.ChainStageResult;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PostStageAction;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.repository.RepositoryDefinitionManager;
import com.atlassian.bamboo.security.EncryptionService;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHCommitState;

public class GitHubStatusPostStage extends AbstractGitHubStatusAction implements PostStageAction {

    public GitHubStatusPostStage(AdministrationConfigurationAccessor adminConfigAccessor,
                                 EncryptionService encryptionService,
                                 RepositoryDefinitionManager repositoryDefinitionManager,
                                 CachedPlanManager cachedPlanManager) {
        super(adminConfigAccessor, encryptionService, repositoryDefinitionManager, cachedPlanManager);
    }

    @Override
    public void execute(@NotNull ChainResultsSummary chainResultsSummary,
                        @NotNull ChainStageResult chainStageResult,
                        @NotNull StageExecution stageExecution) {
        updateStatus(statusOf(stageExecution), stageExecution);
    }

    private static GHCommitState statusOf(StageExecution stageExecution) {
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
    }

}
