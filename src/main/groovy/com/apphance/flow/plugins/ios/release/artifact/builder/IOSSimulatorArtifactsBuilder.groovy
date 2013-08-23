package com.apphance.flow.plugins.ios.release.artifact.builder

import com.apphance.flow.configuration.ios.IOSFamily
import com.apphance.flow.executor.command.Command
import com.apphance.flow.plugins.ios.parsers.MobileProvisionParser
import com.apphance.flow.plugins.ios.release.artifact.info.IOSSimArtifactInfo
import groovy.transform.PackageScope
import org.apache.commons.io.IOUtils

import javax.inject.Inject

import static com.google.common.io.Files.createTempDir
import static org.gradle.api.logging.Logging.getLogger

class IOSSimulatorArtifactsBuilder extends AbstractIOSArtifactsBuilder<IOSSimArtifactInfo> {

    private logger = getLogger(getClass())

    @Inject MobileProvisionParser mpParser

    @Lazy
    @PackageScope
    File tmplDir = {
        def resName = 'ios_sim_tmpl.zip'
        def resStream = getClass().getResourceAsStream(resName)
        def tmpDir = createTempDir()
        def tmpDirZip = new File(tmpDir, resName)
        tmpDir.deleteOnExit()
        def os = new FileOutputStream(tmpDirZip)
        IOUtils.copy(resStream, os)
        IOUtils.closeQuietly(os)
        executor.executeCommand(new Command(runDir: tmpDir, cmd: ['unzip', tmpDirZip.canonicalPath, '-d', tmpDir]))
        new File(tmpDir, 'Contents')
    }()

    @Override
    void buildArtifacts(IOSSimArtifactInfo bi) {
        IOSFamily.values().each {
            prepareSimulatorBundleFile(bi, it)
        }
    }

    @PackageScope
    void prepareSimulatorBundleFile(IOSSimArtifactInfo bi, IOSFamily family) {
        def fa = artifactProvider.simulator(bi, family)
        releaseConf.dmgImageFiles.put("${family.iFormat()}-$bi.id" as String, fa)//TODO simplify
        mkdirs(fa)

        def tmpDir = tmpDir(bi, family)
        def embedDir = embedDir(tmpDir)
        def contentsPlist = new File(tmpDir, 'Contents/Info.plist')
        def icon = new File(tmpDir, 'Contents/Resources/Launcher.icns')
        def embedPlist = new File(embedDir, "$bi.appName/Info.plist")

        syncSimAppTemplateToTmpDir(tmplDir, tmpDir)
        syncAppToTmpDir(sourceApp(bi), embedDir)

        resampleIcon(icon)
        updateBundleId(bi.mobileprovision, contentsPlist)
        updateDeviceFamily(family, embedPlist)

        createSimAppDmg(fa.location, tmpDir, "$bi.appName-${family.iFormat()}")

        logger.info("Simulator zip file created: $fa.location")
    }

    @PackageScope
    File tmpDir(IOSSimArtifactInfo bi, IOSFamily family) {
        def tmpDir = createTempDir()
        tmpDir.deleteOnExit()
        def appDir = new File(tmpDir, "$bi.productName (${family.iFormat()}_Simulator) ${conf.fullVersionString}.app")
        appDir.mkdirs()
        logger.info("Temp dir for iOS simulator artifact created: $tmpDir.absolutePath")
        appDir
    }

    private File embedDir(File tmpDir) {
        def embedDir = new File(tmpDir, 'Contents/Resources/EmbeddedApp')
        embedDir.mkdirs()
        embedDir
    }

    @PackageScope
    void syncSimAppTemplateToTmpDir(File tmplDir, File tmpDir) {
        executor.executeCommand(new Command(runDir: conf.rootDir, cmd: [
                'rsync', '-alE', "$tmplDir.canonicalPath", tmpDir
        ]))
    }

    @PackageScope
    File sourceApp(IOSSimArtifactInfo bi) {
        new File("$bi.archiveDir/Products/Applications", bi.appName)
    }

    @PackageScope
    void syncAppToTmpDir(File sourceAppDir, File embedDir) {
        executor.executeCommand(new Command(runDir: conf.rootDir, cmd: [
                'rsync', '-alE', sourceAppDir, embedDir
        ]))
    }

    @PackageScope
    void resampleIcon(File icon) {
        executor.executeCommand(new Command(runDir: conf.rootDir, cmd: [
                '/opt/local/bin/convert',
                new File(conf.rootDir, releaseConf.iconFile.value.path).canonicalPath,
                '-resample',
                '128x128',
                icon.canonicalPath
        ]))
    }

    @PackageScope
    void updateBundleId(File mobileprovision, File plist) {
        def bundleId = mpParser.bundleId(mobileprovision)
        runPlistBuddy("Set :CFBundleIdentifier ${bundleId}.launchsim", plist)
    }

    @PackageScope
    void updateDeviceFamily(IOSFamily family, File plist) {
        runPlistBuddy('Delete UIDeviceFamily', plist, false)
        runPlistBuddy('Add UIDeviceFamily array', plist)
        runPlistBuddy("Add UIDeviceFamily:0 integer $family.UIDDeviceFamily", plist)
    }

    private void runPlistBuddy(String command, File targetPlistFile, boolean failOnError = true) {
        executor.executeCommand(new Command(runDir: conf.rootDir, cmd: [
                '/usr/libexec/PlistBuddy',
                '-c',
                command,
                targetPlistFile.absolutePath
        ], failOnError: failOnError))
    }

    @PackageScope
    void createSimAppDmg(File destDir, File srcDir, String name) {
        executor.executeCommand(new Command(runDir: conf.rootDir, cmd: [
                'hdiutil',
                'create',
                destDir.canonicalPath,
                '-srcfolder',
                srcDir.canonicalPath,
                '-volname',
                name
        ]))
    }
}
