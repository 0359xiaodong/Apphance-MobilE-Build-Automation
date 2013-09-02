package com.apphance.flow.plugins.ios.buildplugin.tasks

import com.apphance.flow.configuration.ios.IOSConfiguration
import com.apphance.flow.configuration.ios.IOSReleaseConfiguration
import com.apphance.flow.configuration.ios.variants.IOSVariant
import com.apphance.flow.configuration.properties.IOSBuildModeProperty
import com.apphance.flow.configuration.properties.StringProperty
import com.apphance.flow.executor.IOSExecutor
import com.apphance.flow.plugins.ios.parsers.XCSchemeParser
import com.apphance.flow.plugins.ios.release.artifact.builder.IOSDeviceArtifactsBuilder
import com.apphance.flow.plugins.ios.release.artifact.builder.IOSSimulatorArtifactsBuilder
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files

import static com.apphance.flow.configuration.ios.IOSBuildMode.*
import static org.gradle.testfixtures.ProjectBuilder.builder

class ArchiveVariantTaskSpec extends Specification {

    def project = builder().build()
    def task = project.task('archiveTask', type: ArchiveVariantTask) as ArchiveVariantTask

    def setup() {
        task.iosExecutor = GroovyMock(IOSExecutor)
    }

    def 'exception when null variant passed'() {
        given:
        task.variant = null

        when:
        task.build()

        then:
        def e = thrown(NullPointerException)
        e.message == 'Null variant passed to builder!'
    }

    def 'exception when variant with bad mode passed'() {
        given:
        task.variant = GroovyMock(IOSVariant) {
            getMode() >> new IOSBuildModeProperty(value: FRAMEWORK)
        }

        when:
        task.build()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid build mode: $FRAMEWORK!"
    }

    def 'executor runs archive command when variant passed & release conf disabled'() {
        given:
        def tmpFile = Files.createTempFile('a', 'b').toFile()

        and:
        def variant = GroovySpy(IOSVariant) {
            getTmpDir() >> GroovyMock(File)
            getConf() >> GroovyMock(IOSConfiguration) {
                xcodebuildExecutionPath() >> ['xcodebuild']
            }
            getName() >> 'GradleXCode'
            getSchemeFile() >> tmpFile
            getMode() >> new IOSBuildModeProperty(value: DEVICE)
        }
        task.releaseConf = GroovyMock(IOSReleaseConfiguration) {
            isEnabled() >> false
        }
        task.conf = GroovyMock(IOSConfiguration) {
            xcodebuildExecutionPath() >> ['xcodebuild']
            getSdk() >> new StringProperty(value: 'iphoneos')
        }
        task.variant = variant
        task.schemeParser = GroovyMock(XCSchemeParser)

        when:
        task.build()

        then:
        noExceptionThrown()
        1 * task.iosExecutor.buildVariant(_, ['xcodebuild', '-scheme', 'GradleXCode', '-sdk', 'iphoneos', 'clean', 'archive']) >> ["FLOW_ARCHIVE_PATH=$tmpFile.absolutePath"].iterator()

        cleanup:
        tmpFile.delete()
    }

    def 'null returned when no archive found'() {
        expect:
        task.findArchiveFile([].iterator()) == null
    }

    def 'exception thrown when no archive found'() {
        given:
        def archive = new File('not-existing')

        when:
        task.validateArchiveFile(archive)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.endsWith("Xcarchive file: $archive.absolutePath does not exist or is not a directory")
    }

    @Unroll
    def 'correct instance of builder is returned for mode #mode'() {
        given:
        task.simulatorArtifactsBuilder = GroovyMock(IOSSimulatorArtifactsBuilder)
        task.deviceArtifactsBuilder = GroovyMock(IOSDeviceArtifactsBuilder)

        and:
        task.variant = GroovyMock(IOSVariant) {
            getMode() >> new IOSBuildModeProperty(value: mode)
        }

        expect:
        closure.call(task.builder.call())

        where:
        mode      | closure
        DEVICE    | { it.class.name.contains(IOSDeviceArtifactsBuilder.class.name) }
        SIMULATOR | { it.class.name.contains(IOSSimulatorArtifactsBuilder.class.name) }
        FRAMEWORK | { it == null }
    }
}
