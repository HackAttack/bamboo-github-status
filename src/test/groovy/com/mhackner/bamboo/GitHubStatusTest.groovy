package com.mhackner.bamboo

import spock.lang.Specification

import com.atlassian.bamboo.build.BuildDefinition
import com.atlassian.bamboo.plan.AbstractChain
import com.atlassian.bamboo.plan.PlanManager
import com.atlassian.bamboo.plan.cache.ImmutableChain
import com.atlassian.bamboo.plugins.git.GitHubRepository
import com.atlassian.bamboo.repository.RepositoryDefinition
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration

class GitHubStatusTest extends Specification {

    def 'master plan updates with no config'() {
        ImmutableChain chain = Stub {
            hasMaster() >> false
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [:]
            }
        }
        RepositoryDefinition repo = Stub()

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
        RepositoryDefinition repo = Stub()

        expect:
        AbstractGitHubStatusAction.shouldUpdateRepo(chain, repo)
    }

    def 'master plan updates when configured'() {
        ImmutableChain chain = Stub {
            hasMaster() >> false
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [(Configuration.CONFIG_KEY): '123']
            }
        }
        RepositoryDefinition repo = Stub {
            getId() >> 123L
        }

        expect:
        AbstractGitHubStatusAction.shouldUpdateRepo(chain, repo)
    }

    def 'master plan doesn\'t update when not configured'() {
        ImmutableChain chain = Stub {
            hasMaster() >> false
            getBuildDefinition() >> Stub(BuildDefinition) {
                getCustomConfiguration() >> [(Configuration.CONFIG_KEY): '123']
            }
        }
        RepositoryDefinition repo = Stub {
            getId() >> 124L
        }

        expect:
        !AbstractGitHubStatusAction.shouldUpdateRepo(chain, repo)
    }

    def 'branch plan updates when master configured'() {
        RepositoryDefinition branchRepo = Stub {
            getName() >> 'name'
            getId() >> 123L
            getRepository() >> new GitHubRepository()
        }
        RepositoryDefinition masterRepo = Stub {
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
                getCustomConfiguration() >> [(Configuration.CONFIG_KEY): '124']
            }
        }

        expect:
        AbstractGitHubStatusAction.shouldUpdateRepo(chain, branchRepo)
    }

    def 'branch plan doesn\'t update when master repo has different name'() {
        RepositoryDefinition branchRepo = Stub {
            getName() >> 'name'
            getId() >> 123L
            getRepository() >> new GitHubRepository()
        }
        RepositoryDefinition masterRepo = Stub {
            getName() >> 'other name'
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
                getCustomConfiguration() >> [(Configuration.CONFIG_KEY): '124']
            }
        }

        when:
        AbstractGitHubStatusAction.shouldUpdateRepo(chain, branchRepo)

        then:
        thrown(NoSuchElementException)
    }

    def 'only default repo chosen with no config'() {
        RepositoryDefinition repo1 = Stub {
            getPosition() >> 0
        }
        RepositoryDefinition repo2 = Stub {
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
        RepositoryDefinition repo1 = Stub {
            getId() >> 123L
            getPosition() >> 0
            getRepository() >> new GitHubRepository()
        }
        RepositoryDefinition repo2 = Stub {
            getId() >> 124L
            getPosition() >> 1
            getRepository() >> new GitHubRepository()
        }
        AbstractChain chain = Stub {
            getEffectiveRepositoryDefinitions() >> [repo1, repo2]
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
        buildConfiguration.getProperty(Configuration.CONFIG_KEY) == [123L]
    }

}
