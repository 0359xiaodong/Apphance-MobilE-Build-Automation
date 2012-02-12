package com.apphance.ameba.plugins.projectconfiguration;



import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import com.apphance.ameba.AbstractPrepareSetupTask;
import com.apphance.ameba.AmebaCommonBuildTaskGroups
import com.apphance.ameba.ProjectConfiguration
import com.apphance.ameba.ProjectHelper
import com.apphance.ameba.PropertyCategory;


/**
 * Plugin for Mercurial implementation of VCS system
 *
 */
class ProjectConfigurationPlugin implements Plugin<Project> {

    static Logger logger = Logging.getLogger(ProjectConfigurationPlugin.class)

    ProjectHelper projectHelper
    ProjectConfiguration conf

    void apply(Project project) {
        projectHelper = new ProjectHelper()
        prepareRepositories(project)
        prepareVerifySetupTask(project)
        readProjectConfigurationTask(project)
        preparePrepareSetupTask(project)
        project.task('checkTests', type: CheckTestsTask.class)
        showProjectConfigurationTask(project)
        prepareCleanConfigurationTask(project)
        prepareShowPropertiesTask(project)
        project.task('verifyBaseSetup', type: VerifyBaseSetupTask.class)
        project.task('prepareBaseSetup', type: PrepareBaseSetupTask.class)
        project.task('showBaseSetup', type: ShowBaseSetupTask.class)
    }

    void prepareRepositories(Project project) {
        project.repositories.mavenCentral()
    }

    void prepareVerifySetupTask(Project project) {
        def task = project.task('verifySetup')
        task.description = "Verifies if the project can be build properly"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_SETUP
        task << {
            // this task does nothing. It is there to serve as umbrella task for other setup tasks
        }
    }

    def void preparePrepareSetupTask(Project project) {
        def task = project.task('prepareSetup')
        task.description = "Walk-through wizard for preparing project's configuration"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_SETUP
        project.logging.setLevel(LogLevel.QUIET)
        task.logging.setLevel(LogLevel.QUIET)
        task << {
            use(PropertyCategory) {
                String propertiesToWrite = project.readProperty(AbstractPrepareSetupTask.GENERATED_GRADLE_PROPERTIES,'')
                File f = new File('gradle.properties')
                f << propertiesToWrite
            }
        }
    }

    void prepareShowPropertiesTask(Project project) {
        def task = project.task('showSetup')
        task.description = "Shows all available project properties"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_SETUP
        task << {
            // this task does nothing. It is there to serve as umbrella task for other setup tasks
        }
    }
    def void readProjectConfigurationTask(Project project) {
        def task = project.task('readProjectConfiguration')
        task.description = "Reads project's configuration and sets it up in projectConfiguration property of project"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_CONFIGURATION
        task << {
            use (PropertyCategory) {
                this.conf = project.getProjectConfiguration()
                // NOTE! conf.versionString and conf.versionCode need to
                // be read before project configuration task -> task reading the version
                // should be injected here
                project.retrieveBasicProjectData()
                prepareGeneratedDirectories(project)
            }
        }
    }

    private prepareGeneratedDirectories(Project project) {
        conf.otaDirectory = new File(project.rootDir,"ota/")
        conf.tmpDirectory = new File(project.rootDir,"tmp/")
    }

    def void showProjectConfigurationTask(Project project) {
        def task = project.task('showProjectConfiguration')
        task.group = AmebaCommonBuildTaskGroups.AMEBA_CONFIGURATION
        task.description = "Shows project's configuration"
        task << {
            logger.lifecycle( "Configuration: " + project['project.configuration'])
        }
        task.dependsOn(project.readProjectConfiguration)
    }

    def void prepareCleanConfigurationTask(Project project) {
        def task = project.task('cleanConfiguration')
        task.description = "Cleans configuration before each build"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_CONFIGURATION
        task << {
            conf.buildDirectory.deleteDir()
            conf.tmpDirectory.deleteDir()
            conf.logDirectory.deleteDir()
            conf.buildDirectory.mkdirs()
            conf.logDirectory.mkdirs()
            conf.tmpDirectory.mkdirs()
        }
        task.dependsOn(project.readProjectConfiguration)
    }
}