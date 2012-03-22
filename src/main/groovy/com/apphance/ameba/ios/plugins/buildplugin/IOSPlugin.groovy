package com.apphance.ameba.ios.plugins.buildplugin;


import groovy.io.FileType

import javax.xml.parsers.DocumentBuilderFactory

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import com.apphance.ameba.AmebaCommonBuildTaskGroups
import com.apphance.ameba.ProjectConfiguration
import com.apphance.ameba.ProjectHelper
import com.apphance.ameba.PropertyCategory
import com.apphance.ameba.XMLBomAwareFileReader
import com.apphance.ameba.ios.IOSProjectConfiguration
import com.apphance.ameba.ios.IOSXCodeOutputParser
import com.apphance.ameba.plugins.projectconfiguration.ProjectConfigurationPlugin
import com.sun.org.apache.xpath.internal.XPathAPI

/**
 * Plugin for various X-Code related tasks.
 * Requires plistFileName set in project properties
 * (set to point to main project .plist file)
 *
 */
class IOSPlugin implements Plugin<Project> {

    static final String IOS_CONFIGURATION_LOCAL_PROPERTY = 'ios.configuration'
    static final String IOS_TARGET_LOCAL_PROPERTY = 'ios.target'

    static Logger logger = Logging.getLogger(IOSPlugin.class)

    String pListFileName
    ProjectHelper projectHelper
    ProjectConfiguration conf
    IOSProjectConfiguration iosConf
    IOSSingleVariantBuilder iosSingleVariantBuilder

    public static final List<String> FAMILIES = ['iPad', 'iPhone']

    def void apply (Project project) {
        ProjectHelper.checkAllPluginsAreLoaded(project, this.class, ProjectConfigurationPlugin.class)
        use (PropertyCategory) {
            this.projectHelper = new ProjectHelper();
            this.conf = project.getProjectConfiguration()
            this.iosConf = IOSXCodeOutputParser.getIosProjectConfiguration(project)
            this.iosSingleVariantBuilder = new IOSSingleVariantBuilder(project, project.ant)
            prepareCopySourcesTask(project)
            prepareCopyDebugSourcesTask(project)
            prepareReadIosProjectConfigurationTask(project)
            prepareReadIosTargetsAndConfigurationsTask(project)
            prepareReadIosProjectVersionsTask(project)
            prepareCleanTask(project)
            prepareUnlockKeyChainTask(project)
            prepareCopyMobileProvisionTask(project)
            prepareBuildSingleVariantTask(project)
            project.task('buildAllSimulators', type: IOSBuildAllSimulatorsTask)
            prepareBuildAllTask(project)
            prepareReplaceBundleIdPrefixTask(project)
            addIosSourceExcludes()
            project.prepareSetup.prepareSetupOperations << new PrepareIOSSetupOperation()
            project.verifySetup.verifySetupOperations << new  VerifyIOSSetupOperation()
            project.showSetup.showSetupOperations << new ShowIOSSetupOperation()
        }
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
        use (PropertyCategory) {
            readBasicIosProjectProperties(project)
            iosConf.sdk = project.readProperty(IOSProjectProperty.IOS_SDK)
            iosConf.simulatorsdk = project.readProperty(IOSProjectProperty.IOS_SIMULATOR_SDK)
            iosConf.plistFile = pListFileName == null ? null : project.file(pListFileName)
            String distDirName = project.readProperty(IOSProjectProperty.DISTRIBUTION_DIR)
            iosConf.distributionDirectory = distDirName == null ? null : project.file( distDirName)
            iosConf.families = project.readProperty(IOSProjectProperty.IOS_FAMILIES).split(",")*.trim()
            if (iosConf.plistFile != null) {
                conf.commitFilesOnVCS << iosConf.plistFile.absolutePath
            }
        }
    }

    private readBasicIosProjectProperties(Project project) {
        use (PropertyCategory) {
            this.pListFileName = project.readProperty(IOSProjectProperty.PLIST_FILE)
            if (project.readProperty(IOSProjectProperty.PROJECT_DIRECTORY) != null) {
                iosConf.xCodeProjectDirectory  = new File(project.readProperty(IOSProjectProperty.PROJECT_DIRECTORY))
            }
            iosConf.excludedBuilds = project.readProperty(IOSProjectProperty.EXCLUDED_BUILDS).split(",")*.trim()
            iosConf.mainTarget = project.readProperty(IOSProjectProperty.MAIN_TARGET)
            iosConf.mainConfiguration = project.readProperty(IOSProjectProperty.MAIN_CONFIGURATION)
        }
    }

    def void prepareReadIosTargetsAndConfigurationsTask(Project project) {
        def task = project.task('readIOSParametersFromXcode')
        task.group = AmebaCommonBuildTaskGroups.AMEBA_CONFIGURATION
        task.description = 'Reads iOS xCode project parameters'
        task << {
            project.file("bin").mkdirs()
            if (iosConf.targets == ['']) {
                logger.lifecycle("Please specify at least one target")
                iosConf.targets = []
            }
            if (iosConf.configurations == ['']) {
                logger.lifecycle("Please specify at least one configuration")
                iosConf.configurations = []
            }
            if (iosConf.mainTarget == null) {
                iosConf.mainTarget = iosConf.targets.empty? null : iosConf.targets[0]
            }
            if (iosConf.mainConfiguration == null) {
                iosConf.mainConfiguration = iosConf.configurations.empty ? null : iosConf.configurations[0]
            }
            logger.lifecycle("Standard buildable targets: " + iosConf.targets)
            logger.lifecycle("Standard buildable configurations : " + iosConf.configurations)
            logger.lifecycle("Main target: " + iosConf.mainTarget)
            logger.lifecycle("Main configuration : " + iosConf.mainConfiguration)
            logger.lifecycle("All targets: " + iosConf.alltargets)
            logger.lifecycle("All configurations : " + iosConf.allconfigurations)
        }
        project.readProjectConfiguration.dependsOn(task)
    }

    private readProjectConfigurationFromXCode(Project project) {
        use (PropertyCategory) {
            if (project.readProperty(IOSProjectProperty.PROJECT_DIRECTORY) != null) {
                iosConf.xCodeProjectDirectory  = new File(project.readProperty(IOSProjectProperty.PROJECT_DIRECTORY))
            }
        }
        def cmd = (iosConf.getXCodeBuildExecutionPath() + ["-list"]) as String []
        def trimmedListOutput = projectHelper.executeCommand(project, cmd, false, null, null, 1, false)*.trim()
        if (trimmedListOutput.empty || trimmedListOutput[0] == '') {
            throw new GradleException("Error while running ${cmd}:")
        } else {
            IOSProjectConfiguration iosConf = IOSXCodeOutputParser.getIosProjectConfiguration(project)
            project.ext[ProjectConfigurationPlugin.PROJECT_NAME_PROPERTY] =  IOSXCodeOutputParser.readProjectName(trimmedListOutput)
            iosConf.targets = IOSXCodeOutputParser.readBuildableTargets(trimmedListOutput)
            iosConf.configurations = IOSXCodeOutputParser.readBuildableConfigurations(trimmedListOutput)
            iosConf.alltargets = IOSXCodeOutputParser.readBaseTargets(trimmedListOutput, { true })
            iosConf.allconfigurations = IOSXCodeOutputParser.readBaseConfigurations(trimmedListOutput, { true })
            def trimmedSdkOutput = projectHelper.executeCommand(project, (iosConf.getXCodeBuildExecutionPath() + ["-showsdks"]) as String[],false, null, null, 1, true)*.trim()
            iosConf.allIphoneSDKs = IOSXCodeOutputParser.readIphoneSdks(trimmedSdkOutput)
            iosConf.allIphoneSimulatorSDKs = IOSXCodeOutputParser.readIphoneSimulatorSdks(trimmedSdkOutput)
        }
    }


    void prepareReadIosProjectVersionsTask(Project project) {
        def task = project.task('readIOSProjectVersions')
        task.group = AmebaCommonBuildTaskGroups.AMEBA_CONFIGURATION
        task.description = 'Reads iOS project version information'
        task << {
            use (PropertyCategory) {
                this.pListFileName = project.readProperty(IOSProjectProperty.PLIST_FILE)
                def root = getParsedPlist(project)
                if (root != null) {
                    XPathAPI.selectNodeList(root,
                                    '/plist/dict/key[text()="CFBundleShortVersionString"]').each{
                                        conf.versionString =  it.nextSibling.nextSibling.textContent
                                    }
                    XPathAPI.selectNodeList(root,
                                    '/plist/dict/key[text()="CFBundleVersion"]').each{
                                        def versionCodeString = it.nextSibling.nextSibling.textContent
                                        try {
                                            conf.versionCode = versionCodeString.toLong()
                                        } catch (NumberFormatException e) {
                                            logger.lifecycle("Format of the ${versionCodeString} is not numeric. Starting from 1.")
                                            conf.versionCode = 0
                                        }
                                    }
                    if (!project.isPropertyOrEnvironmentVariableDefined('version.string')) {
                        logger.lifecycle("Version string is updated to SNAPSHOT because it is not release build")
                        conf.versionString = conf.versionString + "-SNAPSHOT"
                    } else {
                        logger.lifecycle("Version string is not updated to SNAPSHOT because it is release build")
                    }
                }
            }
        }
        project.readProjectConfiguration.dependsOn(task)
    }

    void prepareBuildAllTask(Project project) {
        def task = project.task('buildAll')
        task.group = AmebaCommonBuildTaskGroups.AMEBA_BUILD
        task.description = 'Builds all target/configuration combinations and produces all artifacts (zip, ipa, messages, etc)'
        readProjectConfigurationFromXCode(project)
        readBasicIosProjectProperties(project)
        def targets = iosConf.targets
        def configurations = iosConf.configurations
        println("Preparing all build tasks")
        targets.each { target ->
            configurations.each { configuration ->
                def id = "${target}-${configuration}".toString()
                if (!iosConf.isBuildExcluded(id)) {
                    def noSpaceId = id.replaceAll(' ','_')
                    def singleTask = project.task("build-${noSpaceId}")
                    singleTask.group = AmebaCommonBuildTaskGroups.AMEBA_BUILD
                    singleTask.description = "Builds target:${target} configuration:${configuration}"
                    singleTask << {
                        def singleVariantBuilder = new IOSSingleVariantBuilder(project, project.ant)
                        singleVariantBuilder.buildNormalVariant(project, target, configuration)
                    }
                    task.dependsOn(singleTask)
                    singleTask.dependsOn(project.readProjectConfiguration, project.copyMobileProvision, project.verifySetup, project.copySources)
                } else {
                    println ("Skipping build ${id} - it is excluded in configuration (${iosConf.excludedBuilds})")
                }
            }
        }
        task.dependsOn(project.readProjectConfiguration)
    }


    private org.w3c.dom.Element getParsedPlist(Project project) {
        if (pListFileName == null) {
            return null
        }
        File pListFile = new File("${project.rootDir}/${pListFileName}")
        if (!pListFile.exists() || !pListFile.isFile()) {
            return null
        }
        return new XMLBomAwareFileReader().readXMLFileIncludingBom(pListFile)
    }


    private org.w3c.dom.Element getParsedPlist(File file) {
        def builderFactory = DocumentBuilderFactory.newInstance()
        builderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        builderFactory.setFeature("http://xml.org/sax/features/validation", false)
        def builder = builderFactory.newDocumentBuilder()
        return new XMLBomAwareFileReader().readXMLFileIncludingBom(file)
    }

    def void prepareBuildSingleVariantTask(Project project) {
        def task = project.task('buildSingleVariant')
        task.group = AmebaCommonBuildTaskGroups.AMEBA_BUILD
        task.description = "Builds single variant for iOS. Requires ios.target and ios.configuration properties"
        task << {
            use (PropertyCategory) {
                def singleVariantBuilder = new IOSSingleVariantBuilder(project, this.ant)
                String target = project.readExpectedProperty(IOS_TARGET_LOCAL_PROPERTY)
                String configuration = project.readExpectedProperty(IOS_CONFIGURATION_LOCAL_PROPERTY)
                singleVariantBuilder.buildNormalVariant(project, target, configuration)
            }
        }
        task.dependsOn(project.readProjectConfiguration, project.verifySetup, project.copySources)
    }


    def void prepareCleanTask(Project project) {
        def task = project.task('clean')
        task.description = "Cleans the project"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_BUILD
        task << {
            projectHelper.executeCommand(project, ["dot_clean", "./"]as String [])
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
              or OSX_KEYCHAIN_PASSWORD and OSX_KEYCHAIN_LOCATION"""
        task.group = AmebaCommonBuildTaskGroups.AMEBA_BUILD
        task << {
            use(PropertyCategory) {
                def keychainPassword = project.readPropertyOrEnvironmentVariable("osx.keychain.password")
                def keychainLocation = project.readPropertyOrEnvironmentVariable("osx.keychain.location")
                projectHelper.executeCommand(project, [
                    "security",
                    "unlock-keychain",
                    "-p",
                    keychainPassword,
                    keychainLocation
                ])
            }
        }
        task.dependsOn(project.readProjectConfiguration)
    }

    def void prepareCopyMobileProvisionTask(Project project) {
        def task = project.task('copyMobileProvision')
        task.description = "Copies mobile provision file to the user library"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_BUILD
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

    private void prepareReplaceBundleIdPrefixTask(Project project) {
        def task = project.task('replaceBundleIdPrefix')
        task.description = """Replaces bundleId prefix with a new one. Requires oldBundleIdPrefix and newBundleIdPrefix
           parameters. The .mobileprovision files need to be in 'newBundleIdPrefix' sub-directory of distribution directory"""
        task.group = AmebaCommonBuildTaskGroups.AMEBA_BUILD
        task << {
            use (PropertyCategory) {
                def oldBundleIdPrefix = project.readExpectedProperty("oldBundleIdPrefix")
                logger.lifecycle("Old bundleId ${oldBundleIdPrefix}")
                def newBundleIdPrefix = project.readExpectedProperty("newBundleIdPrefix")
                logger.lifecycle("New bundleId ${newBundleIdPrefix}")
                replaceBundleInAllPlists(project, newBundleIdPrefix, oldBundleIdPrefix)
                replaceBundleInAllSourceFiles(project, newBundleIdPrefix, oldBundleIdPrefix)
                iosConf.distributionDirectory = new File(iosConf.distributionDirectory, newBundleIdPrefix)
                logger.lifecycle("New distribution directory: ${iosConf.distributionDirectory}")
                logger.lifecycle("Replaced the bundleIdprefix everywhere")
            }
        }
    }

    private void replaceBundleInAllPlists(Project project, String newBundleIdPrefix, String oldBundleIdPrefix) {
        logger.lifecycle("Finding all plists")
        def plistFiles = findAllPlistFiles(project)
        plistFiles.each {  file ->
            def root = getParsedPlist(file)
            XPathAPI.selectNodeList(root,'/plist/dict/key[text()="CFBundleIdentifier"]').each {
                String bundleToReplace = it.nextSibling.nextSibling.textContent
                if (bundleToReplace.startsWith(oldBundleIdPrefix)) {
                    String newResult = newBundleIdPrefix + bundleToReplace.substring(oldBundleIdPrefix.length())
                    it.nextSibling.nextSibling.textContent  = newResult
                    file.write(root as String)
                    logger.lifecycle("Replaced the bundleId to ${newResult} from ${bundleToReplace} in ${file}")
                } else if (bundleToReplace.startsWith(newBundleIdPrefix)) {
                    logger.lifecycle("Already replaced the bundleId to ${bundleToReplace} in ${file}")
                } else {
                    throw new GradleException("The bundle to replace ${bundleToReplace} does not start with expected ${oldBundleIdPrefix} in ${file}. Not replacing !!!!!!!.")
                }
            }
        }
        logger.lifecycle("Finished processing all plists")
    }

    private void replaceBundleInAllSourceFiles(Project project, String newBundleIdPrefix, String oldBundleIdPrefix) {
        logger.lifecycle("Finding all source files")
        def sourceFiles = findAllSourceFiles(project)
        String valueToFind = 'bundleWithIdentifier:@"' + oldBundleIdPrefix
        String valueToReplace = 'bundleWithIdentifier:@"' + newBundleIdPrefix
        sourceFiles.each {  file ->
            String t = file.text
            if (t.contains(valueToFind)) {
                file.write(t.replace(valueToFind, valueToReplace))
                logger.lifecycle("Replaced the ${valueToFind} with ${valueToReplace} in ${file}")
            }
        }
        logger.lifecycle("Finished processing all source files")
    }

    Collection<File> findAllPlistFiles(Project project) {
        def result = []
        project.rootDir.traverse([type: FileType.FILES, maxDepth : ProjectHelper.MAX_RECURSION_LEVEL]) {
            if (it.name.endsWith("-Info.plist") && !it.path.contains("/External/") && !it.path.contains('/build/')) {
                logger.lifecycle("Adding plist file ${it} to processing list")
                result << it
            }
        }
        return result
    }

    Collection<File> findAllSourceFiles(Project project) {
        def result = []
        project.rootDir.traverse([type: FileType.FILES, maxDepth : ProjectHelper.MAX_RECURSION_LEVEL]) {
            if ((it.name.endsWith(".m") || it.name.endsWith(".h")) && !it.path.contains("/External/")) {
                logger.lifecycle("Adding source file ${it} to processing list")
                result << it
            }
        }
        return result
    }

    void prepareCopySourcesTask(Project project) {
        def task = project.task('copySources')
        task.description = "Copies all sources to tmp directories for build"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_BUILD
        task << {
            iosConf.alltargets.each { target ->
                iosConf.allconfigurations.each { configuration ->
                    if (!iosConf.isBuildExcluded(target + "-" + configuration)) {
                        new AntBuilder().sync(toDir : iosSingleVariantBuilder.tmpDir(target, configuration), failonerror: false, overwrite:true, verbose:true) {
                            fileset(dir : "${project.rootDir}/") {
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
        task.group = AmebaCommonBuildTaskGroups.AMEBA_BUILD
        def debugConfiguration = 'Debug'
        task << {
            iosConf.alltargets.each { target ->
                new AntBuilder().sync(toDir : iosSingleVariantBuilder.tmpDir(target, debugConfiguration), failonerror: false, overwrite:true, verbose:true) {
                    fileset(dir : "${project.rootDir}/") {
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
