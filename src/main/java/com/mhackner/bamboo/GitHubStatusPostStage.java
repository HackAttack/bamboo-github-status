package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.ChainStageResult;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PostStageAction;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.security.EncryptionService;

import org.jetbrains.annotations.NotNull;

public class GitHubStatusPostStage extends AbstractGitHubStatusAction implements PostStageAction {

    public GitHubStatusPostStage(AdministrationConfigurationAccessor adminConfigAccessor,
                                 EncryptionService encryptionService,
                                 PlanManager planManager) {
        super(adminConfigAccessor, encryptionService, planManager);
    }

    @Override
    public void execute(@NotNull ChainResultsSummary chainResultsSummary,
                        @NotNull ChainStageResult chainStageResult,
                        @NotNull StageExecution stageExecution) {
        updateStatus(stageExecution);
    }

}
