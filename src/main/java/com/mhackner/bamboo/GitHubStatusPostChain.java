package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.plugins.PostChainAction;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.security.EncryptionService;

import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHCommitState;

public class GitHubStatusPostChain extends AbstractGitHubStatusAction implements PostChainAction {

    public GitHubStatusPostChain(AdministrationConfigurationAccessor adminConfigAccessor,
                                 EncryptionService encryptionService) {
        super(adminConfigAccessor, encryptionService);
    }

    @Override
    public void execute(@NotNull Chain chain, @NotNull ChainResultsSummary chainResultsSummary,
                        @NotNull ChainExecution chainExecution) {
        switch (chainResultsSummary.getBuildState()) {
        case FAILED:
            updateStatus(GHCommitState.FAILURE, chain, chainExecution);
            break;
        case SUCCESS:
            updateStatus(GHCommitState.SUCCESS, chain, chainExecution);
            break;
        case UNKNOWN:
            updateStatus(GHCommitState.ERROR, chain, chainExecution);
            break;
        }
    }

}
