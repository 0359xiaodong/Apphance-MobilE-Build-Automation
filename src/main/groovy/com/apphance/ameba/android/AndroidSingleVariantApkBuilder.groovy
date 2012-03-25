package com.apphance.ameba.android

import java.io.File
import java.util.Collection

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import com.apphance.ameba.ProjectConfiguration
import com.apphance.ameba.ProjectHelper
import com.apphance.ameba.PropertyCategory
import com.apphance.ameba.android.plugins.buildplugin.AndroidBuildListener
import com.apphance.ameba.plugins.release.AmebaArtifact
import com.sun.tools.jdi.JDWP.ClassType.Superclass;


/**
 * Builds APK from the project - one per variant.
 *
 */
class AndroidSingleVariantApkBuilder extends AbstractAndroidSingleVariantBuilder {

    AndroidSingleVariantApkBuilder(Project project, AndroidProjectConfiguration androidProjectConfiguration) {
        super(project, androidProjectConfiguration)
    }

    AndroidBuilderInfo buildApkArtifactBuilderInfo(Project project, String variant, String debugRelease) {
        if (variant != null && debugRelease == null) {
            debugRelease = androidConf.debugRelease[variant]
        }
        String debugReleaseLowercase = debugRelease?.toLowerCase()
        String variablePart = debugReleaseLowercase + "-${variant}"
        File binDir = new File(androidConf.tmpDirs[variant],'bin')
        AndroidBuilderInfo bi = new AndroidBuilderInfo(
                        variant: variant,
                        debugRelease: debugRelease,
                        buildDirectory : binDir,
                        originalFile : new File(binDir, "${conf.projectName}-${debugReleaseLowercase}.apk"),
                        fullReleaseName : "${conf.projectName}-${variablePart}-${conf.fullVersionString}",
                        filePrefix : "${conf.projectName}-${variablePart}-${conf.fullVersionString}")
        return bi
    }

    void buildSingle(AndroidBuilderInfo bi) {
        projectHelper.executeCommand(project, androidConf.tmpDirs[bi.variant], ['ant', 'clean'])
        def variantPropertiesDir = new File(variantsDir, bi.variant)
        if (bi.variant != null && variantPropertiesDir.exists()) {
            project.ant {
                copy(todir : new File(androidConf.tmpDirs[bi.variant],'res/raw'), failonerror:false, overwrite:'true', verbose:'true') {
                    fileset(dir: variantPropertiesDir,
                                    includes:'*', excludes:'market_variant.txt')
                }
            }
        }
        projectHelper.executeCommand(project, , androidConf.tmpDirs[bi.variant], [
            'ant',
            bi.debugRelease.toLowerCase()
        ])
        logger.lifecycle("Apk file created: ${bi.originalFile}")
        buildListeners.each {
            it.buildDone(project, bi)
        }
    }

}
