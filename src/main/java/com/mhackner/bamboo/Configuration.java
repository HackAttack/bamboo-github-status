package com.mhackner.bamboo;

import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanHelper;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.TopLevelPlan;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.plan.configuration.MiscellaneousPlanConfigurationPlugin;
import com.atlassian.bamboo.plugins.git.GitHubRepository;
import com.atlassian.bamboo.plugins.git.GitRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.v2.build.BaseBuildConfigurationAwarePlugin;
import com.atlassian.bamboo.vcs.configuration.PlanRepositoryDefinition;
import com.atlassian.bamboo.vcs.configuration.RepositoryPositionProvider;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Configuration extends BaseBuildConfigurationAwarePlugin
        implements MiscellaneousPlanConfigurationPlugin {

    static final String LIST_MODEL_NAME = "gitHubRepositories";
    static final String LIST_SELECTED_MODEL_NAME = "selectedGitHubRepositories";

    static final String CONFIG_KEY = "custom.gitHubStatus.repositories";

    static final String ID_PREFIX = "id_";

    /** Predicate that controls whether a repo is selected in the absence of any configuration. */
    static final Predicate<RepositoryPositionProvider> DEFAULT_REPO_PREDICATE =
            new Predicate<RepositoryPositionProvider>() {
                @Override
                public boolean apply(RepositoryPositionProvider input) {
                    return input.getPosition() == 0;
                }
            };

    private PlanManager planManager;

    public void setPlanManager(PlanManager planManager) {
        this.planManager = planManager;
    }

    @Override
    public boolean isApplicableTo(@NotNull ImmutablePlan immutablePlan) {
        return immutablePlan instanceof TopLevelPlan;
    }

    @Override
    public boolean isApplicableTo(Plan plan) {
        return plan instanceof TopLevelPlan;
    }

    @Override
    public void addDefaultValues(@NotNull BuildConfiguration buildConfiguration) {
        Plan plan = planManager.getPlanByKey(PlanKeys.getPlanKey(buildConfiguration.getString("buildKey")));
        PlanRepositoryDefinition repo = Iterables.find(getPlanRepositories(plan), DEFAULT_REPO_PREDICATE, null);
        if (repo != null) {
            buildConfiguration.setProperty(convertIdToPropertyName(repo.getId()), true);
        }
    }

    @Override
    public boolean isConfigurationMissing(@NotNull final BuildConfiguration buildConfiguration) {
        return !buildConfiguration.containsKey(CONFIG_KEY);
    }

    @Override
    protected void populateContextForEdit(@NotNull Map<String, Object> context,
                                          @NotNull BuildConfiguration buildConfiguration,
                                          Plan plan) {

        Set<Long> selectedIds = new HashSet<Long>();
        List<PlanRepositoryDefinition> repositoryOptions = new ArrayList<PlanRepositoryDefinition>();
        for (PlanRepositoryDefinition repositoryDefinition : getPlanRepositories(plan)) {
            repositoryOptions.add(repositoryDefinition);
            if (isRepositorySelected(buildConfiguration, repositoryDefinition.getId())) {
                selectedIds.add(repositoryDefinition.getId());
            }
        }
        context.put(
                LIST_MODEL_NAME,
                Iterables.toArray(repositoryOptions, PlanRepositoryDefinition.class)
        );
        context.put(LIST_SELECTED_MODEL_NAME, selectedIds);
    }

    @Override
    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration) {

        Set<Long> repositoryIds = getRepositoryIdsFromConfiguration(buildConfiguration);
        buildConfiguration.setProperty(CONFIG_KEY, convertPropertyIdsToCsv(repositoryIds));
    }

    static Set<Long> getRepositoryIdsFromConfiguration(BuildConfiguration buildConfiguration) {

        @SuppressWarnings("unchecked")
        String value = (String) buildConfiguration.getProperty(CONFIG_KEY);
        if (value != null && !value.isEmpty()) {
            return convertCsvToPropertyIds(value);
        } else {
            Set<Long> repositoryIds = new HashSet<Long>();
            for (Iterator<?> keys = buildConfiguration.getKeys(); keys.hasNext();) {
                @SuppressWarnings("unchecked")
                String propertyName = (String) keys.next();
                if (propertyName.startsWith(CONFIG_KEY)) {
                    repositoryIds.add(convertPropertyNameToId(propertyName));
                }
            }
            return repositoryIds;
        }
    }

    static Set<Long> convertCsvToPropertyIds(String csv) {
        return FluentIterable.from(StringUtils.commaDelimitedListToSet(csv))
                .transform(new Function<String, Long>() {
                    @Override
                    public Long apply(@Nullable String s) {
                        return Long.valueOf(s.trim());
                    }
                })
                .toSet();
    }

    static String convertPropertyIdsToCsv(Set<Long> propertyIds) {
        return StringUtils.collectionToCommaDelimitedString(propertyIds);
    }

    static List<PlanRepositoryDefinition> getPlanRepositories(ImmutablePlan plan) {

        List<PlanRepositoryDefinition> result = new ArrayList<PlanRepositoryDefinition>();
        for (PlanRepositoryDefinition repositoryDefinition : PlanHelper.getPlanRepositoryDefinitions(plan)) {
            Repository repository = repositoryDefinition.asLegacyData().getRepository();
            if (repository instanceof GitHubRepository || repository instanceof GitRepository) {
                result.add(repositoryDefinition);
            }
        }
        return result;
    }

    static boolean isRepositorySelected(BuildConfiguration buildConfiguration, long repositoryId) {
        String propertyName = convertIdToPropertyName(repositoryId);
        Object value = buildConfiguration.getProperty(propertyName);
        return value != null && Boolean.valueOf(value.toString());
    }

    static boolean isRepositorySelected(Map<String, ?> configuration, long repositoryId) {
        String propertyName = convertIdToPropertyName(repositoryId);
        Object value = configuration.get(propertyName);
        return value != null && Boolean.valueOf(value.toString());
    }

    static String convertIdToPropertyName(long repositoryId) {
        return String.format("%s.%s%d", CONFIG_KEY, ID_PREFIX, repositoryId);
    }

    static long convertPropertyNameToId(String propertyName) {
        String trimmed = propertyName.substring(CONFIG_KEY.length() + 1).replace(ID_PREFIX, "");
        return Long.parseLong(trimmed);
    }
}
