package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.plugins.PostChainAction;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.sal.api.ApplicationProperties;

import org.eclipse.egit.github.core.CommitStatus;
import org.jetbrains.annotations.NotNull;

public class GitHubStatusPostChain extends AbstractGitHubStatusAction implements PostChainAction {

    public GitHubStatusPostChain(ApplicationProperties applicationProperties,
                                 EncryptionService encryptionService) {
        super(applicationProperties, encryptionService);
    }

    @Override
    public void execute(@NotNull Chain chain, @NotNull ChainResultsSummary chainResultsSummary,
                        @NotNull ChainExecution chainExecution) {
        switch (chainResultsSummary.getBuildState()) {
        case FAILED:
            updateStatus(CommitStatus.STATE_FAILURE, chain, chainExecution);
            break;
        case SUCCESS:
            updateStatus(CommitStatus.STATE_SUCCESS, chain, chainExecution);
            break;
        case UNKNOWN:
            updateStatus(CommitStatus.STATE_ERROR, chain, chainExecution);
            break;
        }
    }

}
