def release(components, dryRun) {
    def componentListToPrint = ""
    def parallelBuild = [:]

    for ( int i = 0; i < components.size(); i++ ) {
        def c = components[i]
        componentListToPrint += "\n    - ${c.name}"

        parallelBuild[c.name] = {
            def scmUrl = "git@github.com:gravitee-io/${c.name}.git"
            def scmBranch = "master"
            node {
                stage "${c.name} v${c.version.releaseVersion()}"
                println("\n    scmUrl         = ${scmUrl}" +
                        "\n    scmBranch      = ${scmBranch}" +
                        "\n    releaseVersion = ${c.version.releaseVersion()}" +
                        "\n    nextSnapshot   = ${c.version.nextMinorSnapshotVersion()}")

                sh 'rm -rf *'
                sh 'rm -rf .git'

                def mvnHome = tool 'Maven 3.2.2'
                def javaHome = tool 'JDK 8'
                def nodeHome = tool 'NodeJS 0.12.4'

                withEnv(["PATH+MAVEN=${mvnHome}/bin",
                        "PATH+NODE=${nodeHome}/bin",
                        "HOME=/root",
                        "M2_HOME=${mvnHome}",
                        "JAVA_HOME=${javaHome}"]) {

                    checkout([
                            $class: 'GitSCM',
                            branches: [[
                                    name: "${scmBranch}"
                            ]],
                            doGenerateSubmoduleConfigurations: false,
                            extensions: [[
                                    $class: 'LocalBranch',
                                    localBranch: "${scmBranch}"
                            ]],
                            submoduleCfg: [],
                            userRemoteConfigs: [[
                                    credentialsId: 'ce78e461-eab0-44fb-bc8d-15b7159b483d',
                                    url: "${scmUrl}"
                            ]]
                    ])

                    // set version
                    sh "mvn -B versions:set -DnewVersion=${c.version.releaseVersion()} -DgenerateBackupPoms=false"

                    // use release version of each -SNAPSHOT gravitee artifact
                    sh "mvn -B -U versions:update-properties -Dincludes=io.gravitee.*:* -DgenerateBackupPoms=false"

                    sh "cat pom.xml"
                    // deploy
                    if ( dryRun ) {
                        sh "mvn -B -U clean install"
                        sh "mvn enforcer:enforce"
                    } else {
                        sh "mvn -B -U -P gravitee-release clean deploy"
                    }

                    // commit, tag the release
                    sh "git add --update"
                    sh "git commit -m 'release(${c.version.releaseVersion()})'"
                    sh "git tag ${c.version.releaseVersion()}"

                    // update next version
                    sh "mvn -B versions:set -DnewVersion=${c.version.nextMinorSnapshotVersion()} -DgenerateBackupPoms=false"

                    // commit, tag the snapshot
                    sh "git add --update"
                    sh "git commit -m 'chore(): Prepare next version'"

                    // push
                    if ( !dryRun ) {
                        sh "git push --tags origin ${scmBranch}"
                    }
                }
            }
        }
    }

    println("    Release ${components.size()} components in parallel : ${componentListToPrint} \n")
    parallel parallelBuild

}

return this