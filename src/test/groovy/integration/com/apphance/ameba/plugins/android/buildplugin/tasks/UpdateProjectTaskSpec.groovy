package com.apphance.ameba.plugins.android.buildplugin.tasks

import com.apphance.ameba.configuration.android.AndroidConfiguration
import com.apphance.ameba.configuration.properties.StringProperty
import com.apphance.ameba.executor.AndroidExecutor
import com.apphance.ameba.executor.command.CommandExecutor
import com.apphance.ameba.executor.command.CommandLogFilesGenerator
import com.apphance.ameba.executor.linker.FileLinker
import spock.lang.Specification

import static com.apphance.ameba.executor.command.CommandLogFilesGenerator.LogFile.ERR
import static com.apphance.ameba.executor.command.CommandLogFilesGenerator.LogFile.STD
import static java.io.File.createTempFile
import static org.gradle.testfixtures.ProjectBuilder.builder

class UpdateProjectTaskSpec extends Specification {

    def 'all projects are updated'() {
        given:
        def testProjectDir = new File('testProjects/android/android-basic')

        and:
        def props = [
                new File(testProjectDir, 'local.properties'),
                new File(testProjectDir, 'subproject/local.properties'),
                new File(testProjectDir, 'subproject/subsubproject/local.properties')
        ]

        and:
        def project = builder().withProjectDir(testProjectDir).build()

        and:
        def fileLinker = Mock(FileLinker) {
            fileLink(_) >> ''
        }

        and:
        def errLog = createTempFile('tmp', 'file-err')
        def outLog = createTempFile('tmp', 'file-out')
        def logFileGenerator = Mock(CommandLogFilesGenerator) {
            commandLogFiles() >> [(STD): outLog, (ERR): errLog]
        }

        and:
        def ce = new CommandExecutor(fileLinker, logFileGenerator)

        and:
        def ae = new AndroidExecutor(ce)

        and:
        def ac = GroovyMock(AndroidConfiguration)
        ac.target >> new StringProperty(value: 'android-7')
        ac.projectName >> new StringProperty(value: 'TestAndroidProject')
        ac.rootDir >> project.rootDir

        and:
        def updateTask = project.task(UpdateProjectTask.NAME, type: UpdateProjectTask) as UpdateProjectTask
        updateTask.androidExecutor = ae
        updateTask.conf = ac

        when:
        props.each { it.delete() }
        updateTask.runUpdate()

        then:
        props.every { it.exists() }

        cleanup:
        outLog.delete()
        errLog.delete()
    }
}