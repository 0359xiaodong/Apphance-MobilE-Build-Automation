package com.apphance.ameba.executor

import com.apphance.ameba.configuration.android.AndroidConfiguration
import com.apphance.ameba.executor.command.Command
import com.apphance.ameba.executor.command.CommandExecutor
import groovy.transform.PackageScope
import org.gradle.api.GradleException

import javax.inject.Inject

class AndroidExecutor {

    static final TARGET_HEADER_PATTERN = /id: ([0-9]+) or "([A-Za-z:\-\. 0-9]+)"/

    private List<String> targets
    private Map<String, List<String>> skinsForTarget = [:]
    private Map<String, String> defaultSkinForTarget = [:]
    private Map<String, String> idForTarget = [:]

    @Inject CommandExecutor executor
    @Inject AndroidConfiguration conf

    def updateProject(File dir, String target, String name) {
        def targetId = idForTarget(target)
        run(dir, "update project -p . -t ${targetId ?: target} -n $name -s")
    }

    @PackageScope
    String idForTarget(String target) {
        if (!idForTarget[target]) {
            List<String> output = run(conf.rootDir, 'list target')
            output.collect {
                def header = (it =~ TARGET_HEADER_PATTERN)
                if (header.matches())
                    idForTarget[header[0][2]] = header[0][1]
            }
        }
        idForTarget[target]
    }

    def listAvd() {
        run(conf.rootDir, 'list avd -c')
    }

    def targets() {
        if (!targets) {
            List<String> output = run(conf.rootDir, 'list target')
            targets = parseResult(output, TARGET_HEADER_PATTERN).sort()
        }
        targets
    }

    private List<String> parseResult(input, regex) {
        def result = []
        input.each {
            it = it?.trim()
            def matcher = (it =~ regex)
            if (matcher.matches()) {
                result << matcher[0][2]
            }
        }
        result
    }

    List<String> skinsForTarget(String target) {
        if (!skinsForTarget[target]) {
            List<String> output = run(conf.rootDir, 'list target')
            def targetIdx = output.findIndexOf { it?.contains(target) }
            def skinsIdx = output.findIndexOf(targetIdx) { it?.contains('Skins:') }
            def skinsRaw = output[skinsIdx]
            def skinsProcessed = skinsRaw.substring(skinsRaw.indexOf(':') + 1).replaceAll('\\(default\\)', '')
            skinsForTarget[target] = skinsProcessed.split(',').collect { it.trim() }.sort()
        }
        skinsForTarget[target]
    }

    @PackageScope
    String defaultSkinForTarget(String target) {
        if (!defaultSkinForTarget[target]) {
            List<String> output = run(conf.rootDir, 'list target')
            def targetIdx = output.findIndexOf { it?.contains(target) }
            def skinsIdx = output.findIndexOf(targetIdx) { it?.contains('Skins:') }
            def skinsRaw = output[skinsIdx]
            def skinForTarget = skinsRaw.substring(skinsRaw.indexOf(':') + 1).split(',').find { it.contains('default') }.replaceAll('\\(default\\)', '').trim()
            defaultSkinForTarget[target] = skinForTarget
        }
        defaultSkinForTarget[target]
    }

    def createAvdEmulator(File directory, String name, String targetName, String skin, String cardSize, File avdDir, boolean snapshotsEnabled) {
        run(directory, "-v create avd -n $name -t $targetName -s $skin -c $cardSize -p $avdDir -f ${snapshotsEnabled ? '-a' : ''}", [input: ['no']])
    }

    private List<String> run(File directory, String command, Map params = [:]) {
        try {
            executor.executeCommand(new Command([runDir: directory, cmd: "android $command".split(), failOnError: false] + params))
        } catch (IOException e) {
            throw new GradleException("""|The android utility is probably not in your PATH. Please add it!
                                         |BEWARE! For eclipse junit build it's best to add symbolic link to your
                                         |\$ANDROID_HOME/tools/android in /usr/bin""".stripMargin(), e)
        }
    }
}