#!groovy

pipelineJob("dependency-checks") {

    description("This job demo's different dependency scanning technologies.")

    disabled(false)
    keepDependencies(false)

    triggers {
        githubPush()
    }

    definition {
        
        cps {
            sandbox()
        }

        cpsScm {
            scm {
                git {
                    remote {
                        name('origin')
                        url('https://github.com/eficode/cicd-dependency-scan-demo.git')
                    }
                    branches('*/jenkins')
                }
            }
            scriptPath("jenkins.yaml")
        }
    }
}

listView("Dependency Checks") {
    jobs {
        regex('(dependency-checks).*')
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
    }
}
