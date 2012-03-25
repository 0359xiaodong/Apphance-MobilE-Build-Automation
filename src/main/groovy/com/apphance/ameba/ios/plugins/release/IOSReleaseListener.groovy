package com.apphance.ameba.ios.plugins.release

import groovy.text.SimpleTemplateEngine

import java.io.File
import java.net.URL

import org.gradle.api.AntBuilder
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import com.apphance.ameba.ProjectConfiguration
import com.apphance.ameba.ProjectHelper
import com.apphance.ameba.PropertyCategory
import com.apphance.ameba.ios.IOSBuilderInfo
import com.apphance.ameba.ios.IOSProjectConfiguration
import com.apphance.ameba.ios.IOSXCodeOutputParser
import com.apphance.ameba.ios.MPParser
import com.apphance.ameba.ios.plugins.buildplugin.IOSBuildListener
import com.apphance.ameba.ios.plugins.buildplugin.IOSSingleVariantBuilder
import com.apphance.ameba.plugins.release.AmebaArtifact
import com.apphance.ameba.plugins.release.ProjectReleaseCategory
import com.apphance.ameba.plugins.release.ProjectReleaseConfiguration

/**
 * Build listener for releases.
 *
 */
class IOSReleaseListener implements IOSBuildListener {

    ProjectHelper projectHelper
    ProjectConfiguration conf
    ProjectReleaseConfiguration releaseConf
    IOSProjectConfiguration iosConf
    IOSReleaseConfiguration iosReleaseConf
    AntBuilder ant

    static Logger logger = Logging.getLogger(IOSReleaseListener.class)


    IOSReleaseListener(Project project, AntBuilder ant) {
        use (PropertyCategory) {
            this.projectHelper = new ProjectHelper()
            this.conf = project.getProjectConfiguration()
            this.releaseConf = ProjectReleaseCategory.getProjectReleaseConfiguration(project)
            this.iosConf = IOSXCodeOutputParser.getIosProjectConfiguration(project)
            this.iosReleaseConf = IOSReleaseConfigurationRetriever.getIosReleaseConfiguration(project)
            this.ant = ant
        }
    }

    String getFolderPrefix(IOSBuilderInfo bi) {
        return "${releaseConf.projectDirectoryName}/${conf.fullVersionString}/${bi.target}/${bi.configuration}"
    }

    public void buildDone(Project project, IOSBuilderInfo bi) {
        if (conf.versionString != null) {
            if (bi.configuration != 'Debug') {
                prepareDistributionZipFile(project, bi)
                prepareDSYMZipFile(project, bi)
                prepareIpaFile(project, bi)
                prepareManifestFile(project,bi)
                prepareMobileProvisionFile(project,bi)
            } else {
                iosConf.families.each { family ->
                    prepareSimulatorBundleFile(project,bi,family)
                }
            }
        } else {
            logger.lifecycle("Skipping building artifacts -> the build is not versioned")
        }
    }

    private void prepareDistributionZipFile(Project project, IOSBuilderInfo bi) {
        AmebaArtifact distributionZipArtifact = prepareDistributionZipArtifact(bi)
        distributionZipArtifact.location.parentFile.mkdirs()
        distributionZipArtifact.location.delete()
        ant.zip(destfile: distributionZipArtifact.location) {
            zipfileset(dir: iosConf.distributionDirectory,
                            includes: IOSXCodeOutputParser.findMobileProvisionFile(project, bi.target, bi.configuration).name)
            zipfileset(dir: bi.buildDirectory , includes: "${bi.target}.app/**")
        }
        logger.lifecycle("Distribution zip file created: ${distributionZipArtifact}")
    }

    private void prepareDSYMZipFile(Project project, IOSBuilderInfo bi) {
        AmebaArtifact dSYMZipArtifact = prepareDSYMZipArtifact(bi)
        dSYMZipArtifact.location.parentFile.mkdirs()
        dSYMZipArtifact.location.delete()
        ant.zip(destfile: dSYMZipArtifact.location) {
            zipfileset(dir: bi.buildDirectory , includes: "${bi.target}.app.dSYM/**")
        }
        logger.lifecycle("dSYM zip file created: ${dSYMZipArtifact}")
    }


    private void prepareIpaFile(Project project, IOSBuilderInfo bi) {
        AmebaArtifact ipaArtifact = prepareIpaArtifact(bi)
        ipaArtifact.location.parentFile.mkdirs()
        ipaArtifact.location.delete()
        String[] command = [
            "/usr/bin/xcrun",
            "-sdk",
            iosConf.sdk,
            "PackageApplication",
            "-v",
            new File(bi.buildDirectory,"${bi.target}.app"),
            "-o",
            ipaArtifact.location,
            "--embed",
            bi.mobileprovisionFile
        ]
        projectHelper.executeCommand(project,command)
        logger.lifecycle("ipa file created: ${ipaArtifact}")
    }


    private void prepareManifestFile(Project project, IOSBuilderInfo bi) {
        AmebaArtifact manifestArtifact = prepareManifestArtifact(bi)
        manifestArtifact.location.parentFile.mkdirs()
        manifestArtifact.location.delete()

        URL manifestTemplate = this.class.getResource("manifest.plist")
        SimpleTemplateEngine engine = new SimpleTemplateEngine()
        def binding = [
                            ipaUrl : iosReleaseConf.ipaFiles.get(bi.id).url,
                            title : bi.target,
                            bundleId : MPParser.readBundleIdFromPlist(bi.plistFile.toURI().toURL())
                        ]
        def result = engine.createTemplate(manifestTemplate).make(binding)
        manifestArtifact.location << (result.toString())
        logger.lifecycle("Manifest file created: ${manifestArtifact}")
    }

    private void prepareMobileProvisionFile(Project project, IOSBuilderInfo bi) {
        AmebaArtifact mobileProvisionArtifact = prepareMobileProvisionArtifact(bi)
        mobileProvisionArtifact.location.parentFile.mkdirs()
        mobileProvisionArtifact.location.delete()
        mobileProvisionArtifact.location << bi.mobileprovisionFile.text
        logger.lifecycle("Mobile provision file created: ${mobileProvisionArtifact}")
    }


    private AmebaArtifact prepareDistributionZipArtifact(IOSBuilderInfo bi, boolean checkIfExists = false) {
        AmebaArtifact distributionZipArtifact = new AmebaArtifact(
                        name : "Distribution zip",
                        url : new URL(releaseConf.baseUrl, "${getFolderPrefix(bi)}/${bi.filePrefix}.zip"),
                        location : new File(releaseConf.otaDirectory,"${getFolderPrefix(bi)}/${bi.filePrefix}.zip"))
        if (!checkIfExists || distributionZipArtifact.location.exists()) {
            iosReleaseConf.distributionZipFiles.put(bi.id,distributionZipArtifact)
        } else {
            logger.lifecycle("Skipping preparing distribution zip for ${bi} -> missing")
        }
        return distributionZipArtifact
    }


    private AmebaArtifact prepareDSYMZipArtifact(IOSBuilderInfo bi, boolean checkIfExists = false) {
        AmebaArtifact dSYMZipArtifact = new AmebaArtifact(
                        name : "dSYM zip",
                        url : new URL(releaseConf.baseUrl, "${getFolderPrefix(bi)}/${bi.filePrefix}_dSYM.zip"),
                        location : new File(releaseConf.otaDirectory,"${getFolderPrefix(bi)}/${bi.filePrefix}_dSYM.zip"))
        if (!checkIfExists || dSYMZipArtifact.location.exists() ) {
            iosReleaseConf.dSYMZipFiles.put(bi.id,dSYMZipArtifact)
        } else {
            logger.lifecycle("Skipping preparing dSYM artifact for ${bi.id} : ${dSYMZipArtifact.location} -> missing")
        }
        return dSYMZipArtifact
    }


    private AmebaArtifact prepareIpaArtifact(IOSBuilderInfo bi, boolean checkIfExists = false) {
        AmebaArtifact ipaArtifact = new AmebaArtifact(
                        name : "The ipa file",
                        url : new URL(releaseConf.baseUrl, "${getFolderPrefix(bi)}/${bi.filePrefix}.ipa"),
                        location : new File(releaseConf.otaDirectory,"${getFolderPrefix(bi)}/${bi.filePrefix}.ipa"))
        if (!checkIfExists || ipaArtifact.location.exists()) {
            iosReleaseConf.ipaFiles.put(bi.id,ipaArtifact)
        } else {
            logger.lifecycle("Skipping preparing ipa artifact for ${bi.id} : ${ipaArtifact.location} -> missing")
        }
        return ipaArtifact
    }

    private AmebaArtifact prepareManifestArtifact(IOSBuilderInfo bi, boolean checkIfExists = false) {
        AmebaArtifact manifestArtifact = new AmebaArtifact(
                        name : "The manifest file",
                        url : new URL(releaseConf.baseUrl, "${getFolderPrefix(bi)}/manifest.plist"),
                        location : new File(releaseConf.otaDirectory,"${getFolderPrefix(bi)}/manifest.plist"))
        if (!checkIfExists || manifestArtifact.location.exists()) {
            iosReleaseConf.manifestFiles.put(bi.id,manifestArtifact)
        } else {
            logger.lifecycle("Skipping preparing manifest artifact for ${bi.id} : ${manifestArtifact.location} -> missing")
        }
        return manifestArtifact
    }

    private AmebaArtifact prepareMobileProvisionArtifact(IOSBuilderInfo bi, boolean checkIfExists = false) {
        AmebaArtifact mobileProvisionArtifact = new AmebaArtifact(
                        name : "The mobile provision file",
                        url : new URL(releaseConf.baseUrl, "${getFolderPrefix(bi)}/${bi.filePrefix}.mobileprovision"),
                        location : new File(releaseConf.otaDirectory,"${getFolderPrefix(bi)}/${bi.filePrefix}.mobileprovision"))
        if (!checkIfExists || mobileProvisionArtifact.location.exists()) {
            iosReleaseConf.mobileProvisionFiles.put(bi.id,mobileProvisionArtifact)
        } else {
            logger.lifecycle("Skipping preparing mobileProvision artifact for ${bi.id} : ${mobileProvisionArtifact.location} -> missing")
        }
        return mobileProvisionArtifact
    }

    void buildArtifactsOnly(Project project, String target, String configuration) {
        if (conf.versionString != null) {
            IOSSingleVariantBuilder builder = new IOSSingleVariantBuilder(project, project.ant)
            IOSBuilderInfo bi = builder.buidSingleBuilderInfo(target, configuration, 'iphoneos', project)
            prepareDistributionZipArtifact(bi, true)
            prepareDSYMZipArtifact(bi, true)
            prepareIpaArtifact(bi, true)
            prepareManifestArtifact(bi, true)
            prepareMobileProvisionArtifact(bi, true)
        } else {
            logger.lifecycle("Skipping building artifacts -> the build is not versioned")
        }
    }

    void prepareSimulatorBundleFile(Project project, IOSBuilderInfo bi, String family) {
        AmebaArtifact file = new AmebaArtifact()
        file.name = "Simulator build for ${family}"
        file.url = new URL(releaseConf.baseUrl, "${getFolderPrefix(bi)}/${bi.filePrefix}-${family}-simulator-image.dmg")
        file.location = new File(releaseConf.otaDirectory,"${getFolderPrefix(bi)}/${bi.filePrefix}-${family}-simulator-image.dmg")
        file.location.parentFile.mkdirs()
        file.location.delete()
        def File tmpDir = File.createTempFile("${conf.projectName}-${bi.target}-${family}-simulator",".tmp")
        tmpDir.delete()
        tmpDir.mkdir()
        def destDir = new File(tmpDir,"${bi.target} (${family}_Simulator).app")
        destDir.mkdir()
        rsyncTemplatePreservingExecutableFlag(project,destDir)
        File embedDir = new File(destDir, "Contents/Resources/EmbeddedApp")
        embedDir.mkdirs()
        File sourceApp = new File(bi.buildDirectory,"${bi.target}.app")
        rsyncEmbeddedAppPreservingExecutableFlag(project,sourceApp, embedDir)
        updateBundleId(project, bi, destDir)
        resampleIcon(project, destDir)
        updateDeviceFamily(family, embedDir, bi, project)
        String [] prepareDmgCommand = [
            "hdiutil",
            "create",
            file.location,
            "-srcfolder",
            destDir,
            "-volname",
            "${conf.projectName}-${bi.target}-${family}"
        ]
        projectHelper.executeCommand(project, prepareDmgCommand)
        iosReleaseConf.dmgImageFiles.put("${family}-${iosConf.mainTarget}" as String,file)
        logger.lifecycle("Simulator zip file created: ${file} for ${family}-${iosConf.mainTarget}")
    }

    private rsyncTemplatePreservingExecutableFlag(Project project, File destDir) {
        String [] rsyncCommand = [
            "rsync",
            "-aE",
            "--exclude",
            "Contents/Resources/EmbeddedApp",
            "/Applications/Simulator Bundler.app/Contents/Resources/Launcher.app/",
            destDir
        ]
        projectHelper.executeCommand(project, rsyncCommand)
    }

    private rsyncEmbeddedAppPreservingExecutableFlag(Project project, File sourceAppDir, File embedDir) {
        String [] rsyncCommand = [
            "rsync",
            "-aE",
            sourceAppDir,
            embedDir
        ]
        projectHelper.executeCommand(project, rsyncCommand)
    }


    private updateDeviceFamily(String device, embedDir, IOSBuilderInfo bi, Project project) {
        File targetPlistFile = new File(embedDir, "${bi.target}.app/Info.plist")
        String [] deleteDeviceFamilyCommand = [
            "/usr/libexec/PlistBuddy",
            "-c",
            "Delete UIDeviceFamily",
            targetPlistFile
        ]
        projectHelper.executeCommand(project, deleteDeviceFamilyCommand, false)
        String [] addDeviceFamilyCommand = [
            "/usr/libexec/PlistBuddy",
            "-c",
            "Add UIDeviceFamily array",
            targetPlistFile
        ]
        projectHelper.executeCommand(project, addDeviceFamilyCommand)
        String family = (device == "iPhone" ? "1" : "2")
        String [] updateDeviceFamilyCommand = [
            "/usr/libexec/PlistBuddy",
            "-c",
            "Add UIDeviceFamily:0 integer ${family}",
            targetPlistFile
        ]
        projectHelper.executeCommand(project, updateDeviceFamilyCommand)
    }

    private resampleIcon(Project project, File tmpDir) {
        String [] iconResampleCommand = [
            "/opt/local/bin/convert",
            releaseConf.iconFile,
            "-resample",
            "128x128",
            new File(tmpDir,"Contents/Resources/Launcher.icns")
        ]
        projectHelper.executeCommand(project, iconResampleCommand)
    }

    private updateBundleId(Project project, IOSBuilderInfo bi, File tmpDir) {
        def bundleId = MPParser.readBundleIdFromProvisionFile(bi.mobileprovisionFile.toURI().toURL())
        String [] setBundleIdCommand = [
            "/usr/libexec/PlistBuddy",
            "-c",
            "Set :CFBundleIdentifier ${bundleId}.launchsim",
            new File(tmpDir,"Contents/Info.plist")
        ]
        projectHelper.executeCommand(project, setBundleIdCommand)
    }
}
