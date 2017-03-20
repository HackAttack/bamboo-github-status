package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PreStageAction;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.security.EncryptionService;

import org.jetbrains.annotations.NotNull;

public class GitHubStatusPreStage extends AbstractGitHubStatusAction implements PreStageAction {

    public GitHubStatusPreStage(AdministrationConfigurationAccessor adminConfigAccessor,
                                EncryptionService encryptionService,
                                PlanManager planManager) {
        super(adminConfigAccessor, encryptionService, planManager);
    }

    @Override
    public void execute(@NotNull StageExecution stageExecution) {
        updateStatus(stageExecution);
    }

}
