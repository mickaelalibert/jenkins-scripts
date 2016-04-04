def updateReleaseJson(json, tag, dryRun) {
    node {
        ws {
            withEnv(["HOME=/root"]) {
                sh "rm -rf *"
                sh "rm -rf .git"
                git url: 'git@github.com:gravitee-io/release.git', branch: "master"

                stage "Update release.json"
                println(json)
                writeFile file: 'release.json', text: json

                sh "git add --update"
                if (tag != null) {
                    sh "git commit -m 'release(${tag})'"
                    stage "Tag release.json ${tag}"
                    sh "git tag ${tag}"
                } else {
                    sh "git commit -m 'prepareRelease(): update version'"
                }

                if (!dryRun) {
                    sh "git push --tags origin master"
                }
            }
        }

    }
}

return this