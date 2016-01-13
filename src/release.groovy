import groovy.json.JsonSlurper

{ ->
    node {
        git url: 'git@github.com:gravitee-io/release.git', branch: "master"
        def releaseJSON = readFile encoding: 'UTF-8', file: 'release.json'
        dryRun = Boolean.valueOf(dryRun)
        if (dryRun) {
            println("\n    ##################################" +
                    "\n    #                                #" +
                    "\n    #          DRY RUN MODE          #" +
                    "\n    #                                #" +
                    "\n    ##################################")
        }

        GraviteeIO graviteeIO = new GraviteeIO(parseJson(releaseJSON))

        Component[] componentsToRelease = filteredComponentsToRelease(graviteeIO)
        releaseComponents(componentsToRelease, graviteeIO.buildDependencies, dryRun)
    }
}()

@NonCPS
def parseJson(json) {
    def jsonSlurper = new JsonSlurper()
    def obj = jsonSlurper.parseText(json)
    obj
}

def filteredComponentsToRelease(GraviteeIO graviteeIO) {
    def snapshots = []
    def snapshotsPrintList = "\n"
    for ( int i = 0; i < graviteeIO.components.size(); i++ ) {
        if ( graviteeIO.components[i].version.snapshot ) {
            snapshots.add(graviteeIO.components[i])
            snapshotsPrintList += "     - ${graviteeIO.components[i].name} (${graviteeIO.components[i].version.toString()})\n"
        }
    }
    println("\n    ${snapshots.size()} components need to be released : ${snapshotsPrintList} \n")
    if ( !dryRun ) {
        input "\n    Do you want to release them ?"
    }
    snapshots
}

def releaseComponents(componentsToRelease, buildDependencies, dryRun) {
    if ( componentsToRelease == null || componentsToRelease.size() == 0 )
        return

    def componentsToReleaseAsMap = new LinkedHashMap()
    for ( int i = 0; i < componentsToRelease.size(); i++ ) {
        componentsToReleaseAsMap.put(componentsToRelease[i].name, componentsToRelease[i])
    }

    def parallelBuildGroup = new ArrayList()
    for ( int i = 0; i < buildDependencies.size(); i++ ) {
        def components = new ArrayList()
        for ( int j = 0; j < buildDependencies[i].size(); j++ ) {
            if ( componentsToReleaseAsMap.containsKey(buildDependencies[i][j]) ) {
                components.add(componentsToReleaseAsMap[buildDependencies[i][j]])
            }
        }
        if ( components.size() > 0 ) {
            parallelBuildGroup.add(components)
        }
    }

    for ( int i = 0; i < parallelBuildGroup.size(); i++ ) {
        releaseMavenProject(parallelBuildGroup[i], dryRun)
    }
}

def releaseMavenProject(components, dryRun) {
    def componentListToPrint = ""
    def parallelBuild = [:]

    for ( int i = 0; i < components.size(); i++ ) {
        def c = components[i]
        componentListToPrint += "\n    - ${c.name}"

        parallelBuild[c.name] = {
            def scmUrl = "git@github.com:gravitee-io/${c.name}.git"
            def scmBranch = "master"
            node {
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

class GraviteeIO implements Serializable {
    Version version
    def components = []
    String[][] buildDependencies

    GraviteeIO(jsonObj) {
        this.version = new Version(jsonObj.version)
        this.buildDependencies = jsonObj.buildDependencies
        if ( jsonObj.components ) {
            for ( int i = 0; i < jsonObj.components.size(); i++ ) {
                components.add(new Component(jsonObj.components[i]))
            }
        }
    }
}

class Component implements Serializable {
    String name
    Version version

    Component(jsonObj) {
        this.name = jsonObj.name
        this.version = new Version(jsonObj.version)
    }
}

class Version implements Serializable {

    String version
    Integer major
    Integer minor
    Integer fix
    Boolean snapshot

    Version(versionAsString) {
        this.version = versionAsString
        this.snapshot = versionAsString.contains("-SNAPSHOT")
        def semver = versionAsString.split("-SNAPSHOT")[0].split("\\.")
        this.major= semver[0] as Integer
        this.minor= semver[1] as Integer
        this.fix= semver[2] as Integer
    }

    @Override
    String toString() {
        version
    }

    def releaseVersion() {
        def v = major + "." + minor + "." + fix
        v
    }

    def nextMajorSnapshotVersion() {
        def v = (major + 1) + "." + minor + "." + fix + "-SNAPSHOT"
        v
    }

    def nextMinorSnapshotVersion() {
        def v = major + "." + (minor + 1) + "." + fix + "-SNAPSHOT"
        v
    }

    def nextFixSnapshotVersion() {
        def v = major + "." + minor + "." + (fix + 1) + "-SNAPSHOT"
        v
    }

}
