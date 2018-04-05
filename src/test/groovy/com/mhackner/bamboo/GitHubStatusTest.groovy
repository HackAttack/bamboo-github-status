package com.mhackner.bamboo

import com.atlassian.bamboo.build.BuildDefinition
import com.atlassian.bamboo.plan.AbstractChain
import com.atlassian.bamboo.plan.PlanManager
import com.atlassian.bamboo.plan.cache.ImmutableChain
import com.atlassian.bamboo.plugins.git.GitHubRepository
import com.atlassian.bamboo.repository.RepositoryData
import com.atlassian.bamboo.vcs.configuration.PlanRepositoryDefinition
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration
import spock.lang.Specification

class GitHubStatusTest extends Specification {

    def 'master plan updates with no config'() {
        ImmutableChain chain = Stub {
            hasMaster() >> false
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [:]
            }
        }
        PlanRepositoryDefinition repo = Stub()

        expect:
        AbstractGitHubStatusAction.shouldUpdateRepo(chain, repo)
    }

    def 'branch plan updates with no config'() {
        ImmutableChain chain = Stub {
            hasMaster() >> true
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [:]
            }
        }
        PlanRepositoryDefinition repo = Stub()

        expect:
        AbstractGitHubStatusAction.shouldUpdateRepo(chain, repo)
    }

    def 'master plan updates when configured'() {
        ImmutableChain chain = Stub {
            hasMaster() >> false
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [(Configuration.convertIdToPropertyName(123L)): 'true']
            }
        }
        PlanRepositoryDefinition repo = Stub {
            getId() >> 123L
        }

        expect:
        AbstractGitHubStatusAction.shouldUpdateRepo(chain, repo)
    }

    def 'master plan doesn\'t update when not configured'() {
        given:
        ImmutableChain chain = Stub {
            hasMaster() >> false
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [(Configuration.convertIdToPropertyName(123L)): 'true']
            }
        }
        PlanRepositoryDefinition repo = Stub {
            getPosition() >> 3
            getId() >> 124L
        }

        expect:
        !AbstractGitHubStatusAction.shouldUpdateRepo(chain, repo)
    }

    def 'branch plan updates when master configured'() {
        PlanRepositoryDefinition branchRepo = Stub {
            getName() >> 'name'
            getId() >> 123L
            getRepository() >> new GitHubRepository()
        }
        PlanRepositoryDefinition masterRepo = Stub {
            getName() >> 'name'
            getId() >> 124L
            getRepository() >> new GitHubRepository()
        }
        ImmutableChain chain = Stub {
            getEffectiveRepositoryDefinitions() >> [branchRepo]
            hasMaster() >> true
            getMaster() >> Stub(ImmutableChain) {
                getEffectiveRepositoryDefinitions() >> [masterRepo]
            }
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [(Configuration.convertIdToPropertyName(124L)): 'true']
            }
        }

        expect:
        AbstractGitHubStatusAction.shouldUpdateRepo(chain, branchRepo)
    }

    def 'branch plan doesn\'t update when master repo has different name'() {
        PlanRepositoryDefinition branchRepo = Stub {
            getName() >> 'name'
            getId() >> 123L
            getPosition() >> 1
            getRepository() >> new GitHubRepository()
        }
        PlanRepositoryDefinition masterRepo = Stub {
            getName() >> 'other name'
            getId() >> 124L
            getPosition() >> 0
            getRepository() >> new GitHubRepository()
        }
        ImmutableChain chain = Stub {
            getEffectiveRepositoryDefinitions() >> [branchRepo]
            hasMaster() >> true
            getMaster() >> Stub(ImmutableChain) {
                getEffectiveRepositoryDefinitions() >> [masterRepo]
            }
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [(Configuration.convertIdToPropertyName(124L)): 'true']
            }
        }

        expect:
        !AbstractGitHubStatusAction.shouldUpdateRepo(chain, branchRepo)
    }

    def 'only default repo chosen with no config'() {
        PlanRepositoryDefinition repo1 = Stub {
            getPosition() >> 0
        }
        PlanRepositoryDefinition repo2 = Stub {
            getPosition() >> 1
        }
        ImmutableChain chain = Stub {
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [:]
            }
        }

        expect:
        AbstractGitHubStatusAction.shouldUpdateRepo(chain, repo1)
        !AbstractGitHubStatusAction.shouldUpdateRepo(chain, repo2)
    }

    def 'only default repo selected in UI with no config'() {
        PlanRepositoryDefinition repo1 = Stub {
            getId() >> 123L
            getPosition() >> 0
            asLegacyData() >> Mock(RepositoryData) {
                getRepository() >> new GitHubRepository()
            }
        }
        PlanRepositoryDefinition repo2 = Stub {
            getId() >> 124L
            getPosition() >> 1
            asLegacyData() >> Mock(RepositoryData) {
                getRepository() >> new GitHubRepository()
            }
        }
        AbstractChain chain = Stub {
            getPlanRepositoryDefinitions() >> [repo1, repo2]
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [:]
            }
        }
        PlanManager planManager = Stub {
            getPlanByKey(_) >> chain
        }
        Configuration config = new Configuration()
        config.planManager = planManager
        BuildConfiguration buildConfiguration = new BuildConfiguration()
        buildConfiguration.setProperty('buildKey', 'BUILD-KEY')

        when:
        config.addDefaultValues(buildConfiguration)

        then:
        buildConfiguration.getProperty(Configuration.convertIdToPropertyName(123L)) == true
    }

}
