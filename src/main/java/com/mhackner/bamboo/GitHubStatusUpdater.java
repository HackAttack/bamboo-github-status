package com.mhackner.bamboo;

import com.atlassian.bamboo.plugins.git.GitHubRepository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.events.BuildContextEvent;
import com.atlassian.bamboo.v2.build.events.BuildQueuedEvent;
import com.atlassian.bamboo.v2.build.events.PostBuildCompletedEvent;
import com.atlassian.event.api.EventListener;
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

public class GitHubStatusUpdater {

    private static final Logger log = Logger.getLogger(GitHubStatusUpdater.class);

    private final ApplicationProperties applicationProperties;

    public GitHubStatusUpdater(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @EventListener
    public void handleEvent(BuildQueuedEvent event) {
        updateStatus(CommitStatus.STATE_PENDING, event);
    }

    @EventListener
    public void handleEvent(PostBuildCompletedEvent event) {
        switch (event.getContext().getBuildResult().getBuildState()) {
        case FAILED:
            updateStatus(CommitStatus.STATE_FAILURE, event);
            break;
        case SUCCESS:
            updateStatus(CommitStatus.STATE_SUCCESS, event);
            break;
        case UNKNOWN:
            updateStatus(CommitStatus.STATE_ERROR, event);
            break;
        }
    }

    private void updateStatus(String status, BuildContextEvent event) {
        BuildContext context = event.getContext();
        List<RepositoryDefinition> repoDefinitions = context.getRepositoryDefinitions();
        if (repoDefinitions.size() != 1) {
            log.info(String.format("Wanted 1 repo but found %d. Not updating GitHub status.",
                    repoDefinitions.size()));
            return;
        }
        RepositoryDefinition repositoryDefinition = repoDefinitions.get(0);
        GitHubRepository repo = Narrow.downTo(repositoryDefinition.getRepository(),
                GitHubRepository.class);
        if (repo == null) {
            log.info("Repo is not a GitHub repo.");
            return;
        }

        String sha = context.getBuildChanges().getVcsRevisionKey(repositoryDefinition.getId());
        String url = String.format("%s/browse/%s",
                StringUtils.removeEnd(applicationProperties.getBaseUrl(UrlMode.CANONICAL), "/"),
                context.getPlanResultKey());
        setStatus(status, sha, url, repo.getUsername(), repo.getEncryptedPassword(),
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
        } catch (IOException ex) {
            log.error("Failed to update GitHub status", ex);
        }
    }

}
