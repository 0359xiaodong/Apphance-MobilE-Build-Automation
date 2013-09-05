package com.apphance.flow.plugins.ios.release.tasks

import com.apphance.flow.TestUtils
import com.apphance.flow.configuration.ios.IOSConfiguration
import com.apphance.flow.configuration.ios.variants.IOSVariant
import com.apphance.flow.configuration.ios.variants.IOSVariantsConfiguration
import com.apphance.flow.configuration.properties.IOSBuildModeProperty
import com.apphance.flow.plugins.ios.parsers.PbxJsonParser
import com.apphance.flow.plugins.ios.parsers.PlistParser
import com.apphance.flow.plugins.ios.parsers.XCSchemeParser
import spock.lang.Specification

import static com.apphance.flow.configuration.ios.IOSBuildMode.*
import static com.google.common.io.Files.createTempDir

@Mixin(TestUtils)
class UpdateVersionTaskSpec extends Specification {

    def 'updateVersion task invokes replace version on plist parser'() {
        given:
        def tmpDir = createTempDir()

        and:
        def parser = GroovyMock(PlistParser)

        and:
        def task = create(UpdateVersionTask)

        and:
        task.conf = GroovySpy(IOSConfiguration) {
            getExtVersionCode() >> '3145'
            getExtVersionString() >> '3.1.45'
        }
        and:
        task.variantsConf = GroovySpy(IOSVariantsConfiguration) {
            getVariants() >> [
                    GroovyMock(IOSVariant) {
                        getPlist() >> GroovyMock(File)
                        getTmpDir() >> tmpDir
                        getMode() >> new IOSBuildModeProperty(value: mode)
                    },
                    GroovyMock(IOSVariant) {
                        getPlist() >> GroovyMock(File)
                        getTmpDir() >> tmpDir
                        getMode() >> new IOSBuildModeProperty(value: mode)
                    }
            ]
        }
        and:
        task.parser = parser
        and:
        task.schemeParser = GroovyMock(XCSchemeParser) {
            blueprintIdentifier(_) >> 'blueprintId'
            configuration(_, _) >> 'configuration'
        }
        and:
        task.pbxJsonParser = GroovyMock(PbxJsonParser) {
            getPlistForScheme() >> { a, b, c -> 'some.plist' }.memoize()
        }

        when:
        task.updateVersion()

        then:
        count * parser.replaceVersion(_, '3145', '3.1.45')

        cleanup:
        tmpDir.deleteDir()

        where:
        mode      | count
        FRAMEWORK | 0
        DEVICE    | 1
        SIMULATOR | 1
    }
}
