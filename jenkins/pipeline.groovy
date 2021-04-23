#!groovy

pipeline {
    agent any 
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '5'))
    }
    stages {
        stage('clone') {
            steps {
                checkout([$class: 'GitSCM', 
                    branches: [[name: 'jenkins']], 
                    extensions: [], 
                    userRemoteConfigs: [[url: 'https://github.com/eficode/cicd-dependency-scan-demo.git']]]
                )
            }
        }
        stage('DotNet') {
            parallel {
                /*
                    OWASP open source dependency check cli script. (https://owasp.org/www-project-dependency-check/)
                    See Jenkins Dockerfile for install.
                    See dependency-check.sh -h for commands and options.

                    - Use --format for output. HTML, XML, CSV, JSON, JUNIT, SARIF, or ALL(default)
                    - Use --out to define path to output file.
                    - Use Jenkins plugin for publishing results in Jenkins. (Recommended)
                */
                stage('OWASP-DP') {
                    steps {
                        // Running parallel so need separate buil directories
                        sh 'mkdir owasp-build && cp -r example-projects/eShopOnWeb/* owasp-build/'
                        
                        // Todo: reenable when NVD db issue is fixed.
                        //dir("owasp-build") {
                        //    // Have to build it before scanning
                        //    sh 'dotnet build eShopOnWeb.sln'
                        //    // Now we can scan
                        //    sh 'dependency-check.sh --project "eShopOnWeb" --scan ./ -f XML --out dependency-check-dotnet.xml'
                        //    dependencyCheckPublisher pattern: 'dependency-check-dotnet.xml', 
                        //        failedNewCritical: 1,
                        //        failedNewHigh: 1,
                        //        failedTotalCritical: 23,
                        //        failedTotalHigh: 127,
                        //        unstableTotalCritical: 10,
                        //        unstableTotalHigh: 100,
                        //        unstableTotalMedium: 25
                        //    
                        //    archiveArtifacts artifacts: "**/dependency-check-*.xml", followSymlinks: false
                        //}
                    }
                }
                /*  
                    Snyk can be used to check dependencies for .Net. (https://support.snyk.io/hc/en-us/articles/360004519138-Snyk-for-NET)
                    There is a free plan with 200 tests per month limit. The free plan misses out on a lot of cool features but is a good way to get started.
                    NOTE: It is possible that it can start failing if heavily misused.  
                    
                    You may need to build or publish prior to running snyk dependeing on the project.

                    - Use --severity-threshold to filter vulnerabilities. low, medium, high
                    - Use --fail-on to filter on whether the vulnerbaility is upgradable or patchable
                    - Use `snyk-linux test` to execute vulnerability tests locally.
                    - Use `snyk-linux monitor` to upload to Dashboard and get notified of new vulnerabilities. 
                */
                stage('Snyk') {
                    steps {
                        // Running parallel so need separate buil directories
                        sh 'mkdir snyk-build && cp -r example-projects/eShopOnWeb/* snyk-build/'

                        dir("snyk-build") {
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
                    See dependency-check.sh -h for commands and options.
                    - Use --format for output. HTML, XML, CSV, JSON, JUNIT, SARIF, or ALL(default)
                    - Use --out to define path to output file.
                    - Use Jenkins plugin for publishing results in Jenkins. (Recommended)
                */
                stage('OWASP-DP') {
                    steps {
                        sh 'echo hello world'
                        // Todo: reenable when NVD db issue is fixed.
                        dir("example-projects/node-example-app") {

                            sh 'dependency-check.sh --project "node-example-app" --scan ./ -f XML --out dependency-check-nodejs.xml'
                            
                            // Jenkins plugin can be used to publish the report.
                            dependencyCheckPublisher pattern: 'dependency-check-nodejs.xml', 
                                failedNewCritical: 1,
                                failedNewHigh: 1,
                                failedTotalCritical: 23,
                                failedTotalHigh: 127,
                                unstableTotalCritical: 10,
                                unstableTotalHigh: 100,
                                unstableTotalMedium: 25
                            
                            archiveArtifacts artifacts: 'dependency-check-nodejs.xml',followSymlinks: false
                        }
                    }
                }
                /*  
                    Snyk can be used to check dependencies for NodeJs. 
                    There is a free plan with 200 tests per month limit. The free plan misses out on a lot of cool features but is a good way to get started.
                    NOTE: It is possible that it can start failing if heavily misused.
                    - Use --severity-threshold to filter vulnerabilities. low, medium, high
                    - Use --fail-on to filter on whether the vulnerbaility is upgradable or patchable
                    - Use `snyk test` to execute vulnerability tests locally.
                    - Use `snyk monitor` to upload to Dashboard and get notified of new vulnerabilities. 
                */
                stage('Snyk') {
                    steps {
                        dir("example-projects/node-example-app") {
                            // You can use jenkins snyk plugin.
                            snykSecurity failOnIssues: false, projectName: 'node-example-app', snykInstallation: 'snyk', snykTokenId: 'token-snyk'
                            
                            // You can use snyk cli also. See Jenkins Docker file for installation.
                            //sh 'snyk test --severity-threshold=high --fail-on=upgradable'
                        }
                    }
                }
                /*
                    Yarn and NPM node package managers.
                    Provides audit checking dependency vulnerabilities.
                    See npm/yarn audit -h for options.
                    You can install npm-audit-html/yarn-audit-html to generate html reports. `npm -install -g npm-audit-html|yarn-audit-html`.
                    
                    - npm audits exit code will return non zero and fail the build if any issues are found.
                    - Yarn audits exit code is a mask and will return non zero and fail the build if any issues are found. (https://classic.yarnpkg.com/en/docs/cli/audit/)
                    - Use the --json option to format output in json and pipe the to npm-audit-html/yarn-audit-html to produce a publishable html report.

                    You can install audit-ci which wraps yarn and npm audit to be more CI friendly. `npm install -g audit-ci`.
                    NOTE: The JSON output of audit-ci is NOT compatible with npm-audit-html. An issue has been opened. (https://github.com/IBM/audit-ci/issues/174)
                    
                    - Use audit-ci to determine when to fail based on vulnerability severity (low,moderate,high,critical).
                    - Use audit-ci with NO severity defined and the build will not fail regardless of the number and level of severities found.
                */
                stage('NPM/Yarn Audit')
                {
                    steps {
                        // We are forcing all executions to true to continue pipeline.
                        dir("example-projects/node-example-app") {
                            
                            sh 'mkdir -p results/npm-audit && mkdir -p results/yarn-audit' // Somehwere for reports
                            
                            sh 'npm install'
                            sh 'npm audit --json | npm-audit-html || true' // Generate a report
                            sh 'npm audit || true' // Some output to the console
                            sh 'audit-ci --critical --package-manager yarn -s || true' // audit-ci will only fail if 1 or more critical issues are found

                            sh 'yarn install'
                            sh 'yarn audit --json | yarn-audit-html || true' // Generate a report
                            sh 'yarn audit || true' // Some output to the console
                            sh 'audit-ci --critical --package-manager yarn -s || true' // audit-ci will only fail if 1 or more critical issues are found

                            archiveArtifacts artifacts: '*-audit.html',followSymlinks: false
                        }
                    }
                }
            }
        }
        stage('Java') {
            steps {
                sh "echo Add something"
            }
        }
    }
    post {
        success {
            sh "echo Do something on success!"
        }
        unstable {
            sh "echo Do something on unstable!"
        }
        failure {
            sh "echo Do something on failure!"
        }
        always {
            // Publish NPM and Yarn audit reports
            publishHTML (target : [allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'example-projects/node-example-app',
            reportFiles: 'npm-audit.html,yarn-audit.html',
            reportName: 'Audits',
            reportTitles: 'NPM Audit, Yarn Audit'])
            // Clean up non committed files.
            sh "git clean -fdx"
        }
    }
}