package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.plugins.PostChainAction;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.security.EncryptionService;
import org.jetbrains.annotations.NotNull;

public class GitHubStatusPostChain extends AbstractGitHubStatusAction implements PostChainAction {
    public GitHubStatusPostChain(AdministrationConfigurationAccessor adminConfigAccessor, EncryptionService encryptionService, PlanManager planManager) {
        super(adminConfigAccessor, encryptionService, planManager);
    }

    @Override
    public void execute(@NotNull Chain chain,
                        @NotNull ChainResultsSummary chainResultsSummary,
                        @NotNull ChainExecution chainExecution) {
        updateStatusForMerge(chainResultsSummary, chainExecution);
    }
}
