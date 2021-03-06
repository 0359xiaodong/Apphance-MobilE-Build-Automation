package com.apphance.flow.configuration.apphance

import spock.lang.Shared
import spock.lang.Specification

import static com.apphance.flow.configuration.apphance.ApphanceMode.*

class ApphanceArtifactorySpec extends Specification {

    @Shared
    def apphanceArtifactory = new ApphanceArtifactory()

    def 'android lib versions are filled'() {
        when:
        def libs = apphanceArtifactory.androidLibraries(QA)

        then:
        libs.containsAll('1.9', '1.9.1', '1.9.2', '1.9.3')

        when:
        libs = apphanceArtifactory.androidLibraries(SILENT)

        then:
        libs.containsAll('1.9', '1.9.1', '1.9.2', '1.9.3')

        when:
        libs = apphanceArtifactory.androidLibraries(PROD)

        then:
        libs.contains('1.9')
    }

    def 'exception thrown when bad mode passed for android libs'() {
        when:
        apphanceArtifactory.androidLibraries(DISABLED)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid apphance mode: DISABLED'
    }

    def 'iOS lib versions are filled'() {
        when:
        def versions = apphanceArtifactory.iOSLibraries(mode)

        then:
        versions.size() > 0
        versions.every { it.matches("\\d+\\.\\d+\\.\\d+") }

        where:
        mode << [QA, SILENT, PROD]
    }

    def 'exception thrown when bad mode passed for iOS libs'() {
        when:
        apphanceArtifactory.iOSLibraries(DISABLED)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid apphance mode: DISABLED'
    }
}
