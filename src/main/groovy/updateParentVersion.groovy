package main.groovy

def projects = [
        "gravitee-common",
        "gravitee-definition",
        "gravitee-plugin-core",

        "gravitee-repository",
        "gravitee-repository-jpa",
        "gravitee-repository-mongodb",

        "gravitee-gateway",

        "gravitee-policy-apikey",
        "gravitee-policy-cors",
        "gravitee-policy-ratelimit",

        "gravitee-reporter-es",
        "gravitee-reporter-file",

        "gravitee-management-rest-api",
        "gravitee-management-webui",

        "gravitee-policy-maven-archetype",
        "json-schema-generator-maven-plugin",
        "gravitee-oauth2-server"
]

def updaters = [:]

for (int i = 0; i < projects.size(); i++) {
    def project = projects[i]
    updaters[project] = {
        node {
            dir (project) {
                def scmUrl = "git@github.com:gravitee-io/${project}.git"
                echo ( "\nChange parent version of ${project} to ${PARENT_VERSION}\nSCM: ${scmUrl} \n")
                sh 'rm -rf *'
                sh 'rm -rf .git'

                checkout([
                        $class: 'GitSCM',
                        branches: [[name: BRANCH]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [
                                [$class: 'CleanBeforeCheckout'],
                                [$class: 'LocalBranch', localBranch: BRANCH ]],
                        submoduleCfg: [],
                        userRemoteConfigs: [[
                                credentialsId: 'ce78e461-eab0-44fb-bc8d-15b7159b483d',
                                url: scmUrl ]]
                        ])

                def mvnHome = tool 'Maven 3.2.2'

                // set version
                sh "${mvnHome}/bin/mvn -B versions:update-parent -DparentVersion=${PARENT_VERSION} -DgenerateBackupPoms=false"

                withEnv(['HOME=/root']) {
                    sh "git add --update"
                    sh "git commit -m 'updateParent(${PARENT_VERSION})'"
                    // push
                    sh "git push origin ${BRANCH}"
                }
            }
        }
    }
}

parallel updaters
