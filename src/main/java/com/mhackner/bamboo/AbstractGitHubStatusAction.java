package com.mhackner.bamboo;

import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plugins.git.GitHubRepository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.repository.RepositoryDefinitionManager;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.utils.BambooUrl;
import com.atlassian.bamboo.utils.SystemProperty;
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

    private static final String gitHubEndpoint =
            new SystemProperty(false, "atlassian.bamboo.github.api.base.url",
                    "ATLASSIAN_BAMBOO_GITHUB_API_BASE_URL").getValue("https://api.github.com");

    private final EncryptionService encryptionService;
    private final BambooUrl bambooUrl;
    private final RepositoryDefinitionManager repositoryDefinitionManager;
    private final PlanManager planManager;

    AbstractGitHubStatusAction(AdministrationConfigurationAccessor adminConfigAccessor,
                               EncryptionService encryptionService,
                               RepositoryDefinitionManager repositoryDefinitionManager,
                               PlanManager planManager) {
        this.encryptionService = encryptionService;
        bambooUrl = new BambooUrl(adminConfigAccessor);
        this.repositoryDefinitionManager = repositoryDefinitionManager;
        this.planManager = planManager;
    }

    void updateStatus(GHCommitState status, StageExecution stageExecution) {
        ChainExecution chainExecution = stageExecution.getChainExecution();
        PlanKey planKey = chainExecution.getPlanResultKey().getPlanKey();
        Plan plan = planManager.getPlanByKey(planKey);
        assert plan != null;
        List<RepositoryDefinition> repos = repositoryDefinitionManager
                .getRepositoryDefinitionsForPlan(plan);
        String configuredRepos = plan.getBuildDefinition().getCustomConfiguration()
                .get(Configuration.CONFIG_KEY);

        if (configuredRepos == null) {
            // TODO duplicative with Configuration
            List<RepositoryDefinition> ghRepos = Configuration.ghReposFrom(plan);
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
                        ghRepo.getRepository(), stageExecution.getName());
            }
        }
    }

    private static void setStatus(GHCommitState status, String sha, String url, String user,
                                  String pass, String repo, String context) {
        try {
            GitHub gitHub = GitHub.connectToEnterprise(gitHubEndpoint, user, pass);
            GHRepository repository = gitHub.getRepository(repo);
            sha = repository.getCommit(sha).getSHA1();
            repository.createCommitStatus(sha, status, url, null, context);
            log.info("GitHub status for commit {} ({}) set to {}.", sha, context, status);
        } catch (IOException ex) {
            log.error("Failed to update GitHub status", ex);
        }
    }

}
