package com.apphance.ameba.plugins.ios.buildplugin

import com.apphance.ameba.configuration.ios.IOSConfiguration
import com.apphance.ameba.configuration.ios.variants.IOSVariantsConfiguration
import com.apphance.ameba.plugins.ios.buildplugin.tasks.*
import com.apphance.ameba.plugins.project.PrepareSetupTask
import com.apphance.ameba.plugins.project.tasks.VerifySetupTask
import org.gradle.api.Plugin
import org.gradle.api.Project

import javax.inject.Inject

import static com.apphance.ameba.plugins.AmebaCommonBuildTaskGroups.AMEBA_BUILD

/*
 * Plugin for various X-Code related tasks.
 * This is the main iOS build plugin.
 *
 * The plugin provides all the task needed to build iOS application.
 * Besides tasks explained below, the plugin prepares build-*
 * tasks which are dynamically created, based on targets and configurations available.
 * There is one task available per each Target-Configuration combination - unless particular
 * combination is excluded by the exclude property.
 *
 */
class IOSPlugin implements Plugin<Project> {

    static final String BUILD_ALL_TASK_NAME = 'buildAll'
    static final String BUILD_ALL_DEVICE_TASK_NAME = 'buildAllDevice'
    static final String BUILD_ALL_SIMULATOR_TASK_NAME = 'buildAllSimulator'

    @Inject IOSConfiguration conf
    @Inject IOSVariantsConfiguration variantsConf

    @Override
    void apply(Project project) {
        if (conf.isEnabled()) {

            project.task(CleanTask.NAME,
                    type: CleanTask)

            project.task(CopySourcesTask.NAME,
                    type: CopySourcesTask)

            project.task(CopyMobileProvisionTask.NAME,
                    type: CopyMobileProvisionTask)

            project.task(UnlockKeyChainTask.NAME,
                    type: UnlockKeyChainTask)

            project.task(BUILD_ALL_DEVICE_TASK_NAME,
                    group: AMEBA_BUILD,
                    description: 'Builds all device variants')

            project.task(BUILD_ALL_SIMULATOR_TASK_NAME,
                    group: AMEBA_BUILD,
                    description: 'Builds all simulator variants')

            project.task(BUILD_ALL_TASK_NAME,
                    group: AMEBA_BUILD,
                    dependsOn: [BUILD_ALL_DEVICE_TASK_NAME, BUILD_ALL_SIMULATOR_TASK_NAME],
                    description: 'Builds all variants and produces all artifacts (zip, ipa, messages, etc)')

            variantsConf.variants.each { variant ->
                def buildTask = project.task(variant.buildTaskName,
                        type: SingleVariantTask,
                        dependsOn: [CopySourcesTask.NAME, CopyMobileProvisionTask.NAME]
                ) as SingleVariantTask
                buildTask.variant = variant

                def buildAllMode = "buildAll${variant.mode.value.capitalize()}"
                project.tasks[buildAllMode].dependsOn variant.buildTaskName
            }

            project.tasks.each {
                if (!(it.name in [VerifySetupTask.NAME, PrepareSetupTask.NAME])) {
                    it.dependsOn VerifySetupTask.NAME
                }
            }
        }
    }
}
