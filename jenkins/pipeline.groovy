#!groovy

pipeline {
    agent any
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '5'))
    }
    stages {
        stage('Prepare') {
            steps {
                checkout([$class: 'GitSCM',
                    branches: [[name: 'main']],
                    extensions: [],
                    userRemoteConfigs: [[url: 'https://github.com/eficode/cicd-dependency-scan-demo.git']]])

                // General reports driectory
                sh "mkdir ${WORKSPACE}/reports"

                // Running stuff in parallel so best with separate work directories
                sh 'mkdir -p owasp-dotnet && cp -r example-projects/eShopOnWeb/* owasp-dotnet/'
                sh 'mkdir snyk-dotnet && cp -r example-projects/eShopOnWeb/* snyk-dotnet/'

                sh 'mkdir -p owasp-js && cp -r example-projects/node-example-app/* owasp-js/'
                sh 'mkdir -p snyk-js && cp -r example-projects/node-example-app/* snyk-js/'
                sh 'mkdir -p package-managers-js && cp -r example-projects/node-example-app/* package-managers-js/'
            }
        }
        stage('DotNet') {
            parallel {
                /*
                    OWASP open source dependency check cli script. (https://owasp.org/www-project-dependency-check/)
                    See Jenkins Dockerfile for install.
                    Jenkins Plugin: https://plugins.jenkins.io/dependency-check-jenkins-plugin/

                    See dependency-check.sh -h for commands and options.
                    - Use --format for output. HTML, XML, CSV, JSON, JUNIT, SARIF, or ALL(default)
                    - Use --out to define path to output file or directory.
                    - Use Jenkins plugin for publishing results in Jenkins and createing a THRESHOLD. (Recommended)
                */
                stage('OWASP-DC') {
                    steps {
                        dir('owasp-dotnet') {
                            // Have to build it before scanning
                            sh 'dotnet build eShopOnWeb.sln'

                            // Scan and generate an html report
                            sh 'dependency-check.sh --project "eShopOnWeb" --scan ./ --out eShopOnWeb-dc.html --format HTML'
                            sh "mv eShopOnWeb-dc.html ${WORKSPACE}/reports"

                            // If you want to threshold then you need to generate an xml report.
                            sh 'dependency-check.sh --project "eShopOnWeb" --scan ./ --out eShopOnWeb-dc.xml --format XML'
                            sh "mv eShopOnWeb-dc.xml ${WORKSPACE}/reports"
                        }
                        // Use the publisher to define a threshhold
                        dependencyCheckPublisher pattern: '**/eShopOnWeb-dc.xml',
                                failedNewCritical: 1,
                                failedNewHigh: 1,
                                failedTotalCritical: 23,
                                failedTotalHigh: 127,
                                unstableTotalCritical: 10,
                                unstableTotalHigh: 100,
                                unstableTotalMedium: 25
                    }
                }
                /*
                    Snyk can be used to check dependencies for .Net. (https://support.snyk.io/hc/en-us/articles/360004519138-Snyk-for-NET)
                    There is a free plan with 200 tests per month limit. The free plan misses out on a lot of cool features but is a good way to get started.
                    Jenkins plugin: https://plugins.jenkins.io/snyk-security-scanner/

                    You may need to build or publish prior to running snyk dependeing on the project.

                    - Use --severity-threshold to filter vulnerabilities (low, medium, high).
                    - Use --fail-on to filter on whether to fail build if vulnerbaility is upgradable or patchable.
                    - Use `snyk-linux test` to execute vulnerability tests locally.
                    - Use `snyk-linux monitor` to upload to Dashboard and get notified of new vulnerabilities.
                */
                stage('Snyk') {
                    steps {
                        dir('snyk-dotnet') {
                            // Restore the dependencies
                            sh 'dotnet build eShopOnWeb.sln'
                            // If you have the Jenkins snyk plugin installed then it can be used as below.
                            snykSecurity additionalArguments: '--project-name-prefix=eShopOnWeb/',
                                failOnIssues: false,
                                monitorProjectOnBuild: false,
                                severity: 'high',
                                snykInstallation: 'snyk',
                                snykTokenId: 'token-snyk',
                                targetFile: 'eShopOnWeb.sln'
                            
                            sh "cp snyk_report.html ${WORKSPACE}/reports/eShopOnWeb-snyk-report.html"

                            sh "cp snyk_report.html ${WORKSPACE}/reports/eShopOnWeb-snyk-report.html"

                        // You can also execute snyk from the command line as below.
                        // sh 'snyk-linux test --severity-threshold=high --fail-on=upgradable --file=eShopOnWeb.sln'
                        }
                    }
                }
            }
        }
        stage('JavaScript') {
            parallel {
                /*
                    OWASP open source dependency check cli script. (https://owasp.org/www-project-dependency-check/)
                    See Jenkins Dockerfile for install.
                    Jenkins Plugin: https://plugins.jenkins.io/dependency-check-jenkins-plugin/

                    See dependency-check.sh -h for commands and options.
                    - Use --format for output. HTML, XML, CSV, JSON, JUNIT, SARIF, or ALL(default)
                    - Use --out to define path to output file or directory.
                    - Use Jenkins plugin for publishing results in Jenkins and createing a THRESHOLD. (Recommended)
                */
                stage('OWASP-DC') {
                    steps {
                        dir('owasp-js') {
                            sh 'dependency-check.sh --project "node-example-app" --scan ./ --out js-dc.html --format HTML'
                            sh "mv js-dc.html ${WORKSPACE}/reports"
                        }
                    }
                }
                /*
                    Snyk can be used to check dependencies for Java Script.
                    There is a free plan with 200 tests per month limit. The free plan misses out on a lot of cool features but is a good way to get started.
                    Jenkins plugin: https://plugins.jenkins.io/snyk-security-scanner/

                    - Use --severity-threshold to filter vulnerabilities (low, medium, high).
                    - Use --fail-on to filter on whether to fail build if vulnerbaility is upgradable or patchable.
                    - Use `snyk-linux test` to execute vulnerability tests locally.
                    - Use `snyk-linux monitor` to upload to Dashboard and get notified of new vulnerabilities.
                */
                stage('Snyk') {
                    steps {
                        dir('snyk-js') {
                            // You can use jenkins snyk plugin.
                            snykSecurity failOnIssues: false,
                                projectName: 'node-example-app',
                                severity: 'high',
                                monitorProjectOnBuild: false,
                                snykInstallation: 'snyk',
                                snykTokenId: 'token-snyk'

                        sh "cp snyk_report.html ${WORKSPACE}/reports/js-snyk-report.html"

                        // You can use snyk cli also. See Jenkins Docker file for installation.
                        //sh 'snyk test --severity-threshold=high --fail-on=upgradable'
                        }
                    }
                }
                /*
                    Yarn and NPM node package managers provides audit checking dependency vulnerabilities. See npm/yarn audit -h for options.
                    Versions used here are `npm 6.14.12` and `yarn 1.22.10`.

                    You can install npm-audit-html/yarn-audit-html to generate html reports. `npm -install -g npm-audit-html|yarn-audit-html`.

                        - npm audits exit code will return non zero and fail the build if any issues are found.
                        - Yarn audits exit code is a mask and will return non zero and fail the build if any issues are found. (https://classic.yarnpkg.com/en/docs/cli/audit/)
                        - Use the --json option to format output in json and pipe the to npm-audit-html/yarn-audit-html to produce a publishable html report.

                    You can install audit-ci which wraps yarn and npm audit to be more CI friendly. `npm install -g audit-ci`.
                    NOTE: The JSON output of audit-ci is NOT compatible with npm-audit-html. An issue has been opened. (https://github.com/IBM/audit-ci/issues/174)

                        - Use audit-ci to determine when to fail based on vulnerability severity (low,moderate,high,critical).
                        - Use audit-ci with NO severity defined and the build will not fail regardless of the number and level of severities found.

                    If you have Next Generation Warnings plugin installed AND you configure a simple Groovy parser(see Jenkins.yml in config directory)
                    you can create threshHolds based on number of current issues AND severity for a more concise threshhold.
                */
                stage('NPM/Yarn Audit')
                {
                    steps {
                        // We are forcing all executions to true to continue pipeline.
                        dir('package-managers-js') {
                            sh 'npm install'
                            sh 'npm audit --json | npm-audit-html || true' // Generate a report
                            sh "mv npm-audit.html ${WORKSPACE}/reports"
                            sh 'npm audit || true' // Some output to the console
                            sh 'audit-ci --critical --package-manager yarn || true' // audit-ci will only fail if 1 or more critical issues are found

                            sh 'yarn install'
                            sh 'yarn audit --json | yarn-audit-html || true' // Generate a report
                            sh "mv yarn-audit.html ${WORKSPACE}/reports"
                            sh 'yarn audit || true' // Some output to the console
                            sh 'audit-ci --critical --package-manager yarn || true' // audit-ci will only fail if 1 or more critical issues are found

                            // Generate a report file with the parseable option.
                            sh 'npm audit --parseable > npm-audit-ng.txt || true'
                            sh "mv npm-audit-ng.txt ${WORKSPACE}/reports"
                        }
                        // Use the created groovy parser to threshhold the issues with Next Generation Warnings plugin.
                        recordIssues(
                            tool: groovyScript(parserId: 'npm-audit-parser', pattern: '**/npm-audit-ng.txt'),
                            qualityGates: [[threshold: 100, type: 'TOTAL', unstable: true]])
                    }
                }
            }
        }
        stage('Java') {
            steps {
                sh 'echo Add something'
            }
        }
    }
    post {
        success {
            sh 'echo Do something on success!'
        }
        unstable {
            sh 'echo Do something on unstable!'
        }
        failure {
            sh 'echo Do something on failure!'
        }
        always {
            // Publish NPM and Yarn audit reports
            publishHTML (target : [allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'reports',
                reportFiles: 'npm-audit.html,yarn-audit.html',
                reportName: 'NPM/Yarn Reports',
                reportTitles: 'NPM Audit, Yarn Audit'])

            // Publish Dependency Check reports
            publishHTML (target : [allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'reports',
                reportFiles: 'eShopOnWeb-dc.html,js-dc.html',
                reportName: 'Dependency Check Reports',
                reportTitles: 'eShopOnWeb, Node Example App'])

            // Snyk plugin publishes reports by default. If you use CLI you can do this.
            publishHTML (target : [allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'reports',
                reportFiles: 'eShopOnWeb-snyk-report.html,js-snyk-report.html',
                reportName: 'Snyk Reports',
                reportTitles: 'eShopOnWeb, Node Example App'])

            // Archive all reports
            archiveArtifacts artifacts: 'reports/*', followSymlinks: false

            // Clean up
            sh 'git clean -fdx'
        }
    }
}
