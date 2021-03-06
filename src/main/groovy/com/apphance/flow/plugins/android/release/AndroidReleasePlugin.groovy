package com.apphance.flow.plugins.android.release

import com.apphance.flow.configuration.android.AndroidReleaseConfiguration
import com.apphance.flow.plugins.android.release.tasks.AvailableArtifactsInfoTask
import com.apphance.flow.plugins.android.release.tasks.UpdateVersionTask
import com.apphance.flow.plugins.project.tasks.CopySourcesTask
import org.gradle.api.Plugin
import org.gradle.api.Project

import javax.inject.Inject

import static org.gradle.api.logging.Logging.getLogger

/**
 * Plugin that provides release functionality for android.<br/><br/>
 *
 * It provides basic release tasks that update version code and version name of the application while preparing the release and
 * produces ready-to-use OTA (Over-The-Air) package (in flow-ota directory) that you can copy to appropriate directory on your web server and have
 * ready-to-use, easily installable OTA version of your application
 */
class AndroidReleasePlugin implements Plugin<Project> {

    def logger = getLogger(this.class)

    @Inject AndroidReleaseConfiguration releaseConf

    @Override
    void apply(Project project) {
        if (releaseConf.isEnabled()) {
            logger.lifecycle("Applying plugin ${this.class.simpleName}")

            project.task(
                    UpdateVersionTask.NAME,
                    type: UpdateVersionTask,
                    dependsOn: CopySourcesTask.NAME)

            project.task(
                    AvailableArtifactsInfoTask.NAME,
                    type: AvailableArtifactsInfoTask)
        }
    }
}
