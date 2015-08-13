package com.mhackner.bamboo;

import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.plugins.PostChainAction;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.security.EncryptionService;
import com.google.common.collect.ImmutableMap;

import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHCommitState;

public class GitHubStatusPostChain extends AbstractGitHubStatusAction implements PostChainAction {

    private static final ImmutableMap<BuildState, GHCommitState> states = ImmutableMap.of(
            BuildState.FAILED, GHCommitState.FAILURE,
            BuildState.SUCCESS, GHCommitState.SUCCESS,
            BuildState.UNKNOWN, GHCommitState.ERROR
    );

    public GitHubStatusPostChain(AdministrationConfigurationAccessor adminConfigAccessor,
                                 EncryptionService encryptionService) {
        super(adminConfigAccessor, encryptionService);
    }

    @Override
    public void execute(@NotNull Chain chain, @NotNull ChainResultsSummary chainResultsSummary,
                        @NotNull ChainExecution chainExecution) {
        updateStatus(states.get(chainResultsSummary.getBuildState()), chain, chainExecution);
    }

}
