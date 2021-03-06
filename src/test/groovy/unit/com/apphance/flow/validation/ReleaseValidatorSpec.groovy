package com.apphance.flow.validation

import com.apphance.flow.configuration.properties.ListStringProperty
import org.gradle.api.GradleException
import spock.lang.Shared
import spock.lang.Specification

class ReleaseValidatorSpec extends Specification {

    @Shared validator = new ReleaseValidator()

    def 'mail port is validated correctly when empty'() {
        when:
        validator.validateMailPort(mailPort)

        then:
        def e = thrown(GradleException)
        e.message =~ 'Property mail.port has invalid value!'

        where:
        mailPort << [null, '', '  \t', 'with letter', 'withletter', '123-123']
    }

    def 'mail port is validated correctly when set'() {
        when:
        validator.validateMailPort(mailPort)

        then:
        noExceptionThrown()

        where:
        mailPort << ['121', '1']
    }

    def 'mail server is validated correctly when empty'() {
        when:
        validator.validateMailServer(mailServer)

        then:
        def e = thrown(GradleException)
        e.message =~ 'Property mail.server has invalid value!'

        where:
        mailServer << [null, '  ', '  \t', 'with\tletter', 'with space']
    }

    def 'mail server is validated correctly when set'() {
        when:
        validator.validateMailServer(mailServer)

        then:
        noExceptionThrown()

        where:
        mailServer << ['releaseString', 'release_String', 'relase_String_123_4']
    }

    def 'test validate mail list'() {
        when:
        validator.validateMailList(new ListStringProperty(value: emails))

        then:
        notThrown(Exception)

        where:
        emails << [
                ['Alice Smith <alice.smith@mail.server.com>'],
                ['Alice Smith <alice.smith@mail.server.com>', 'Bob Smith <bob.smith@mail.com>']
        ]
    }

    def 'validate mail list throws exception'() {
        when:
        validator.validateMailList(new ListStringProperty(value: []))

        then:
        thrown(GradleException)
    }
}
