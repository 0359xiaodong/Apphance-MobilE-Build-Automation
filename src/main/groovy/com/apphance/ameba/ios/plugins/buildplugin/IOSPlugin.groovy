package com.apphance.ameba.ios.plugins.buildplugin

import com.apphance.ameba.AmebaCommonBuildTaskGroups
import com.apphance.ameba.ProjectConfiguration
import com.apphance.ameba.PropertyCategory
import com.apphance.ameba.executor.IOSExecutor
import com.apphance.ameba.executor.command.Command
import com.apphance.ameba.executor.command.CommandExecutor
import com.apphance.ameba.ios.IOSProjectConfiguration
import com.apphance.ameba.ios.MPParser
import com.apphance.ameba.ios.plugins.release.IOSReleaseListener
import com.apphance.ameba.plugins.projectconfiguration.ProjectConfigurationPlugin
import com.sun.org.apache.xpath.internal.XPathAPI
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

import javax.inject.Inject

import static com.apphance.ameba.AmebaCommonBuildTaskGroups.AMEBA_BUILD
import static com.apphance.ameba.ios.IOSXCodeOutputParser.*
import static org.gradle.api.logging.Logging.getLogger

/**
 * Plugin for various X-Code related tasks.
 *
 */
class IOSPlugin implements Plugin<Project> {

    public static final String IOS_PROJECT_CONFIGURATION = 'ios.project.configuration'
    public static final String IOS_CONFIGURATION_LOCAL_PROPERTY = 'ios.configuration'
    public static final String IOS_TARGET_LOCAL_PROPERTY = 'ios.target'

    def l = getLogger(getClass())

    @Inject
    CommandExecutor executor

    @Inject
    IOSExecutor iosExecutor

    String pListFileName
    ProjectConfiguration conf
    IOSProjectConfiguration iosConf
    IOSSingleVariantBuilder iosSingleVariantBuilder

    public static final List<String> FAMILIES = ['iPad', 'iPhone']

    @Override
    def void apply(Project project) {
        use(PropertyCategory) {
            this.conf = project.getProjectConfiguration()
            this.iosConf = getIosProjectConfiguration(project)
            this.iosSingleVariantBuilder = new IOSSingleVariantBuilder(project, executor)
            prepareCopySourcesTask(project)
            prepareCopyDebugSourcesTask(project)
            prepareReadIosProjectConfigurationTask(project)
            prepareReadIosTargetsAndConfigurationsTask(project)
            prepareReadIosProjectVersionsTask(project)
            prepareCleanTask(project)
            prepareUnlockKeyChainTask(project)
            prepareCopyMobileProvisionTask(project)
            prepareBuildSingleVariantTask(project)
            prepareBuildAllSimulatorsTask(project)
            prepareBuildAllTask(project)
            addIosSourceExcludes()
            project.prepareSetup.prepareSetupOperations << new PrepareIOSSetupOperation()
            project.verifySetup.verifySetupOperations << new VerifyIOSSetupOperation()
            project.showSetup.showSetupOperations << new ShowIOSSetupOperation()
        }
    }

    IOSProjectConfiguration getIosProjectConfiguration(Project project) {
        if (!project.ext.has(IOS_PROJECT_CONFIGURATION)) {
            project.ext.set(IOS_PROJECT_CONFIGURATION, new IOSProjectConfiguration())
        }
        return project.ext.get(IOS_PROJECT_CONFIGURATION)
    }

    private addIosSourceExcludes() {
        conf.sourceExcludes << '**/build/**'
    }

    private prepareReadIosProjectConfigurationTask(Project project) {
        def task = project.task('readIOSProjectConfiguration')
        task.group = AmebaCommonBuildTaskGroups.AMEBA_CONFIGURATION
        task.description = 'Reads iOS project configuration'
        task << { readIosProjectConfiguration(project) }
        project.readProjectConfiguration.dependsOn(task)
    }

    private readIosProjectConfiguration(Project project) {
        use(PropertyCategory) {
            readBasicIosProjectProperties(project)
            iosConf.sdk = project.readProperty(IOSProjectProperty.IOS_SDK)
            iosConf.simulatorSDK = project.readProperty(IOSProjectProperty.IOS_SIMULATOR_SDK)
            iosConf.plistFile = pListFileName == null ? null : project.file(pListFileName)
            String distDirName = project.readProperty(IOSProjectProperty.DISTRIBUTION_DIR)
            iosConf.distributionDirectory = distDirName == null ? null : project.file(distDirName)
            iosConf.families = project.readProperty(IOSProjectProperty.IOS_FAMILIES).split(",")*.trim()
        }
    }

    private readBasicIosProjectProperties(Project project) {
        use(PropertyCategory) {
            this.pListFileName = project.readProperty(IOSProjectProperty.PLIST_FILE)
            readProjectDirectory(project)
            iosConf.excludedBuilds = project.readProperty(IOSProjectProperty.EXCLUDED_BUILDS).split(",")*.trim()
            iosConf.mainTarget = project.readProperty(IOSProjectProperty.MAIN_TARGET)
            iosConf.mainConfiguration = project.readProperty(IOSProjectProperty.MAIN_CONFIGURATION)
        }
    }

    private readProjectDirectory(Project project) {
        use(PropertyCategory) {
            if (project.readProperty(IOSProjectProperty.PROJECT_DIRECTORY) != null) {
                iosConf.xCodeProjectDirectory = new File(project.rootDir, project.readProperty(IOSProjectProperty.PROJECT_DIRECTORY))
            }
        }
    }

    private readVariantedProjectDirectories(Project project) {
        use(PropertyCategory) {
            if (project.readProperty(IOSProjectProperty.PROJECT_DIRECTORY) != null) {
                readBasicIosProjectProperties(project)
                iosConf.allTargets.each { target ->
                    iosConf.allConfigurations.each { configuration ->
                        String variant = iosConf.getVariant(target, configuration)
                        iosConf.xCodeProjectDirectories[variant] = new File(iosSingleVariantBuilder.tmpDir(target, configuration),
                                project.readProperty(IOSProjectProperty.PROJECT_DIRECTORY))

                    }
                }
                l.info("Adding project directory: ${this.iosConf.mainTarget}-Debug")
                iosConf.xCodeProjectDirectories["${this.iosConf.mainTarget}-Debug".toString()] =
                    new File(iosSingleVariantBuilder.tmpDir(this.iosConf.mainTarget, 'Debug'),
                            project.readProperty(IOSProjectProperty.PROJECT_DIRECTORY))
            }
        }
    }

    def void prepareReadIosTargetsAndConfigurationsTask(Project project) {
        def task = project.task('readIOSParametersFromXcode')
        task.group = AmebaCommonBuildTaskGroups.AMEBA_CONFIGURATION
        task.description = 'Reads iOS xCode project parameters'
        task << {
            project.file("bin").mkdirs()
            if (iosConf.targets == ['']) {
                l.lifecycle("Please specify at least one target")
                iosConf.targets = []
            }
            if (iosConf.configurations == ['']) {
                l.lifecycle("Please specify at least one configuration")
                iosConf.configurations = []
            }
            if (iosConf.mainTarget == null) {
                iosConf.mainTarget = iosConf.targets.empty ? null : iosConf.targets[0]
            }
            if (iosConf.mainConfiguration == null) {
                iosConf.mainConfiguration = iosConf.configurations.empty ? null : iosConf.configurations[0]
            }
            l.lifecycle("Standard buildable targets: " + iosConf.targets)
            l.lifecycle("Standard buildable configurations : " + iosConf.configurations)
            l.lifecycle("Main target: " + iosConf.mainTarget)
            l.lifecycle("Main configuration : " + iosConf.mainConfiguration)
            l.lifecycle("All targets: " + iosConf.allTargets)
            l.lifecycle("All configurations : " + iosConf.allConfigurations)
        }
        project.readProjectConfiguration.dependsOn(task)
    }

    private readProjectConfigurationFromXCode(Project project) {
        readProjectDirectory(project)
        def trimmedListOutput = iosExecutor.list()*.trim()
        if (trimmedListOutput.empty || trimmedListOutput[0] == '') {//TODO possible?
            throw new GradleException("Error while running ${cmd}:")
        }

        project.ext[ProjectConfigurationPlugin.PROJECT_NAME_PROPERTY] = readProjectName(trimmedListOutput)
        iosConf.targets = readBuildableTargets(trimmedListOutput)
        iosConf.configurations = readBuildableConfigurations(trimmedListOutput)
        iosConf.allTargets = readBaseTargets(trimmedListOutput, { true })
        iosConf.allConfigurations = readBaseConfigurations(trimmedListOutput, { true })
        iosConf.schemes = readSchemes(trimmedListOutput)

        def trimmedSdkOutput = iosExecutor.showSdks()*.trim()
        iosConf.allIphoneSDKs = readIphoneSdks(trimmedSdkOutput)
        iosConf.allIphoneSimulatorSDKs = readIphoneSimulatorSdks(trimmedSdkOutput)
        readVariantedProjectDirectories(project)
    }

    void prepareReadIosProjectVersionsTask(Project project) {
        def task = project.task('readIOSProjectVersions')
        task.group = AmebaCommonBuildTaskGroups.AMEBA_CONFIGURATION
        task.description = 'Reads iOS project version information'
        task << {
            use(PropertyCategory) {
                this.pListFileName = project.readProperty(IOSProjectProperty.PLIST_FILE)
                def root = MPParser.getParsedPlist(pListFileName, project)
                if (root != null) {
                    XPathAPI.selectNodeList(root,
                            '/plist/dict/key[text()="CFBundleShortVersionString"]').each {
                        conf.versionString = it.nextSibling.nextSibling.textContent
                    }
                    XPathAPI.selectNodeList(root,
                            '/plist/dict/key[text()="CFBundleVersion"]').each {
                        def versionCodeString = it.nextSibling.nextSibling.textContent
                        try {
                            conf.versionCode = versionCodeString.toLong()
                        } catch (NumberFormatException e) {
                            l.lifecycle("Format of the ${versionCodeString} is not numeric. Starting from 1.")
                            conf.versionCode = 0
                        }
                    }
                    if (!project.isPropertyOrEnvironmentVariableDefined('version.string')) {
                        l.lifecycle("Version string is updated to SNAPSHOT because it is not release build")
                        conf.versionString = conf.versionString + "-SNAPSHOT"
                    } else {
                        l.lifecycle("Version string is not updated to SNAPSHOT because it is release build")
                    }
                }
            }
        }
        project.readProjectConfiguration.dependsOn(task)
    }


    void prepareBuildAllTask(Project project) {
        def task = project.task('buildAll')
        task.group = AMEBA_BUILD
        task.description = 'Builds all target/configuration combinations and produces all artifacts (zip, ipa, messages, etc)'

        readProjectConfigurationFromXCode(project)
        readBasicIosProjectProperties(project)

        l.lifecycle('Preparing all build tasks')
        iosConf.allBuildableVariants.each { v ->
            def singleTask = project.task("build-${v.noSpaceId}")
            singleTask.group = AMEBA_BUILD
            singleTask.description = "Builds target: ${v.target}, configuration: ${v.configuration}"
            singleTask << {
                def builder = new IOSSingleVariantBuilder(project, executor, new IOSReleaseListener(project, executor))
                builder.buildNormalVariant(project, v.target, v.configuration)
            }
            task.dependsOn(singleTask)
            singleTask.dependsOn(project.readProjectConfiguration, project.copyMobileProvision, project.verifySetup, project.copySources)
        }

        task.dependsOn(project.readProjectConfiguration)
    }

    def void prepareBuildSingleVariantTask(Project project) {
        def task = project.task('buildSingleVariant')
        task.group = AMEBA_BUILD
        task.description = "Builds single variant for iOS. Requires ios.target and ios.configuration properties"
        task << {
            use(PropertyCategory) {
                def singleVariantBuilder = new IOSSingleVariantBuilder(project, executor)
                String target = project.readExpectedProperty(IOS_TARGET_LOCAL_PROPERTY)
                String configuration = project.readExpectedProperty(IOS_CONFIGURATION_LOCAL_PROPERTY)
                singleVariantBuilder.buildNormalVariant(project, target, configuration)
            }
        }
        task.dependsOn(project.readProjectConfiguration, project.verifySetup, project.copySources)
    }

    private void prepareBuildAllSimulatorsTask(Project project) {
        def task = project.task('buildAllSimulators', group: AMEBA_BUILD,
                description: 'Builds all simulators for the project',
                dependsOn: [project.readProjectConfiguration, project.copyMobileProvision, project.copyDebugSources])
        task.doLast { new IOSAllSimulatorsBuilder(project, executor).buildAllSimulators() }

    }

    def void prepareCleanTask(Project project) {
        def task = project.task('clean')
        task.description = "Cleans the project"
        task.group = AMEBA_BUILD
        task << {
            executor.executeCommand(new Command(runDir: project.rootDir, cmd: ['dot_clean', './']))
            ant.delete(dir: project.file("build"), verbose: true)
            ant.delete(dir: project.file("bin"), verbose: true)
            ant.delete(dir: project.file("tmp"), verbose: true)
        }
        task.dependsOn(project.cleanConfiguration)
    }

    def void prepareUnlockKeyChainTask(Project project) {
        def task = project.task('unlockKeyChain')
        task.description = """Unlocks key chain used during project building.
              Requires osx.keychain.password and osx.keychain.location properties
              or OSX_KEYCHAIN_PASSWORD and OSX_KEYCHAIN_LOCATION environment variable"""
        task.group = AMEBA_BUILD
        task << {
            use(PropertyCategory) {
                def keychainPassword = project.readOptionalPropertyOrEnvironmentVariable("osx.keychain.password")
                def keychainLocation = project.readOptionalPropertyOrEnvironmentVariable("osx.keychain.location")
                if (keychainLocation != null && keychainPassword != null) {
                    executor.executeCommand(new Command(runDir: project.rootDir, cmd: [
                            'security',
                            'unlock-keychain',
                            '-p',
                            keychainPassword,
                            keychainLocation]
                    ))
                } else {
                    l.warn("Seems that no keychain parameters are provided. Skipping unlocking the keychain.")
                }
            }
        }
        task.dependsOn(project.readProjectConfiguration)
    }

    def void prepareCopyMobileProvisionTask(Project project) {
        def task = project.task('copyMobileProvision')
        task.description = "Copies mobile provision file to the user library"
        task.group = AMEBA_BUILD
        task << {
            def userHome = System.getProperty("user.home")
            def mobileProvisionDirectory = userHome + "/Library/MobileDevice/Provisioning Profiles/"
            new File(mobileProvisionDirectory).mkdirs()
            ant.copy(todir: mobileProvisionDirectory, overwrite: true) {
                fileset(dir: iosConf.distributionDirectory) { include(name: "*.mobileprovision") }
            }
        }
        task.dependsOn(project.readProjectConfiguration)
    }

    void prepareCopySourcesTask(Project project) {
        def task = project.task('copySources')
        task.description = "Copies all sources to tmp directories for build"
        task.group = AMEBA_BUILD
        task << {
            iosConf.allTargets.each { target ->
                iosConf.allConfigurations.each { configuration ->
                    if (!iosConf.isBuildExcluded(target + "-" + configuration)) {
                        project.ant.sync(toDir: iosSingleVariantBuilder.tmpDir(target, configuration),
                                failonerror: false, overwrite: true, verbose: false) {
                            fileset(dir: "${project.rootDir}/") {
                                exclude(name: iosSingleVariantBuilder.tmpDir(target, configuration).absolutePath + '/**/*')
                                conf.sourceExcludes.each { exclude(name: it) }
                            }
                        }
                    }
                }
            }
        }
    }

    void prepareCopyDebugSourcesTask(Project project) {
        def task = project.task('copyDebugSources')
        task.description = "Copies all debug sources to tmp directories for build"
        task.group = AMEBA_BUILD
        def debugConfiguration = 'Debug'
        task << {
            iosConf.allTargets.each { target ->
                project.ant.sync(toDir: iosSingleVariantBuilder.tmpDir(target, debugConfiguration),
                        failonerror: false, overwrite: true, verbose: false) {
                    fileset(dir: "${project.rootDir}/") {
                        exclude(name: iosSingleVariantBuilder.tmpDir(target, debugConfiguration).absolutePath + '/**/*')
                        conf.sourceExcludes.each { exclude(name: it) }
                    }
                }
            }
        }
    }

    static public final String DESCRIPTION =
        """This is the main iOS build plugin.

The plugin provides all the task needed to build iOS application.
Besides tasks explained below, the plugin prepares build-*
tasks which are dynamically created, based on targets and configurations available.
There is one task available per each Target-Configuration combination - unless particular
combination is excluded by the exclude property."""
}
