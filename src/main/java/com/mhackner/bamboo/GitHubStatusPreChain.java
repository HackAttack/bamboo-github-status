package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.plugins.PreChainAction;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.security.EncryptionService;

import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHCommitState;

public class GitHubStatusPreChain extends AbstractGitHubStatusAction implements PreChainAction {

    public GitHubStatusPreChain(AdministrationConfigurationAccessor adminConfigAccessor,
                                EncryptionService encryptionService) {
        super(adminConfigAccessor, encryptionService);
    }

    @Override
    public void execute(@NotNull Chain chain, @NotNull ChainExecution chainExecution) {
        updateStatus(GHCommitState.PENDING, chain, chainExecution);
    }

}
