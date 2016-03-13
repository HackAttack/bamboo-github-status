[#if gitHubRepositories?has_content]
    [@ui.bambooSection titleKey='com.mhackner.bamboo-github-status.heading'
                       descriptionKey='com.mhackner.bamboo-github-status.repositories']
        [@ww.checkboxlist name='custom.gitHubStatus.repositories'
                          list=gitHubRepositories
                          nameValue=buildConfiguration.getProperty('custom.gitHubStatus.repositories')
                          listKey='id'
                          listValue='name' /]
    [/@ui.bambooSection]
[/#if]
