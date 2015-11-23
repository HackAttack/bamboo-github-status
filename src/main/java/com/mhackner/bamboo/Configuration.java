package com.mhackner.bamboo;

import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.TopLevelPlan;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.plugins.git.GitHubRepository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.v2.build.BaseBuildConfigurationAwarePlugin;
import com.atlassian.bamboo.v2.build.configuration.MiscellaneousBuildConfigurationPlugin;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Configuration extends BaseBuildConfigurationAwarePlugin
        implements MiscellaneousBuildConfigurationPlugin {

    static final String CONFIG_KEY = "custom.gitHubStatus.repositories";

    private Plan plan;

    @Override
    public boolean isApplicableTo(@NotNull Plan plan) {
        this.plan = plan;
        return plan instanceof TopLevelPlan;
    }

    @Override
    public void addDefaultValues(@NotNull BuildConfiguration buildConfiguration) {
        buildConfiguration.setProperty(CONFIG_KEY, Lists.transform(
                ghRepoDefinitionsFor(plan),
                new Function<RepositoryDefinition, Long>() {
                    @Override
                    public Long apply(RepositoryDefinition input) {
                        return input.getId();
                    }
                }));
    }

    @Override
    public boolean isConfigurationMissing(@NotNull final BuildConfiguration buildConfiguration) {
        return !buildConfiguration.containsKey(CONFIG_KEY);
    }

    @Override
    protected void populateContextForEdit(@NotNull Map<String, Object> context,
                                          @NotNull BuildConfiguration buildConfiguration,
                                          Plan plan) {
        context.put("gitHubRepositories", Iterables.toArray(ghRepoDefinitionsFor(plan),
                RepositoryDefinition.class));
    }

    @Override
    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration) {
        if (buildConfiguration.containsKey(CONFIG_KEY)) {
            buildConfiguration.setProperty(CONFIG_KEY,
                    toList(buildConfiguration.getProperty(CONFIG_KEY)));
        }
    }

    static List<Long> toList(Object object) {
        String string = object.toString();
        if (string.equals("false") || string.equals("[]")) {
            return ImmutableList.of();
        }
        if (string.startsWith("[")) {
            string = string.substring(1, string.length() - 1); // trim '[' and ']'
        }
        ImmutableList<String> strings = ImmutableList.copyOf(Splitter.on(", ").split(string));
        return Lists.transform(strings, new Function<String, Long>() {
            @Override
            public Long apply(String input) {
                return Long.parseLong(input);
            }
        });
    }

    static List<GitHubRepository> ghReposFor(ImmutablePlan plan) {
        ArrayList<GitHubRepository> repos = Lists.newArrayList();
        for (RepositoryDefinition repositoryDefinition : ghRepoDefinitionsFor(plan)) {
            final GitHubRepository repository = Narrow.downTo(repositoryDefinition.getRepository(),
                    GitHubRepository.class);
            if (repository != null) {
                repos.add(repository);
            }
        }

        return repos;
    }

    static List<RepositoryDefinition> ghRepoDefinitionsFor(ImmutablePlan plan) {
        return ImmutableList.copyOf(Iterables.filter(
                ((ImmutableChain) plan).getEffectiveRepositoryDefinitions(),
                new Predicate<RepositoryDefinition>() {
                    @Override
                    public boolean apply(RepositoryDefinition input) {
                        return input.getRepository() instanceof GitHubRepository;
                    }
                }));
    }

}
