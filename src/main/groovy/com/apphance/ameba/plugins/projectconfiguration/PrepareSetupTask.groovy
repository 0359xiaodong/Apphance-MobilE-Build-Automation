
package com.apphance.ameba.plugins.projectconfiguration

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutput.Style;
import org.gradle.logging.StyledTextOutputFactory

import com.apphance.ameba.AbstractPrepareSetupOperation
import com.apphance.ameba.AmebaCommonBuildTaskGroups
import com.apphance.ameba.PropertyCategory

class PrepareSetupTask extends DefaultTask {
    PrepareSetupTask() {
        this.group = AmebaCommonBuildTaskGroups.AMEBA_SETUP
        this.description = "Walk-through wizard for preparing project's configuration"
        this.dependsOn(project.readProjectConfiguration)
    }

    List<AbstractPrepareSetupOperation> prepareSetupOperations = []

    @TaskAction
    void prepareSetup() {
        project[AbstractPrepareSetupOperation.GENERATED_GRADLE_PROPERTIES] = \
"""###########################################################
# Generated by Ameba system by running
#    gradle prepareSetup
# You can modify the file manually.
# Or you can re-run the prepareSetup command
# for guided re-configuration
"""
        prepareSetupOperations.each { it.project = project }
        prepareSetupOperations.each { it.prepareSetup() }
        use(PropertyCategory) {
            String propertiesToWrite = project.readProperty(AbstractPrepareSetupOperation.GENERATED_GRADLE_PROPERTIES,'')
            StyledTextOutput o = services.get(StyledTextOutputFactory).create(this.class)
            o.withStyle(Style.Normal).println("About to write new properties to gradle.properties:")
            propertiesToWrite.split('\n').each {
                if (it.startsWith('#')) {
                    o.withStyle(Style.Info).println(it)
                } else {
                    o.withStyle(Style.Identifier).println(it)
                }
            }
            o.withStyle(Style.Normal).println("Are you sure y/n?")
            BufferedReader br = AbstractPrepareSetupOperation.getReader()
            File f = new File(project.rootDir,'gradle.properties')
            String answer = ''
            while (!(answer in ['y', 'n'])) {
                answer = br.readLine()
            }
            if (answer == 'y') {
                f.delete()
                f << propertiesToWrite
                System.out.println("File written: ${f}")
            } else {
                System.out.println("Skipped writing to file: ${f}")
            }
        }
    }
}