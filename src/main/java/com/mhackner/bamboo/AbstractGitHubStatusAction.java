package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.plugins.git.GitHubRepository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.egit.github.core.CommitStatus;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.CommitService;

import java.io.IOException;
import java.util.List;

public abstract class AbstractGitHubStatusAction {

    private static final Logger log = Logger.getLogger(AbstractGitHubStatusAction.class);

    private final ApplicationProperties applicationProperties;
    private final EncryptionService encryptionService;

    public AbstractGitHubStatusAction(ApplicationProperties applicationProperties,
                                      EncryptionService encryptionService) {
        this.applicationProperties = applicationProperties;
        this.encryptionService = encryptionService;
    }

    void updateStatus(String status, Chain chain, ChainExecution chainExecution) {
        List<RepositoryDefinition> repos = chain.getEffectiveRepositoryDefinitions();
        if (repos.size() != 1) {
            log.warn(String.format("Wanted 1 repo but found %d. Not updating GitHub status.",
                    repos.size()));
            return;
        }

        RepositoryDefinition repoDefinition = repos.get(0);
        GitHubRepository repo = Narrow.downTo(repoDefinition.getRepository(),
                GitHubRepository.class);
        if (repo == null) {
            log.info(String.format("Repo %s is not a GitHub repo.",
                    repoDefinition.getRepository().getName()));
            return;
        }

        String sha = chainExecution.getBuildChanges().getVcsRevisionKey(repoDefinition.getId());
        String url = String.format("%s/browse/%s",
                StringUtils.removeEnd(applicationProperties.getBaseUrl(UrlMode.CANONICAL), "/"),
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
            log.info(String.format("GitHub status for commit %s set to %s.", sha, status));
        } catch (IOException ex) {
            log.error("Failed to update GitHub status", ex);
        }
    }

}
