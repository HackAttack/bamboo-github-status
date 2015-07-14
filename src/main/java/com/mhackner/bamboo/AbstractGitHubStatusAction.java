package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.plugins.git.GitHubRepository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.sal.api.ApplicationProperties;

import org.apache.commons.lang.StringUtils;
import org.eclipse.egit.github.core.CommitStatus;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.CommitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public abstract class AbstractGitHubStatusAction {

    private static final Logger log = LoggerFactory.getLogger(AbstractGitHubStatusAction.class);

    private final ApplicationProperties applicationProperties;
    private final EncryptionService encryptionService;

    public AbstractGitHubStatusAction(ApplicationProperties applicationProperties,
                                      EncryptionService encryptionService) {
        this.applicationProperties = applicationProperties;
        this.encryptionService = encryptionService;
    }

    void updateStatus(String status, Chain chain, ChainExecution chainExecution) {
        String disabled = chain.getBuildDefinition().getCustomConfiguration()
                .get("custom.gitHubStatus.disabled");
        if (Boolean.parseBoolean(disabled)) {
            return;
        }

        List<RepositoryDefinition> repos = chain.getEffectiveRepositoryDefinitions();
        if (repos.size() != 1) {
            log.warn("Wanted 1 repo but found {}. Not updating GitHub status.", repos.size());
            return;
        }

        RepositoryDefinition repoDefinition = repos.get(0);
        GitHubRepository repo = Narrow.downTo(repoDefinition.getRepository(),
                GitHubRepository.class);
        if (repo == null) {
            log.info("Repo {} is not a GitHub repo.", repoDefinition.getName());
            return;
        }

        String sha = chainExecution.getBuildChanges().getVcsRevisionKey(repoDefinition.getId());

        @SuppressWarnings("deprecation")
        String url = String.format("%s/browse/%s",
                StringUtils.removeEnd(applicationProperties.getBaseUrl(), "/"),
                chainExecution.getPlanResultKey());

        setStatus(status, sha, url, repo.getUsername(),
                encryptionService.decrypt(repo.getEncryptedPassword()),
                repo.getRepository());
    }

    private static void setStatus(String status, String sha, String url, String user, String pass,
                                  String repo) {
        GitHubClient client = new GitHubClient().setCredentials(user, pass);
        CommitService commitService = new CommitService(client);
        CommitStatus commitStatus = new CommitStatus()
                .setState(status)
                .setTargetUrl(url);
        try {
            commitService.createStatus(RepositoryId.createFromId(repo), sha, commitStatus);
            log.info("GitHub status for commit {} set to {}.", sha, status);
        } catch (IOException ex) {
            log.error("Failed to update GitHub status", ex);
        }
    }

}
