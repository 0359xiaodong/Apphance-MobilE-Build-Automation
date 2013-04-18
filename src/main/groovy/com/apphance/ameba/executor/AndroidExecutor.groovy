package com.apphance.ameba.executor

import com.apphance.ameba.executor.command.Command
import com.apphance.ameba.executor.command.CommandExecutor
import com.google.inject.Inject
import org.gradle.api.GradleException

class AndroidExecutor {

    private CommandExecutor executor

    @Inject
    AndroidExecutor(CommandExecutor executor) {
        this.executor = executor
    }

    def updateProject(File directory, String target, String name) {
        run(directory, "update project -p . -t $target -n $name -s")
    }

    def listAvd(File directory) {
        run(directory, "list avd -c")
    }

    def listTarget(File directory) {
        List<String> output = run(directory, "list target")
        parseTargets(output)
    }

    private List<String> parseTargets(List<String> input) {
        def targets = []
        def targetPattern = /id:.*"(.*)"/
        def targetPrefix = 'id:'
        input.each {
            def targetMatcher = (it =~ targetPattern)
            if (it.startsWith(targetPrefix) && targetMatcher.matches()) {
                targets << targetMatcher[0][1]
            }
        }
        targets.sort()
    }

    def createAvdEmulator(File directory, String name, String targetName, String skin, String cardSize, File avdDir, boolean snapshotsEnabled) {
        run(directory, "-v create avd -n $name -t $targetName -s $skin -c $cardSize -p $avdDir -f ${snapshotsEnabled ? '-a' : ''}", [input: ['no']])
    }

    def run(File directory, String command, Map params = [:]) {
        try {
            executor.executeCommand(new Command([runDir: directory, cmd: "android $command".split(), failOnError: false] + params))
        } catch (IOException e) {
            throw new GradleException("""|The android utility is probably not in your PATH. Please add it!
                                         |BEWARE! For eclipse junit build it's best to add symbolic link to your
                                         |\$ANDROID_HOME/tools/android in /usr/bin""".stripMargin(), e)
        }
    }
}