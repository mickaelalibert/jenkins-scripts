node {
    sh "rm -rf *"
    sh "rm -rf .git"
    checkout([
            $class: 'GitSCM',
            branches: [[name: '*/master']],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[$class: 'LocalBranch', localBranch: 'master']],
            submoduleCfg: [],
            userRemoteConfigs: [[credentialsId: '31afd483-f394-439f-b865-94c413e6465f', url: 'git@github.com:gravitee-io/issues.git']]])

    sh "docker run --rm --env MILESTONE_VERSION='${MILESTONE_VERSION}' -v '$WORKSPACE':/data graviteeio/changelog"

    echo readFile("CHANGELOG.adoc")

    sh "git add --update"
    replacement = "${MILESTONE_VERSION}".replace(" ", "_")
    sh "git commit -m \"Generate changelog for version ${replacement}\""
    sh "git tag \"${replacement}\""

    if (!Boolean.valueOf(dryRun)) {
        sh "git push --tags origin master"
    }
}
