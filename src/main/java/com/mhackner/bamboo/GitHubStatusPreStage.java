package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PreStageAction;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.repository.RepositoryDefinitionManager;
import com.atlassian.bamboo.security.EncryptionService;

import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHCommitState;

public class GitHubStatusPreStage extends AbstractGitHubStatusAction implements PreStageAction {

    public GitHubStatusPreStage(AdministrationConfigurationAccessor adminConfigAccessor,
                                EncryptionService encryptionService,
                                RepositoryDefinitionManager repositoryDefinitionManager,
                                CachedPlanManager cachedPlanManager) {
        super(adminConfigAccessor, encryptionService, repositoryDefinitionManager, cachedPlanManager);
    }

    @Override
    public void execute(@NotNull StageExecution stageExecution) {
        updateStatus(GHCommitState.PENDING, stageExecution);
    }

}
