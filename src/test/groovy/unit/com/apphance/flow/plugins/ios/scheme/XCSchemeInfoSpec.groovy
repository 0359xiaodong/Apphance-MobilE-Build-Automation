package com.apphance.flow.plugins.ios.scheme

import com.apphance.flow.configuration.ios.IOSConfiguration
import com.apphance.flow.plugins.ios.parsers.XCSchemeParser
import com.apphance.flow.util.FlowUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
import spock.lang.Shared
import spock.lang.Specification

@Mixin(FlowUtils)
class XCSchemeInfoSpec extends Specification {

    @Shared
    def schemes = ['GradleXCode',
            'GradleXCode With Space',
            'GradleXCodeNoLaunchAction',
            'GradleXCodeWithApphance',
            'GradleXCodeWith2Targets',
            'GradleXCode 2']
    @Shared
    def tmpDir = temporaryDir

    @Shared
    def conf = GroovyMock(IOSConfiguration) {
        getRootDir() >> tmpDir
        getSchemes() >> schemes
    }
    def schemeInfo = new XCSchemeInfo(conf: conf, schemeParser: new XCSchemeParser())

    def setupSpec() {
        FileUtils.copyDirectory(new File(getClass().getResource('iosProject').toURI()), tmpDir)
    }

    def 'schemes are detected correctly'() {
        expect:
        schemeInfo.hasSchemes
        schemeInfo.schemesDeclared()
        schemeInfo.schemesShared()
        schemeInfo.schemesHasEnabledTestTargets()
    }

    def 'schemes are detected correctly when not declared'() {
        given:
        schemeInfo.conf = GroovyMock(IOSConfiguration) {
            getSchemes() >> []
        }

        expect:
        !schemeInfo.hasSchemes
        !schemeInfo.schemesDeclared()
        !schemeInfo.schemesShared()
        !schemeInfo.schemesHasEnabledTestTargets()
    }

    def 'buildable scheme found'() {
        expect:
        schemeInfo.schemeBuildable(schemeInfo.schemeFile.call(scheme)) == buildable

        where:
        scheme << schemes
        buildable << [true, true, false, true, true, false]
    }

    def 'shared scheme found'() {
        expect:
        schemeInfo.schemeShared(schemeInfo.schemeFile.call(scheme)) == shared

        where:
        scheme << schemes
        shared << [true, true, true, true, true, false]
    }

    def 'scheme #scheme has enabled test targets'() {
        expect:
        schemeInfo.schemeHasEnabledTestTargets(schemeInfo.schemeFile.call(scheme)) == enabled

        where:
        scheme << schemes
        enabled << [true, false, false, true, false, false]
    }

    def 'scheme file is found'() {
        given:
        schemeInfo.conf = GroovyMock(IOSConfiguration) {
            getRootDir() >> new File('demo/ios/GradleXCode')
        }

        expect:
        schemeInfo.schemeFile.call(scheme).path.endsWith(expectedPath)

        where:
        scheme        || expectedPath
        'GradleXCode' || 'demo/ios/GradleXCode/GradleXCode.xcodeproj/xcshareddata/xcschemes/GradleXCode.xcscheme'
        'FakeScheme'  || 'demo/ios/GradleXCode/FakeScheme.xcscheme'
    }

    def 'exception thrown when 1+ schemes found for given name'() {
        given:
        def tmpDir2 = temporaryDir
        def sDir1 = new File(tmpDir2, 'xcshareddata/xcschemes')
        def sDir2 = new File(tmpDir2, 'other/xcshareddata/xcschemes')
        sDir1.mkdirs()
        sDir2.mkdirs()
        def s1 = new File(sDir1, 'GradleXCode.xcscheme')
        def s2 = new File(sDir2, 'GradleXCode.xcscheme')
        s1.text = '<Scheme></Scheme>'
        s2.text = '<Scheme></Scheme>'

        and:
        schemeInfo.conf = GroovyMock(IOSConfiguration) {
            getRootDir() >> tmpDir2
        }

        when:
        schemeInfo.schemeFile.call('GradleXCode')

        then:
        def e = thrown(GradleException)
        e.message.startsWith('Found more than one scheme file for name: GradleXCode')
    }
}
