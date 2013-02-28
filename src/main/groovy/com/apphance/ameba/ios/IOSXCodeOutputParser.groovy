package com.apphance.ameba.ios

import com.apphance.ameba.ios.plugins.buildplugin.IOSPlugin
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Parses xcodebuild output.
 *
 */
class IOSXCodeOutputParser {

    static Logger logger = Logging.getLogger(IOSXCodeOutputParser.class)

    static Collection readBuildableConfigurations(List trimmedOutput) {
        return readBaseConfigurations(trimmedOutput, { it != "Debug" && it != "Release" })
    }

    static Collection readBuildableTargets(List trimmedOutput) {
        return readBaseTargets(trimmedOutput, { !it.endsWith('Tests') && !it.endsWith('Specs') })
    }

    static Collection readBaseConfigurations(List trimmed, Closure filter) {
        def startConfigurations = trimmed.indexOf('Build Configurations:')
        def configurations = trimmed[startConfigurations + 1..-1]
        def onlyConfigurations = configurations[0..configurations.indexOf('') - 1]
        return onlyConfigurations.findAll(filter)
    }

    static Collection readBaseTargets(List trimmed, Closure filter) {
        def startTargets = trimmed.indexOf('Targets:')
        def targets = trimmed[startTargets + 1..-1]
        def onlyTargets = targets[0..targets.indexOf('') - 1]
        return onlyTargets.findAll(filter)
    }

    static Collection readSchemes(List trimmed) {
        def startSchemes = trimmed.indexOf('Schemes:')
        def schemes = trimmed[startSchemes + 1..-1]
        schemes.indexOf('') != -1 ? schemes[0..schemes.indexOf('') - 1] : schemes
    }


    static Collection readIphoneSdks(List trimmed) {
        def startConfigurations = trimmed.indexOf('iOS SDKs:')
        def configurations = trimmed[startConfigurations + 1..-1]
        def onlyConfigurations = configurations[0..configurations.indexOf('') - 1]
        def output = ['iphoneos']
        onlyConfigurations.each {
            output << it[it.indexOf('-sdk ') + '-sdk '.length()..-1]
        }
        return output
    }

    static Collection readIphoneSimulatorSdks(List trimmed) {
        def startConfigurations = trimmed.indexOf('iOS Simulator SDKs:')
        def configurations = trimmed[startConfigurations + 1..-1]
        def lastIndex = configurations.indexOf('')
        if (lastIndex == -1) {
            lastIndex = 0
        }
        def onlyConfigurations = configurations[0..lastIndex - 1]
        def output = ['iphonesimulator']
        onlyConfigurations.each {
            output << it[it.indexOf('-sdk ') + '-sdk '.length()..-1]
        }
        return output
    }

    static String readProjectName(List trimmed) {
        String firstLine = trimmed[0]
        def matcher = firstLine =~ /.*"(.*)"/
        return matcher[0][1]
    }

    static File findMobileProvisionFile(Project project, String target, String configuration,
                                        boolean checkForNewBundleId = true) {
        IOSProjectConfiguration iosConf = project.ext.get(IOSPlugin.IOS_PROJECT_CONFIGURATION)

        File distributionDirectory = iosConf.distributionDirectory
        if (checkForNewBundleId && iosConf.distributionDirectories[configuration] != null) {
            distributionDirectory = iosConf.distributionDirectories[configuration];
            if (!distributionDirectory.exists()) {
                throw new GradleException("The directory ${distributionDirectory} must exist\
 and mobile provision files must be placed there")
            }
        }
        File f = new File(distributionDirectory, "${target}-${configuration}.mobileprovision")
        if (f.exists()) {
            logger.lifecycle("Mobile provision file found in ${iosConf.distributionDirectory}: ${f}")
            return f
        }
        f = new File(distributionDirectory, "${target}.mobileprovision")
        if (f.exists()) {
            logger.lifecycle("Mobile provision file found in ${iosConf.distributionDirectory}: ${f}")
            return f
        }
        f = new File(distributionDirectory, "${iosConf.mainTarget}.mobileprovision")
        if (f.exists()) {
            logger.lifecycle("Mobile provision file found in ${iosConf.distributionDirectory}: ${f}")
            return f
        }
        distributionDirectory.eachFile {
            if (it.name.endsWith('.mobileprovision')) {
                f = it
            }
        }
        if (f == null) {
            throw new GradleException("The mobileprovision file cannot be found in ${iosConf.distributionDirectory}.\
 Please add one and name it ${iosConf.mainTarget}.mobileprovision")
        }
        logger.lifecycle("Mobile provision file found in ${iosConf.distributionDirectory}: ${f}")
        return f
    }

}
