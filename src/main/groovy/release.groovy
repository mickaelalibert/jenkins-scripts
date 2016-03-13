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

        Graviteeio graviteeio = new Graviteeio(parseJson(releaseJSON))
        def MavenReleaser = fileLoader.fromGit(
                'src/main/groovy/releasemaven',
                'https://github.com/gravitee-io/jenkins-scripts.git',
                'master',
                null,
                '')

        Component[] componentsToRelease = filteredComponentsToRelease(graviteeio)
        releaseComponents(componentsToRelease, graviteeio.buildDependencies, MavenReleaser, dryRun)
    }
}()

@NonCPS
def parseJson(json) {
    def jsonSlurper = new JsonSlurper()
    def obj = jsonSlurper.parseText(json)
    obj
}

def filteredComponentsToRelease(Graviteeio graviteeIO) {
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

def releaseComponents(componentsToRelease, buildDependencies, MavenReleaser, dryRun) {
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
        MavenReleaser.release(parallelBuildGroup[i], dryRun)
    }
}


class Graviteeio implements Serializable {
    Version version
    def components = []
    String[][] buildDependencies

    Graviteeio(jsonObj) {
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
        def v = (major + 1) + ".0.0-SNAPSHOT"
        v
    }

    def nextMinorSnapshotVersion() {
        def v = major + "." + (minor + 1) + ".0-SNAPSHOT"
        v
    }

    def nextFixSnapshotVersion() {
        def v = major + "." + minor + "." + (fix + 1) + "-SNAPSHOT"
        v
    }

}