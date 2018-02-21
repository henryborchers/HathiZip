#!groovy
@Library("ds-utils")
import org.ds.*

pipeline {
    agent {
        label "Windows&&DevPi"
    }
    options {
        disableConcurrentBuilds()  //each branch has 1 job running at a time
    }
    environment {
        mypy_args = "--junit-xml=mypy.xml"
        pytest_args = "--junitxml=reports/junit-{env:OS:UNKNOWN_OS}-{envname}.xml --junit-prefix={env:OS:UNKNOWN_OS}  --basetemp={envtmpdir}"
    }
    parameters {
        string(name: "PROJECT_NAME", defaultValue: "HathiTrust Zip for Submit", description: "Name given to the project")
        booleanParam(name: "UNIT_TESTS", defaultValue: true, description: "Run automated unit tests")
        booleanParam(name: "ADDITIONAL_TESTS", defaultValue: true, description: "Run additional tests")
        booleanParam(name: "PACKAGE", defaultValue: true, description: "Create a package")
        booleanParam(name: "DEPLOY_DEVPI", defaultValue: true, description: "Deploy to devpi on https://devpi.library.illinois.edu/DS_Jenkins/${env.BRANCH_NAME}")
        choice(choices: 'None\nRelease_to_devpi_only\nRelease_to_devpi_and_sccm\n', description: "Release the build to production. Only available in the Master branch", name: 'RELEASE')
        booleanParam(name: "UPDATE_DOCS", defaultValue: false, description: "Update online documentation")
        string(name: 'URL_SUBFOLDER', defaultValue: "hathi_zip", description: 'The directory that the docs should be saved under')
    }
    stages {

        stage("Cloning Source") {
            agent any

            steps {
                deleteDir()
                checkout scm
                stash includes: '**', name: "Source", useDefaultExcludes: false
            }

        }
        stage("Unit tests") {
            when {
                expression { params.UNIT_TESTS == true }
            }
            steps {
                node(label: "Windows") {
                    checkout scm
                    bat "${tool 'Python3.6.3_Win64'} -m tox -e pytest -- --junitxml=reports/junit-${env.NODE_NAME}-pytest.xml --junit-prefix=${env.NODE_NAME}-pytest --cov-report html:reports/coverage/ --cov=hathizip"
                    junit "reports/junit-${env.NODE_NAME}-pytest.xml"
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'reports/coverage', reportFiles: 'index.html', reportName: 'Coverage', reportTitles: ''])
                }
            }
        }
        stage("Additional tests") {
            when {
                expression { params.ADDITIONAL_TESTS == true }
            }

            steps {
                parallel(
                    "Documentation": {
                        node(label: "Windows") {
                            checkout scm
                            bat "${tool 'Python3.6.3_Win64'} -m tox -e docs"
                            script{
                                // Multibranch jobs add the slash and add the branch to the job name. I need only the job name
                                def alljob = env.JOB_NAME.tokenize("/") as String[]
                                def project_name = alljob[0]
                                dir('.tox/dist') {
                                    zip archive: true, dir: 'html', glob: '', zipFile: "${project_name}-${env.BRANCH_NAME}-docs-html-${env.GIT_COMMIT.substring(0,6)}.zip"
                                    dir("html"){
                                        stash includes: '**', name: "HTML Documentation"
                                    }
                                }
                            }
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '.tox/dist/html', reportFiles: 'index.html', reportName: 'Documentation', reportTitles: ''])
                        }
                    },
                    "MyPy": {
                    
                        node(label: "Windows") {
                            checkout scm
                            bat "call make.bat install-dev"
                            bat "venv\\Scripts\\mypy.exe -p hathizip --junit-xml=junit-${env.NODE_NAME}-mypy.xml --html-report reports/mypy_html"
                            junit "junit-${env.NODE_NAME}-mypy.xml"
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'reports/mypy_html', reportFiles: 'index.html', reportName: 'MyPy', reportTitles: ''])
                        }
                    }
                )
            }
        }
        stage("Packaging") {
            when {
                expression { params.PACKAGE == true }
            }

            steps {
                parallel(
                        "Source and Wheel formats": {
                            bat "call make.bat"
                        },
                        "Windows CX_Freeze MSI": {
                            node(label: "Windows") {
                                deleteDir()
                                checkout scm
                                bat "${tool 'Python3.6.3_Win64'} -m venv venv"
                                bat "make freeze"
                                dir("dist") {
                                    stash includes: "*.msi", name: "msi"
                                }

                            }
                            node(label: "Windows") {
                                deleteDir()
                                git url: 'https://github.com/UIUCLibrary/ValidateMSI.git'
                                unstash "msi"
                                bat "call validate.bat -i"
                                
                            }
                        },
                )
            }
            post {
              success {
                  dir("dist"){
                      unstash "msi"
                      archiveArtifacts artifacts: "*.whl", fingerprint: true
                      archiveArtifacts artifacts: "*.tar.gz", fingerprint: true
                      archiveArtifacts artifacts: "*.msi", fingerprint: true
                }
              }
            }

        }
        stage("Deploying to Devpi") {
            when {
                expression { params.DEPLOY_DEVPI == true && (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "dev") }
            }
            steps {
                bat "${tool 'Python3.6.3_Win64'} -m devpi use https://devpi.library.illinois.edu"
                withCredentials([usernamePassword(credentialsId: 'DS_devpi', usernameVariable: 'DEVPI_USERNAME', passwordVariable: 'DEVPI_PASSWORD')]) {
                    bat "${tool 'Python3.6.3_Win64'} -m devpi login ${DEVPI_USERNAME} --password ${DEVPI_PASSWORD}"
                    bat "${tool 'Python3.6.3_Win64'} -m devpi use /${DEVPI_USERNAME}/${env.BRANCH_NAME}_staging"
                    script {
                        bat "${tool 'Python3.6.3_Win64'} -m devpi upload --from-dir dist"
                        try {
                            bat "${tool 'Python3.6.3_Win64'} -m devpi upload --only-docs"
                        } catch (exc) {
                            echo "Unable to upload to devpi with docs."
                        }
                    }
                }

            }
        }
        stage("Test Devpi packages") {
            when {
                expression { params.DEPLOY_DEVPI == true && (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "dev") }
            }
            steps {
                parallel(
                        "Source Distribution: .tar.gz": {
                            script {
                                def name = bat(returnStdout: true, script: "@${tool 'Python3.6.3_Win64'} setup.py --name").trim()
                                def version = bat(returnStdout: true, script: "@${tool 'Python3.6.3_Win64'} setup.py --version").trim()
                                node("Windows") {
                                    withCredentials([usernamePassword(credentialsId: 'DS_devpi', usernameVariable: 'DEVPI_USERNAME', passwordVariable: 'DEVPI_PASSWORD')]) {
                                        bat "${tool 'Python3.6.3_Win64'} -m devpi login ${DEVPI_USERNAME} --password ${DEVPI_PASSWORD}"
                                        bat "${tool 'Python3.6.3_Win64'} -m devpi use /${DEVPI_USERNAME}/${env.BRANCH_NAME}_staging"
                                        echo "Testing Source package in devpi"
                                        bat "${tool 'Python3.6.3_Win64'} -m devpi test --index https://devpi.library.illinois.edu/${DEVPI_USERNAME}/${env.BRANCH_NAME}_staging ${name} -s tar.gz"
                                    }
                                }

                            }
                        },
                        "Source Distribution: .zip": {
                            script {
                                def name = bat(returnStdout: true, script: "@${tool 'Python3.6.3_Win64'} setup.py --name").trim()
                                def version = bat(returnStdout: true, script: "@${tool 'Python3.6.3_Win64'} setup.py --version").trim()
                                node("Windows") {
                                    withCredentials([usernamePassword(credentialsId: 'DS_devpi', usernameVariable: 'DEVPI_USERNAME', passwordVariable: 'DEVPI_PASSWORD')]) {
                                        bat "${tool 'Python3.6.3_Win64'} -m devpi login ${DEVPI_USERNAME} --password ${DEVPI_PASSWORD}"
                                        bat "${tool 'Python3.6.3_Win64'} -m devpi use /${DEVPI_USERNAME}/${env.BRANCH_NAME}_staging"
                                        echo "Testing Source package in devpi"
                                        bat "${tool 'Python3.6.3_Win64'} -m devpi test --index https://devpi.library.illinois.edu/${DEVPI_USERNAME}/${env.BRANCH_NAME}_staging ${name} -s tar.gz"
                                    }
                                }

                            }
                        },

                        "Built Distribution: Wheel": {
                            script {
                                def name = bat(returnStdout: true, script: "@${tool 'Python3.6.3_Win64'} setup.py --name").trim()
                                def version = bat(returnStdout: true, script: "@${tool 'Python3.6.3_Win64'} setup.py --version").trim()
                                node("Windows") {
                                    withCredentials([usernamePassword(credentialsId: 'DS_devpi', usernameVariable: 'DEVPI_USERNAME', passwordVariable: 'DEVPI_PASSWORD')]) {
                                        bat "${tool 'Python3.6.3_Win64'} -m devpi login ${DEVPI_USERNAME} --password ${DEVPI_PASSWORD}"
                                        bat "${tool 'Python3.6.3_Win64'} -m devpi use /${DEVPI_USERNAME}/${env.BRANCH_NAME}_staging"
                                        echo "Testing Whl package in devpi"
                                        bat " ${tool 'Python3.6.3_Win64'} -m devpi test --index https://devpi.library.illinois.edu/${DEVPI_USERNAME}/${env.BRANCH_NAME}_staging ${name} -s whl"
                                    }
                                }

                            }
                        }
                )

            }
        }

        stage("Deploy to SCCM") {
            when {
                expression { params.RELEASE == "Release_to_devpi_and_sccm"}
            }

            steps {
                node("Linux"){
                    unstash "msi"
                    deployStash("msi", "${env.SCCM_STAGING_FOLDER}/${params.PROJECT_NAME}/")
                    input("Deploy to production?")
                    deployStash("msi", "${env.SCCM_UPLOAD_FOLDER}")
                }

            }
            post {
                success {
                    script{
                        def deployment_request = requestDeploy this, "deployment.yml"
                        echo deployment_request
                        writeFile file: "deployment_request.txt", text: deployment_request
                        archiveArtifacts artifacts: "deployment_request.txt"
                    }
                }
            }
        }
        stage("Update online documentation") {
            agent {
                label "Linux"
            }
            when {
                expression { params.UPDATE_DOCS == true }
            }

            steps {
                updateOnlineDocs url_subdomain: params.URL_SUBFOLDER, stash_name: "HTML Documentation"

            }
        }
    }
    post {
        always {
            script {
                if(env.BRANCH_NAME == "master" || env.BRANCH_NAME == "dev") {
                    def name = "hathizip"
                    def version = bat(returnStdout: true, script: "@${tool 'Python3.6.3_Win64'} setup.py --version").trim()
                    echo "name == ${name}"
                    echo "version == ${version}"
                    withCredentials([usernamePassword(credentialsId: 'DS_devpi', usernameVariable: 'DEVPI_USERNAME', passwordVariable: 'DEVPI_PASSWORD')]) {
                        bat "${tool 'Python3.6.3_Win64'} -m devpi login ${DEVPI_USERNAME} --password ${DEVPI_PASSWORD}"
                        bat "${tool 'Python3.6.3_Win64'} -m devpi use /${DEVPI_USERNAME}/${env.BRANCH_NAME}_staging"
                        bat "${tool 'Python3.6.3_Win64'} -m devpi remove -y ${name}==${version}"
                    }
                }
            }
        }
        failure {
            echo "Build failed"
        }
        success {
            echo "Cleaning up workspace"
            deleteDir()
        }
    }
}
