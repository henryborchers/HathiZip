def getNodeLabel(agent){
    def label
    if (agent.containsKey("dockerfile")){
        return agent.dockerfile.label
    }
    return label
}
def getToxEnv(args){
// TODO: convert something like 3.6 into py36
    return "py"
}

// TODO: Make get agent which returns a clousure
def test_pkg(args = [:]){
    echo "args ${args}"
    def nodeLabel = getNodeLabel(args.agent)
    node(nodeLabel){
        checkout scm
        def dockerImage
        lock("docker build-${env.NODE_NAME}"){
            dockerImage = docker.build("dummy", "-f ${args.agent.dockerfile.filename} ${args.agent.dockerfile.additionalBuildArgs} .")
        }
        ws{
            checkout scm
            dockerImage.inside{
                unstash "${args.stash}"
                findFiles(glob: args.glob).each{
                    echo "testing ${it.path}"

                    def toxCommand = "tox --installpkg ${it.path} -e ${getToxEnv(args)}"
                    if(isUnix()){
                        sh "tox --version"
                        sh toxCommand
                    } else{
                        bat "tox --version"
                        bat toxCommand
                    }
                }
            }
        }
    }
}

return this