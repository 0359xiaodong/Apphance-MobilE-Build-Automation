package com.apphance.flow.plugins.release.tasks

import com.apphance.flow.configuration.ProjectConfiguration
import com.apphance.flow.validation.VersionValidator
import groovy.transform.PackageScope
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

import static com.apphance.flow.plugins.FlowTasksGroups.FLOW_RELEASE

abstract class AbstractUpdateVersionTask extends DefaultTask {

    static final String NAME = 'updateVersion'
    String group = FLOW_RELEASE
    String description = "Updates version stored in configuration file of the project. Numeric version is set from 'version.code' system property or 'VERSION_CODE' environment variable property. String version is set from 'version.string' system property or 'VERSION_CODE' environment variable"

    @Inject ProjectConfiguration conf
    @Inject VersionValidator versionValidator

    @TaskAction
    void updateVersion() {
        def versionString = conf.versionString
        def versionCode = conf.versionCode

        versionValidator.validateVersionString(versionString)
        versionValidator.validateVersionCode(versionCode)

        updateDescriptor(versionCode, versionString)

        logger.lifecycle("New version string: $versionString")
        logger.lifecycle("New version code: $versionCode")
    }

    @PackageScope
    abstract void updateDescriptor(String versionCode, String versionString)
}
