def getNodeLabel(agent){
    def label
    if (agent.containsKey("dockerfile")){
        return agent.dockerfile.label
    }
    return label
}
def getToxEnv(args){
    try{
        def pythonVersion = args.pythonVersion.replace(".", "")
        return "py${pythonVersion}"
    } catch(e){
        return "py"
    }
}

def getAgent(agent){
    def nodeLabel = getNodeLabel(agent)
    return node(nodeLabel)
}

// TODO: Make get agent which returns a clousure
def testPkg(args = [:]){
//     def agentRunner = getAgent(args.agent)
    def nodeLabel = getNodeLabel(args.agent)
//     agentRunner{
    node(nodeLabel){
        checkout scm
        def dockerImage
        def dockerImageName = "dummy_${getToxEnv(args)}"
//         TODO: change the docker image name
        lock("docker build-${env.NODE_NAME}"){
            dockerImage = docker.build(dockerImageName, "-f ${args.agent.dockerfile.filename} ${args.agent.dockerfile.additionalBuildArgs} .")
        }
        ws{
            checkout scm
            dockerImage.inside{
                unstash "${args.stash}"
                findFiles(glob: args.glob).each{
                    echo "testing ${it.path}"

                    def toxCommand = "tox --installpkg ${it.path} -e ${getToxEnv(args)}"
                    if(isUnix()){
                        sh(label: "Testing tox version", script: "tox --version")
                        toxCommand = toxCommand + " --workdir /tmp/tox"

                        sh(label: "Running Tox", script: toxCommand)
                    } else{
                        bat(label: "Testing tox version", script: "tox --version")
                        toxCommand = toxCommand + " --workdir %TEMP%\\tox"
                        bat(label: "Running Tox", script: toxCommand)
                    }
                }
            }
        }
    }
}

return [
    testPkg: this.&testPkg
]