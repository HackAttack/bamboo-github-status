package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.plugins.PreChainAction;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.sal.api.ApplicationProperties;

import org.eclipse.egit.github.core.CommitStatus;
import org.jetbrains.annotations.NotNull;

public class GitHubStatusPreChain extends AbstractGitHubStatusAction implements PreChainAction {

    public GitHubStatusPreChain(ApplicationProperties applicationProperties,
                                EncryptionService encryptionService) {
        super(applicationProperties, encryptionService);
    }

    @Override
    public void execute(@NotNull Chain chain, @NotNull ChainExecution chainExecution) {
        updateStatus(CommitStatus.STATE_PENDING, chain, chainExecution);
    }

}
