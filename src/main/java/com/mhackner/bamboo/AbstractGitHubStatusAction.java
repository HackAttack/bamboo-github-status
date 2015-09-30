package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plugins.git.GitHubRepository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.utils.BambooUrl;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public abstract class AbstractGitHubStatusAction {

    private static final Logger log = LoggerFactory.getLogger(AbstractGitHubStatusAction.class);

    private final EncryptionService encryptionService;
    private final BambooUrl bambooUrl;

    AbstractGitHubStatusAction(AdministrationConfigurationAccessor adminConfigAccessor,
                               EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
        bambooUrl = new BambooUrl(adminConfigAccessor);
    }

    void updateStatus(GHCommitState status, Chain chain, ChainExecution chainExecution) {
        List<RepositoryDefinition> repos = chain.getEffectiveRepositoryDefinitions();
        String configuredRepos = chain.getBuildDefinition()
                .getCustomConfiguration().get(Configuration.CONFIG_KEY);

        if (configuredRepos == null) {
            // TODO duplicative with Configuration
            List<RepositoryDefinition> ghRepos = Configuration.ghReposFrom(chain);
            configuredRepos = Lists.transform(ghRepos,
                    new Function<RepositoryDefinition, Long>() {
                        @Override
                        public Long apply(RepositoryDefinition input) {
                            return input.getId();
                        }
                    }).toString();
        }

        for (RepositoryDefinition repo : repos) {
            if (configuredRepos.contains(Long.toString(repo.getId()))) {
                GitHubRepository ghRepo = Narrow.downTo(repo.getRepository(),
                        GitHubRepository.class);
                assert ghRepo != null; // only GitHub repos are selectable in the UI
                String sha = chainExecution.getBuildChanges().getVcsRevisionKey(repo.getId());
                if (sha == null) {
                    return;
                }

                String url = bambooUrl.withBaseUrlFromConfiguration(
                        "/browse/" + chainExecution.getPlanResultKey());

                setStatus(status, sha, url, ghRepo.getUsername(),
                        encryptionService.decrypt(ghRepo.getEncryptedPassword()),
                        ghRepo.getRepository());
            }
        }
    }

    private static void setStatus(GHCommitState status, String sha, String url, String user,
                                  String pass, String repo) {
        try {
            GitHub gitHub = GitHub.connectUsingPassword(user, pass);
            GHRepository repository = gitHub.getRepository(repo);
            sha = repository.getCommit(sha).getSHA1();
            repository.createCommitStatus(sha, status, url, null);
            log.info("GitHub status for commit {} set to {}.", sha, status);
        } catch (IOException ex) {
            log.error("Failed to update GitHub status", ex);
        }
    }

}
