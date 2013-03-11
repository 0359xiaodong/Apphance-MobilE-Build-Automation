package com.apphance.ameba.ios.plugins.apphance

import com.apphance.ameba.PluginHelper
import com.apphance.ameba.ProjectConfiguration
import com.apphance.ameba.PropertyCategory
import com.apphance.ameba.android.plugins.test.ApphanceNetworkHelper
import com.apphance.ameba.apphance.*
import com.apphance.ameba.executor.command.Command
import com.apphance.ameba.executor.command.CommandExecutor
import com.apphance.ameba.ios.IOSProjectConfiguration
import com.apphance.ameba.ios.plugins.buildplugin.IOSPlugin
import com.apphance.ameba.ios.plugins.buildplugin.IOSSingleVariantBuilder
import com.apphance.ameba.ios.plugins.release.IOSReleaseConfigurationRetriever
import com.apphance.ameba.plugins.release.ProjectReleaseCategory
import com.apphance.ameba.util.Preconditions
import groovy.json.JsonSlurper
import org.apache.http.util.EntityUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

import javax.inject.Inject

import static com.apphance.ameba.AmebaCommonBuildTaskGroups.AMEBA_APPHANCE_SERVICE
import static com.apphance.ameba.apphance.ApphanceProperty.APPLICATION_KEY
import static com.apphance.ameba.util.file.FileManager.MAX_RECURSION_LEVEL
import static groovy.io.FileType.DIRECTORIES
import static groovy.io.FileType.FILES
import static java.io.File.separator
import static org.gradle.api.logging.Logging.getLogger

/**
 * Plugin for all apphance-relate IOS tasks.
 *
 */
@Mixin(Preconditions)
@Mixin(ApphancePluginUtil)
class IOSApphancePlugin implements Plugin<Project> {

    def l = getLogger(getClass())
    static final FRAMEWORK_PATTERN = ~/.*[aA]pphance.*\.framework/

    @Inject
    CommandExecutor executor

    ProjectConfiguration conf
    IOSProjectConfiguration iosConf
    PbxProjectHelper pbxProjectHelper

    @Override
    public void apply(Project project) {
        PluginHelper.checkAllPluginsAreLoaded(project, this.class, IOSPlugin.class)
        use(PropertyCategory) {
            this.conf = project.getProjectConfiguration()
            this.iosConf = project.ext.get(IOSPlugin.IOS_PROJECT_CONFIGURATION)
            this.pbxProjectHelper = new PbxProjectHelper(project.properties['apphance.lib'], project.properties['apphance.mode'])

            addApphanceConfiguration(project)
            preProcessBuildsWithApphance(project)

            project.prepareSetup.prepareSetupOperations << new PrepareApphanceSetupOperation()
            project.verifySetup.verifySetupOperations << new VerifyApphanceSetupOperation()
            project.showSetup.showSetupOperations << new ShowApphancePropertiesOperation()
        }
    }

    private void preProcessBuildsWithApphance(Project project) {
        iosConf.allBuildableVariants.each { v ->
            def buildTask = project."build-${v.id}"
            addApphanceToTask(project, buildTask, v.id, v.target, v.configuration, iosConf)
            prepareSingleBuildUpload(project, buildTask, v, conf)
        }
        if (project.hasProperty('buildAllSimulators')) {
            addApphanceToTask(project, project.buildAllSimulators, "${this.iosConf.mainTarget}-Debug", this.iosConf.mainTarget, 'Debug', iosConf)
        }
    }

    private addApphanceToTask(Project project, Task buildTask, String variant, String target, String configuration, IOSProjectConfiguration projConf) {
        buildTask.doFirst {
            def builder = new IOSSingleVariantBuilder(project, executor)
            if (!isApphancePresent(builder.tmpDir(target, configuration))) {
                l.info("Adding Apphance to ${variant} (${target}, ${configuration}): ${builder.tmpDir(target, configuration)}. Project file = ${projConf.xCodeProjectDirectories[variant]}")
                pbxProjectHelper.addApphanceToProject(builder.tmpDir(target, configuration),
                        projConf.xCodeProjectDirectories[variant], target, configuration, project[APPLICATION_KEY.propertyName])
                copyApphanceFramework(project, builder.tmpDir(target, configuration))
            }
        }
    }

    boolean isApphancePresent(File projectDir) {
        l.lifecycle("Looking for apphance in: ${projectDir.absolutePath}")

        def apphancePresent = false

        projectDir.traverse([type: DIRECTORIES, maxDepth: MAX_RECURSION_LEVEL]) { file ->
            if (file.name =~ FRAMEWORK_PATTERN) {
                apphancePresent = true
            }
        }

        apphancePresent ?
            l.lifecycle("Apphance already in project") :
            l.lifecycle("Apphance not in project")

        apphancePresent
    }

    private copyApphanceFramework(Project project, File libsDir) {

        def apphanceLibDependency = prepareApphanceLibDependency(project, 'com.apphance:ios.pre-production.armv7:1.8+')

        libsDir.mkdirs()
        clearLibsDir(libsDir)
        l.lifecycle("Copying apphance framework directory " + libsDir)

        try {
            project.copy {
                from { project.configurations.apphance }
                into libsDir
                rename { String filename ->
                    'apphance.zip'
                }
            }
        } catch (e) {
            def msg = "Error while resolving dependency: '$apphanceLibDependency'"
            l.error("""$msg.
To solve the problem add correct dependency to gradle.properties file or add -Dapphance.lib=<apphance.lib> to invocation.
Dependency should be added in gradle style to 'apphance.lib' entry""")
            throw new GradleException(msg)
        }

        def projectApphanceZip = new File(libsDir, "apphance.zip")
        l.lifecycle("Unpacking file " + projectApphanceZip)
        l.lifecycle("Exists " + projectApphanceZip.exists())
        executor.executeCommand(new Command(runDir: project.rootDir,
                cmd: ['unzip', projectApphanceZip.canonicalPath, '-d', libsDir.canonicalPath]))

        checkFrameworkFolders(apphanceLibDependency, libsDir)

        project.delete {
            projectApphanceZip
        }
    }

    private clearLibsDir(File libsDir) {
        libsDir.traverse([type: FILES, maxDepth: MAX_RECURSION_LEVEL]) { framework ->
            if (framework.name =~ FRAMEWORK_PATTERN) {
                l.lifecycle("Removing old apphance framework: " + framework.name)
                delClos(new File(framework.canonicalPath))
            }
        }
    }

    def delClos = {
        it.eachDir(delClos);
        it.eachFile {
            it.delete()
        }
    }

    private void checkFrameworkFolders(String apphanceLib, File libsDir) {
        def libVariant = apphanceLib.split(':')[1].split('\\.')[1].replace('p', 'P')
        def frameworkFolder = "Apphance-${libVariant}.framework"
        def frameworkFolderFile = new File(libsDir.canonicalPath + separator + frameworkFolder)
        if (!frameworkFolderFile.exists() || !frameworkFolderFile.isDirectory() || !(frameworkFolderFile.length() > 0l)) {
            throw new GradleException("There is no framework folder (or may be empty): ${frameworkFolderFile.canonicalPath} associated with apphance version: '${apphanceLib}'")
        }
    }

    void prepareSingleBuildUpload(Project project, Task buildTask, Expando e, ProjectConfiguration conf) {

        def uploadTask = project.task("upload-${e.noSpaceId}")

        uploadTask.description = 'Uploads ipa, dsym & image_montage to Apphance server'
        uploadTask.group = AMEBA_APPHANCE_SERVICE

        uploadTask << {

            def builder = new IOSSingleVariantBuilder(project, executor)
            builder.buildSingleBuilderInfo(e.target, e.configuration, 'iphoneos', project)
            def iOSReleaseConf = IOSReleaseConfigurationRetriever.getIosReleaseConfiguration(project)
            def releaseConf = ProjectReleaseCategory.getProjectReleaseConfiguration(project)

            //TODO gradle.properties
            String user = project['apphanceUserName']
            String pass = project['apphancePassword']
            //TODO gradle.properties
            String key = project[ApphanceProperty.APPLICATION_KEY.propertyName]

            def networkHelper

            try {
                networkHelper = new ApphanceNetworkHelper(user, pass)

                def response = networkHelper.updateArtifactQuery(key, conf.versionString, conf.versionCode, false, ['ipa', 'dsym', 'image_montage'])
                l.lifecycle("Upload version query response: ${response.statusLine}")

                throwIf(!response.entity, "Error while uploading version query, empty response received")

                def responseJSON = new JsonSlurper().parseText(response.entity.content.text)

                response = networkHelper.uploadResource(iOSReleaseConf.ipaFiles[e.id].location, responseJSON.update_urls.ipa, 'ipa')
                l.lifecycle("Upload ipa response: ${response.statusLine}")
                EntityUtils.consume(response.entity)

                response = networkHelper.uploadResource(releaseConf.imageMontageFile.location, responseJSON.update_urls.image_montage, 'image_montage')
                l.lifecycle("Upload image_montage response: ${response.statusLine}")
                EntityUtils.consume(response.entity)

                //TODO turn on after DI is implemented
//                iOSReleaseConf.ahSYMDirs[e.id].location.list([accept: { d, n -> def f = new File(d, n); n.endsWith("ahsym") && f.isFile() && f.exists() }] as FilenameFilter).each { ahSYM ->
//                    response = networkHelper.uploadResource(new File(iOSReleaseConf.ahSYMDirs[e.id].location, ahSYM), responseJSON.update_urls.dsym, 'dsym')
//                    l.lifecycle("Upload ahsym ($ahSYM) response: ${response.statusLine}")
//                    EntityUtils.consume(response.entity)
//                }

            } catch (err) {
                def msg = "Error while uploading artifact to apphance: ${err.message}"
                l.error(msg)
                throw new GradleException(msg)
            } finally {
                networkHelper?.closeConnection()
            }
        }

        uploadTask.dependsOn(buildTask)
        uploadTask.dependsOn('prepareImageMontage')
    }

    static public final String DESCRIPTION =
        """This plugins provides automated adding of Apphance libraries to the project.
"""
}
