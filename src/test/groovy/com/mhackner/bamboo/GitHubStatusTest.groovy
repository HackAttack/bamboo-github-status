package com.mhackner.bamboo

import spock.lang.Specification

import com.atlassian.bamboo.build.BuildDefinition
import com.atlassian.bamboo.plan.cache.ImmutableChain
import com.atlassian.bamboo.plugins.git.GitHubRepository
import com.atlassian.bamboo.repository.RepositoryDefinition

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

}
