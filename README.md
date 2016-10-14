# bamboo-github-status
A plugin for Atlassian Bamboo that reports build results to the GitHub status API

Once this plugin is installed, your Bamboo plans will start automatically
reporting [commit statuses](https://developer.github.com/v3/repos/statuses/).
There is no configuration, though you can disable the plugin on a per-plan
basis from the Miscellaneous tab. It defaults to enabled.

GitHub Enterprise users can set an ATLASSIAN_BAMBOO_GITHUB_API_BASE_URL
environment variable in the Bamboo startup script to get the GitHub repository
type (and this plugin) to work.

NOTE: Make sure that the user that Bamboo is using to check out your
repositories also has write permissions, otherwise it will not be able to
create commit statuses.
