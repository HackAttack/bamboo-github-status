[#if gitHubRepositories?has_content]
    [@ui.bambooSection titleKey='com.mhackner.bamboo-github-status.heading'
                       descriptionKey='com.mhackner.bamboo-github-status.repositories']
        [#list gitHubRepositories as repository]
            [@ww.checkbox labelKey="${repository.name}" label="${repository.name}" toggle=true name="custom.gitHubStatus.repositories.id_${repository.id}"
            fieldValue=true/]
        [/#list]
    [/@ui.bambooSection]
[/#if]
