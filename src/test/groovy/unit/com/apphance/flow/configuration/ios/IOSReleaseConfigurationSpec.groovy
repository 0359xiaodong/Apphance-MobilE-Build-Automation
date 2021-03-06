package com.apphance.flow.configuration.ios

import com.apphance.flow.configuration.ios.variants.AbstractIOSVariant
import com.apphance.flow.configuration.ios.variants.IOSVariantsConfiguration
import com.apphance.flow.configuration.reader.PropertyPersister
import com.apphance.flow.executor.IOSExecutor
import com.apphance.flow.plugins.ios.parsers.PlistParser
import com.apphance.flow.plugins.ios.parsers.PlistParserSpec
import com.google.common.io.Files
import org.gradle.api.Project
import spock.lang.Specification

import static com.apphance.flow.configuration.ios.IOSReleaseConfiguration.getICON_PATTERN

class IOSReleaseConfigurationSpec extends Specification {

    def iosReleaseConf = new IOSReleaseConfiguration()

    def setup() {
        def iosConf = GroovySpy(IOSConfiguration)
        iosConf.project = GroovyStub(Project) {
            getRootDir() >> new File('demo/ios/GradleXCode/')
        }

        def variantsConf = GroovyStub(IOSVariantsConfiguration)
        variantsConf.mainVariant >> GroovyStub(AbstractIOSVariant) {
            getPlist() >> new File(PlistParserSpec.class.getResource('Test.plist.json').toURI())
        }

        def parser = new PlistParser()

        parser.executor = GroovyMock(IOSExecutor) {
            plistToJSON(_) >> new File(PlistParserSpec.class.getResource('Test.plist.json').toURI()).text.split('\n')
        }

        iosReleaseConf.conf = iosConf
        iosReleaseConf.iosVariantsConf = variantsConf
        iosReleaseConf.plistParser = parser
    }

    def 'test defaultIcon'() {
        expect:
        iosReleaseConf.possibleIcon().path == 'icon.png'
    }

    def 'test possibleIcons'() {
        expect:
        iosReleaseConf.possibleIcons().containsAll(['icon.png', 'icon_retina.png'])
    }

    def 'mobile provision file are found'() {
        when:
        def files = iosReleaseConf.mobileprovisionFiles

        then:
        files.size() == 1
        'GradleXCode.mobileprovision' in files*.name

    }

    def 'test canBeEnabled'() {
        expect:
        iosReleaseConf.canBeEnabled()
    }

    def 'test matching icon pattern'() {
        expect:
        ok ==~ ICON_PATTERN

        where:
        ok << ['Icon.png', 'icon.png', 'Icon@2x.png', 'Icon-72.png', 'icon-small.png', 'abcIcOnaaa.png']
    }

    def 'test not matching icon pattern'() {
        expect:
        !(notMatching ==~ ICON_PATTERN)

        where:
        notMatching << ['con.png', 'icoan.png', 'icon.jpg', 'icon', 'ico.png']
    }

    def 'non existing icon handled'() {
        given:
        def rootDir = Files.createTempDir()

        and:
        def releaseConf = new IOSReleaseConfiguration(conf: GroovyStub(IOSConfiguration) {
            getRootDir() >> rootDir
        })

        when:
        releaseConf.possibleIcon()
        def value = releaseConf.releaseIcon.defaultValue()

        then:
        noExceptionThrown()
        value == null

        cleanup:
        rootDir.deleteDir()
    }

    def 'default icon exists'() {
        given:
        def configuration = new IOSReleaseConfiguration()

        when:
        def icon = configuration.defaultIcon

        then:
        icon.exists()
        icon.size() > 100
        icon.name == 'ios-icon.svg'
    }

    def 'default icon exists where empty conf'() {
        given:
        def releaseConf = new IOSReleaseConfiguration()
        releaseConf.propPersister = GroovyStub(PropertyPersister) {
            get('release.icon') >> null
            get('release.url') >> null
        }
        releaseConf.conf = GroovyStub(IOSConfiguration) {
            getRootDir() >> new File('.')
        }

        and:
        releaseConf.init()

        when:
        def icon = releaseConf.releaseIcon.value

        then:
        icon.exists()
        icon.size() > 100
        icon.name == 'ios-icon.svg'
    }

    def 'default icon exists when set'() {
        given:
        def conf = new IOSReleaseConfiguration()
        conf.propPersister = GroovyStub(PropertyPersister) {
            get('release.icon') >> 'src/test/resources/com/apphance/flow/plugins/release/tasks/Blank.jpg'
            get('release.url') >> null
        }

        when:
        conf.init()

        then:
        def icon = conf.releaseIcon.value
        icon.exists()
        icon.size() > 100
        icon.name == 'Blank.jpg'
    }
}
