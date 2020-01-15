#!groovy
@Library("ds-utils")
import org.ds.*

@Library(["devpi", "PythonHelpers"]) _
CONFIGURATIONS = [
    '3.6': [
        test_docker_image: "python:3.6-windowsservercore",
        tox_env: "py36"
        ],
    "3.7": [
        test_docker_image: "python:3.7",
        tox_env: "py37"
        ]
]

def get_package_version(stashName, metadataFile){
    node {
        unstash "${stashName}"
        script{
            def props = readProperties interpolate: true, file: "${metadataFile}"
            cleanWs(patterns: [[pattern: "${metadataFile}", type: 'INCLUDE']])
            //deleteDir()
            return props.Version
        }
    }
}
def get_package_name(stashName, metadataFile){
    node {
        unstash "${stashName}"
        script{
            def props = readProperties interpolate: true, file: "${metadataFile}"
            cleanWs(patterns: [[pattern: "${metadataFile}", type: 'INCLUDE']])
            return props.Name
        }
    }
}


def remove_from_devpi(devpiExecutable, pkgName, pkgVersion, devpiIndex, devpiUsername, devpiPassword){
    script {
                try {
                    bat "${devpiExecutable} login ${devpiUsername} --password ${devpiPassword}"
                    bat "${devpiExecutable} use ${devpiIndex}"
                    bat "${devpiExecutable} remove -y ${pkgName}==${pkgVersion}"
                } catch (Exception ex) {
                    echo "Failed to remove ${pkgName}==${pkgVersion} from ${devpiIndex}"
            }

    }
}


pipeline {
    agent none
    //agent {
    //    label "Windows && Python3"
    //}
    options {
        disableConcurrentBuilds()  //each branch has 1 job running at a time
        timeout(60)  // Timeout after 60 minutes. This shouldn't take this long but it hangs for some reason
        //checkoutToSubdirectory("source")
    }
//    environment {
////        DEVPI = credentials("DS_devpi")
////        mypy_args = "--junit-xml=mypy.xml"
////        pytest_args = "--junitxml=reports/junit-{env:OS:UNKNOWN_OS}-{envname}.xml --junit-prefix={env:OS:UNKNOWN_OS}  --basetemp={envtmpdir}"
//    }
    triggers {
       parameterizedCron '@daily % PACKAGE_CX_FREEZE=true; DEPLOY_DEVPI=true; TEST_RUN_TOX=true'
    }
    parameters {
        string(name: "PROJECT_NAME", defaultValue: "HathiTrust Zip for Submit", description: "Name given to the project")
        booleanParam(name: "TEST_RUN_TOX", defaultValue: false, description: "Run Tox Tests")
        booleanParam(name: "PACKAGE_CX_FREEZE", defaultValue: false, description: "Create a package with CX_Freeze")
        // TODO: set this to false
        booleanParam(name: "DEPLOY_DEVPI", defaultValue: true, description: "Deploy to devpi on https://devpi.library.illinois.edu/DS_Jenkins/${env.BRANCH_NAME}")
        booleanParam(name: "DEPLOY_DEVPI_PRODUCTION", defaultValue: false, description: "Deploy to https://devpi.library.illinois.edu/production/release")
        booleanParam(name: "DEPLOY_SCCM", defaultValue: false, description: "Deploy to SCCM")
        booleanParam(name: "UPDATE_DOCS", defaultValue: false, description: "Update online documentation")
        string(name: 'URL_SUBFOLDER', defaultValue: "hathi_zip", description: 'The directory that the docs should be saved under')
    }
    stages {
        stage("Stashing important files for later"){
            agent {
                dockerfile {
                    filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                    label "windows && docker"
                }
            }
            steps{
                bat "python setup.py dist_info"
            }
            post{
                success{
                    stash includes: "HathiZip.dist-info/**", name: 'DIST-INFO'
                    archiveArtifacts artifacts: "HathiZip.dist-info/**"
                    stash includes: 'deployment.yml', name: "Deployment"
                }
                cleanup{
                    cleanWs(
                        deleteDirs: true,
                        patterns: [
                            [pattern: "dist/", type: 'INCLUDE'],
                            [pattern: "HathiZip.dist-info/", type: 'INCLUDE'],
                            [pattern: 'build/', type: 'INCLUDE']
                        ]
                    )
                }
            }
        }

        stage("Build"){
            agent {
              dockerfile {
                filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                label "windows && docker"
              }
            }
            stages{
                stage("Python Package"){
                    steps {
                        bat "if not exist logs mkdir logs"
                        powershell "& python setup.py build -b build   | tee ${WORKSPACE}\\logs\\build.log"
                    }
                    post{
                        always{
                            recordIssues(tools: [
                                        pyLint(name: 'Setuptools Build: PyLint', pattern: 'logs/build.log'),
                                    ]
                                )
                            archiveArtifacts artifacts: "logs/build.log"
                        }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: "dist/", type: 'INCLUDE'],
                                    [pattern: 'build/', type: 'INCLUDE']
                                ]
                            )
                        }

                    }
                }
                stage("Building Sphinx Documentation"){
                    steps {
                        bat(label:"Building docs on ${env.NODE_NAME}", script: "python -m sphinx docs/source build/docs/html -d build/docs/.doctrees -v -w logs\\build_sphinx.log")
                    }
                    post{
                        always {
                            recordIssues(tools: [sphinxBuild(name: 'Sphinx Documentation Build', pattern: 'logs/build_sphinx.log')])
                            archiveArtifacts artifacts: 'logs/build_sphinx.log'
                        }
                        success{
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'build/docs/html', reportFiles: 'index.html', reportName: 'Documentation', reportTitles: ''])
                            unstash "DIST-INFO"
                            script{
                                def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
                                def DOC_ZIP_FILENAME = "${props.Name}-${props.Version}.doc.zip"
                                zip archive: true, dir: "build/docs/html", glob: '', zipFile: "dist/${DOC_ZIP_FILENAME}"
                                stash includes: "dist/${DOC_ZIP_FILENAME},build/docs/html/**", name: 'DOCS_ARCHIVE'
                            }
                        }
                        failure{
                            echo "Failed to build Python package"
                        }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: "dist/", type: 'INCLUDE'],
                                    [pattern: 'build/', type: 'INCLUDE'],
                                    [pattern: "HathiZip.dist-info/", type: 'INCLUDE'],
                                ]
                            )
                        }
                    }
                }
            }
        }
        stage("Tests") {

            parallel {
                stage("PyTest"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                            label "windows && docker"
                        }
                    }
                    steps{
                        bat "python -m pytest --junitxml=reports/junit-${env.NODE_NAME}-pytest.xml --junit-prefix=${env.NODE_NAME}-pytest --cov-report html:reports/coverage/ --cov=hathizip" //  --basetemp={envtmpdir}"

                    }
                    post {
                        always{
                            dir("reports"){
                                script{
                                    def report_files = findFiles glob: '**/*.pytest.xml'
                                    report_files.each { report_file ->
                                        echo "Found ${report_file}"
                                        junit "${report_file}"
                                        bat "del ${report_file}"
                                    }
                                }
                            }
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'reports/coverage', reportFiles: 'index.html', reportName: 'Coverage', reportTitles: ''])
                        }
                    }
                }
                stage("Documentation"){
                    //environment {
                    //    PATH = "${WORKSPACE}\\venv\\Scripts;$PATH"
                    //}
                    agent {
                        dockerfile {
                            filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                            label "windows && docker"
                        }
                    }
                    steps{
                        bat "python -m sphinx -b doctest docs\\source build\\docs -d build\\docs\\doctrees -v"
                    }
                    post{
                        cleanup{
                            cleanWs(
                                    deleteDirs: true,
                                    patterns: [
                                        [pattern: 'build/', type: 'INCLUDE'],
                                        [pattern: 'dist/', type: 'INCLUDE'],
                                        [pattern: 'logs/', type: 'INCLUDE'],
                                        [pattern: 'HathiZip.egg-info/', type: 'INCLUDE'],
                                    ]
                                )
                            //cleanWs(
                            //    deleteDirs: true,
                            //    patterns: [
                            //        [pattern: 'ci/', type: 'EXCLUDE'],
                            //        [pattern: 'docs/', type: 'EXCLUDE'],
                            //        [pattern: 'hathizip/', type: 'EXCLUDE'],
                            //        [pattern: 'tests/', type: 'EXCLUDE'],
                            //        [pattern: '.git/', type: 'EXCLUDE'],
                            //        [pattern: '.gitignore', type: 'EXCLUDE'],
                            //        [pattern: '.travis.yml', type: 'EXCLUDE'],
                            //        [pattern: 'CHANGELOG.rst', type: 'EXCLUDE'],
                            //        [pattern: 'cx_setup.py', type: 'EXCLUDE'],
                            //        [pattern: 'deployment.yml', type: 'EXCLUDE'],
                            //        [pattern: 'documentation.url', type: 'EXCLUDE'],
                            //        [pattern: 'Jenkinsfile', type: 'EXCLUDE'],
                            //        [pattern: 'LICENSE', type: 'EXCLUDE'],
                            //        [pattern: 'make.bat', type: 'EXCLUDE'],
                            //        [pattern: 'MANIFEST.in', type: 'EXCLUDE'],
                            //        [pattern: 'Pipfile', type: 'EXCLUDE'],
                            //        [pattern: 'Pipfile.lock', type: 'EXCLUDE'],
                            //        [pattern: 'README.rst', type: 'EXCLUDE'],
                            //        [pattern: 'requirements.txt', type: 'EXCLUDE'],
                            //        [pattern: 'requirements-dev.txt', type: 'EXCLUDE'],
                            //        [pattern: 'requirements-freeze.txt', type: 'EXCLUDE'],
                            //        [pattern: 'setup.cfg', type: 'EXCLUDE'],
                            //        [pattern: 'setup.py', type: 'EXCLUDE'],
                            //        [pattern: 'tox.ini', type: 'EXCLUDE']
                            //    ]
                            //)
                        }
                    }

                }
                stage("MyPy"){
                    agent {
                      dockerfile {
                        filename 'ci/docker/python-testing/Dockerfile'
                        label "linux && docker"
                      }
                    }
//                    environment {
//                        PATH = "${WORKSPACE}\\venv\\Scripts;$PATH"
//                    }
                    steps{
                        sh "mkdir -p reports/mypy"

                        sh "mkdir -p logs"
                        sh(
                            returnStatus: true,
                            script: "mypy -p hathizip --html-report ${WORKSPACE}/reports/mypy/mypy_html | tee logs/mypy.log")
                    }
                    post{
                        always {
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'reports/mypy/mypy_html', reportFiles: 'index.html', reportName: 'MyPy', reportTitles: ''])
                            recordIssues(tools: [myPy(name: 'MyPy', pattern: 'logs/mypy.log')])
                        }
                        cleanup{
                            deleteDir()
                        }
                    }
                }
                stage("Run Flake8 Static Analysis") {
                    agent {
                        dockerfile {
                            filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                            label "windows && docker"
                        }
                    }
                    steps{
                        bat "if not exist logs mkdir logs"
                        script{
                            // TODO: change to catch errors block
                            try{
                                bat "flake8 pyhathiprep --tee --output-file=logs\\flake8.log"
                            } catch (exc) {
                                echo "flake8 found some warnings"
                            }
                        }
                    }
                    post {
                        always {

                            recordIssues(tools: [flake8(name: 'Flake8', pattern: 'logs/flake8.log')])
                        }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: 'build/', type: 'INCLUDE'],
                                    [pattern: 'dist/', type: 'INCLUDE'],
                                    [pattern: 'logs/', type: 'INCLUDE'],
                                    [pattern: 'HathiZip.egg-info/', type: 'INCLUDE'],
                                ]
                            )
                        }
                    }
                }
                stage("Run Tox"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                            label "windows && docker"
                        }
                    }
                    //environment{
                    //    PATH = "${tool 'CPython-3.6'};${tool 'CPython-3.7'};${WORKSPACE}\\venv\\Scripts;$PATH"
                    //}
                    when{
                        equals expected: true, actual: params.TEST_RUN_TOX
                        beforeAgent true
                    }
                    steps {
                        script{
                            try{
                                bat (
                                    label: "Run Tox",
                                    script: "tox -e py --workdir .tox -v"
                                )
                            } catch (exc) {
                                bat (
                                    label: "Run Tox with new environments",
                                    script: "tox --recreate -e py  --workdir .tox -v"
                                )
                            }
                        }
//                        dir("source"){
//                            script{
//                                try{
//                                    bat "tox --parallel=auto --parallel-live --workdir ${WORKSPACE}\\.tox -vv"
//                                } catch (exc) {
//                                    bat "tox --parallel=auto --parallel-live --workdir ${WORKSPACE}\\.tox --recreate -vv"
//                                }
//                            }
//                        }
                    }
                    post{
                        always{
                            archiveArtifacts allowEmptyArchive: true, artifacts: '.tox/py*/log/*.log,.tox/log/*.log,logs/tox_report.json'
                        }
                        cleanup{
                            cleanWs deleteDirs: true, patterns: [
                                [pattern: '.tox/', type: 'INCLUDE'],
                                [pattern: "HathiZip.dist-info/", type: 'INCLUDE'],
                                [pattern: 'logs/rox_report.json', type: 'INCLUDE']
                            ]
                        }
                    }
                }
            }
        }
        stage("Packaging") {
            parallel {
                stage("Source and Wheel formats"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                            label "windows && docker"
                        }
                    }
                    steps{
                        bat "python setup.py sdist --format zip -d dist bdist_wheel -d dist"

                    }
                    post{
                        success{
                            archiveArtifacts artifacts: "dist/*.whl,dist/*.tar.gz,dist/*.zip", fingerprint: true
                            stash includes: 'dist/*.whl,dist/*.tar.gz,dist/*.zip', name: "dist"
                        }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: 'build/', type: 'INCLUDE'],
                                    [pattern: 'dist/', type: 'INCLUDE'],
                                    [pattern: 'logs/', type: 'INCLUDE'],
                                    [pattern: 'HathiZip.egg-info/', type: 'INCLUDE'],
                                ]
                            )
                        }
                    }
                }
                stage("Windows CX_Freeze MSI"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                            label "windows && docker"
                        }
                    }
                    when{
                        equals expected: true, actual: params.PACKAGE_CX_FREEZE
                        beforeAgent true
                    }
                    steps{
                        //bat "venv\\Scripts\\pip.exe install -r source\\requirements.txt -r source\\requirements-freeze.txt -r source\\requirements-dev.txt -q"
                        bat "python cx_setup.py bdist_msi --add-to-path=true -k --bdist-dir build/msi -d dist"


                    }
                    post{
                        success{
                            stash includes: "dist/*.msi", name: "msi"
                            archiveArtifacts artifacts: "dist/*.msi", fingerprint: true
                            }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: 'build/', type: 'INCLUDE'],
                                    [pattern: 'dist/', type: 'INCLUDE'],
                                    [pattern: 'logs/', type: 'INCLUDE'],
                                    [pattern: 'HathiZip.egg-info/', type: 'INCLUDE'],
                                ]
                            )
                        }
                    }
                }
            }
        }
        stage("Deploying to Devpi") {
            when {
                allOf{
                    anyOf{
                        equals expected: true, actual: params.DEPLOY_DEVPI
                        triggeredBy "TimerTriggerCause"
                    }
                    anyOf {
                        equals expected: "master", actual: env.BRANCH_NAME
                        equals expected: "dev", actual: env.BRANCH_NAME
                    }
                }
                beforeAgent true
            }
            options{
                timestamps()
            }
            agent none
            //agent{
            //    label "windows && Python3"
            //}
            environment{
                DEVPI = credentials("DS_devpi")
            }

            stages{
                //stage("Install DevPi Client"){
                //    agent {
                //        dockerfile {
                //            filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                //            label "windows && docker"
                //        }
                //    }
                //    steps{
                //        bat "pip install devpi-client"
                //    }
                //}
                stage("Uploading to DevPi Staging"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/deploy/devpi/deploy/Dockerfile'
                            label 'linux&&docker'
                            additionalBuildArgs '--build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g)'
                          }
                    }
                    steps {
                        unstash "DOCS_ARCHIVE"
                        unstash "dist"
                        sh(
                                label: "Connecting to DevPi Server",
                                script: 'devpi use https://devpi.library.illinois.edu --clientdir ${WORKSPACE}/devpi && devpi login $DEVPI_USR --password $DEVPI_PSW --clientdir ${WORKSPACE}/devpi'
                            )
                            sh(
                                label: "Uploading to DevPi Staging",
                                script: """devpi use /${env.DEVPI_USR}/${env.BRANCH_NAME}_staging --clientdir ${WORKSPACE}/devpi
    devpi upload --from-dir dist --clientdir ${WORKSPACE}/devpi"""
                            )

                    }
                    post{
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: "dist/", type: 'INCLUDE'],
                                    [pattern: "HathiZip.dist-info/", type: 'INCLUDE'],
                                    [pattern: 'build/', type: 'INCLUDE']
                                ]
                            )
                        }
                    }
                }
                stage("Test DevPi packages") {
                    environment{
                        PKG_NAME = get_package_name("DIST-INFO", "HathiZip.dist-info/METADATA")
                        PKG_VERSION = get_package_version("DIST-INFO", "HathiZip.dist-info/METADATA")
                    }
                    matrix {
                        axes {
                            axis {
                                name 'FORMAT'
                                values 'zip', "whl"
                            }
                            axis {
                                name 'PYTHON_VERSION'
                                values '3.6', "3.7"
                            }
                        }
                        agent {
                          dockerfile {
                            additionalBuildArgs "--build-arg PYTHON_DOCKER_IMAGE_BASE=${CONFIGURATIONS[PYTHON_VERSION].test_docker_image}"
                            filename 'ci/docker/deploy/devpi/test/windows/Dockerfile'
                            label 'windows && docker'
                          }
                        }
                        stages{
                            stage("Testing DevPi Package"){
                                options{
                                    timeout(10)
                                }
                                steps{
                                    bat "devpi use https://devpi.library.illinois.edu --clientdir certs\\ && devpi login %DEVPI_USR% --password %DEVPI_PSW% --clientdir certs\\ && devpi use ${env.BRANCH_NAME}_staging --clientdir certs\\"
                                    bat "devpi test --index ${env.BRANCH_NAME}_staging ${PKG_NAME}==${PKG_VERSION} -s ${FORMAT} --clientdir certs\\ -e ${CONFIGURATIONS[PYTHON_VERSION].tox_env} -v"
                                }
                            }

                        }
                    }
                    post{
                        success{
                            node('linux && docker') {
                               script{
                                    docker.build("hathizip:devpi.${env.BUILD_ID}",'-f ./ci/docker/deploy/devpi/deploy/Dockerfile --build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g) .').inside{
                                        unstash "DIST-INFO"
                                        def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
                                        sh(
                                            label: "Connecting to DevPi Server",
                                            script: 'devpi use https://devpi.library.illinois.edu --clientdir ${WORKSPACE}/devpi && devpi login $DEVPI_USR --password $DEVPI_PSW --clientdir ${WORKSPACE}/devpi'
                                        )
                                        sh "devpi use DS_Jenkins/${env.BRANCH_NAME}_staging --clientdir ${WORKSPACE}/devpi"
                                        sh "devpi push ${props.Name}==${props.Version} DS_Jenkins/${env.BRANCH_NAME} --clientdir ${WORKSPACE}/devpi"
                                    }
                               }
                            }
                        }
                        cleanup{
                            node('linux && docker') {
                               script{
                                    docker.build("hathizip:devpi.${env.BUILD_ID}",'-f ./ci/docker/deploy/devpi/deploy/Dockerfile --build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g) .').inside{
                                        unstash "DIST-INFO"
                                        def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
                                        sh(
                                            label: "Connecting to DevPi Server",
                                            script: 'devpi use https://devpi.library.illinois.edu --clientdir ${WORKSPACE}/devpi && devpi login $DEVPI_USR --password $DEVPI_PSW --clientdir ${WORKSPACE}/devpi'
                                        )
                                        sh "devpi use /DS_Jenkins/${env.BRANCH_NAME}_staging --clientdir ${WORKSPACE}/devpi"
                                        sh "devpi remove -y ${props.Name}==${props.Version} --clientdir ${WORKSPACE}/devpi"
                                    }
                               }
                            }
                        }

                    }
                }
                stage("Deploy to DevPi Production") {
                    when {
                        allOf{
                            equals expected: true, actual: params.DEPLOY_DEVPI_PRODUCTION
                            branch "master"
                        }
                        beforeAgent true
                    }
                    agent {
                        dockerfile {
                            filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                            label "windows && docker"
                        }
                    }
                    steps {
                        unstash "DIST-INFO"
                        script {
                            def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
                            input "Release ${props.Name} ${props.Version} to DevPi Production?"
                            bat "devpi login ${env.DEVPI_USR} --password ${env.DEVPI_PSW}"
                            bat "devpi  use /DS_Jenkins/${env.BRANCH_NAME}"
                            bat "devpi push ${props.Name}==${props.Version} production/release"
                        }
                    }
                }
            }
        }

        stage("Deploy to SCCM") {
            when{
                allOf{
                    equals expected: true, actual: params.DEPLOY_SCCM
                    branch "master"
                }
                beforeAgent true
            }
            agent{
                label "linux"
            }
            options{
                skipDefaultCheckout true
            }

            steps {
                unstash "msi"
                deployStash("msi", "${env.SCCM_STAGING_FOLDER}/${params.PROJECT_NAME}/")
                input("Deploy to production?")
                deployStash("msi", "${env.SCCM_UPLOAD_FOLDER}")

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
    //post{
    //    cleanup{
//            script {
//                if(fileExists('source/setup.py')){
//                    dir("source"){
//                        try{
//                            retry(3) {
//                                bat "${WORKSPACE}\\venv\\Scripts\\python.exe setup.py clean --all"
//                            }
//                        } catch (Exception ex) {
//                            echo "Unable to successfully run clean. Purging source directory."
//                            deleteDir()
//                        }
//                    }
//                }
//            }
//            cleanWs deleteDirs: true, patterns: [
//                    [pattern: 'source', type: 'INCLUDE'],
//                    [pattern: 'certs', type: 'INCLUDE'],
//                    [pattern: 'dist*', type: 'INCLUDE'],
//                    [pattern: 'logs*', type: 'INCLUDE'],
//                    [pattern: 'reports*', type: 'INCLUDE'],
//                    [pattern: '*tmp', type: 'INCLUDE']
//                    ]
//        }
//    }
}
