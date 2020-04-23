import groovy.json.JsonSlurper

node() {
    git url: 'git@github.com:gravitee-io/release.git', branch: "master"
    def releaseJSON = readFile encoding: 'UTF-8', file: 'release.json'
    def jsonObj = parseJson(releaseJSON)
    def parallelBuild = [:]

    for ( int i = 0; i < jsonObj.components.size(); i++ ) {
        def name = jsonObj.components[i].name
        def scmUrl = "git@github.com:gravitee-io/${name}.git"
        parallelBuild[name] = {
            node {

            }
            //echo ( "\nChange parent version of ${name} to ${PARENT_VERSION}\nSCM: ${scmUrl} \n")
            /*ws {
                sh 'rm -rf *'
                sh 'rm -rf .git'

                checkout([
                        $class                           : 'GitSCM',
                        branches                         : [[
                                                                    name: "master"
                                                            ]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [[
                                                                    $class     : 'LocalBranch',
                                                                    localBranch: "master"
                                                            ]],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [[
                                                                    credentialsId: '31afd483-f394-439f-b865-94c413e6465f',
                                                                    url          : "${scmUrl}"
                                                            ]]
                ])

                def mvnHome = tool 'MVN33'
                def javaHome = tool 'JDK 8'
                withEnv(["PATH+MAVEN=${mvnHome}/bin",
                         "M2_HOME=${mvnHome}",
                         "JAVA_HOME=${javaHome}"]) {
                    sh "mvn -B versions:update-parent -DparentVersion=${PARENT_VERSION} -DgenerateBackupPoms=false"

                    sh "git add --update"
                    sh "git commit -m 'updateParent(${PARENT_VERSION})'"
                    // push
                    sh "git push origin master"
                }
            }*/
        }
    }
    parallel parallelBuild
}

@NonCPS
def parseJson(json) {
    def jsonSlurper = new JsonSlurper()
    def obj = jsonSlurper.parseText(json)
    obj
}