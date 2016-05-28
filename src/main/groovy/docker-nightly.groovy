import groovy.json.JsonSlurper

node() {
    git url: 'git@github.com:gravitee-io/release.git', branch: "master"
    def releaseJSON = readFile encoding: 'UTF-8', file: 'release.json'
    def jsonObj = parseJson(releaseJSON)
    def parallelBuild = [:]

    for ( int i = 0; i < jsonObj.components.size(); i++ ) {
        def name = jsonObj.components[i].name
        def version = jsonObj.components[i].version
        if (["gravitee-management-webui",
             "gravitee-gateway",
             "gravitee-management-rest-api"]
                .contains(name)) {
            parallelBuild[i] = {
                def path = null
                def imgName = ""
                if (name == "gravitee-management-webui") {
                    path = "images/management-ui"
                    imgName = "graviteeio/management-ui"
                } else if (name == "gravitee-management-rest-api") {
                    path = "images/management-api"
                    imgName = "graviteeio/management-api"
                } else if (name == "gravitee-gateway") {
                    path = "images/gateway"
                    imgName = "graviteeio/gateway"
                }

                node() {
                    print("\n build ${path} version ${version}")
                    git url: 'git@github.com:gravitee-io/gravitee-docker.git', branch: "issue/#1-update.docker.images"
                    sh "docker build --build-arg GRAVITEEIO_VERSION=${version} -t ${imgName}:nightly --pull=true -f ${path}/Dockerfile-nightly ${path}"
                    sh "docker push ${imgName}:nightly"
                }
            }
        }
    }
    jsonObj = null
    stage "Docker Build & Push"
    parallel parallelBuild
}

@NonCPS
def parseJson(json) {
    def jsonSlurper = new JsonSlurper()
    def obj = jsonSlurper.parseText(json)
    obj
}