package com.apphance.ameba.plugins.release

import com.apphance.ameba.PropertyCategory
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Shared
import spock.lang.Specification

import static com.apphance.ameba.plugins.release.ProjectReleaseProperty.*

class ProjectReleasePropertySpec extends Specification {

    @Shared
    def project

    def setupSpec() {
        def pb = ProjectBuilder.builder()
        project = pb.build()

        project[RELEASE_PROJECT_ICON_FILE.propertyName] = "Icon.png"
        project[RELEASE_PROJECT_URL.propertyName] = "http://example.com/subproject"
        project[RELEASE_PROJECT_LANGUAGE.propertyName] = "pl"
        project[RELEASE_PROJECT_COUNTRY.propertyName] = "PL"
        project[RELEASE_MAIL_FROM.propertyName] = "test@apphance.com"
        project[RELEASE_MAIL_TO.propertyName] = "no-reply@apphance.com"
        project[RELEASE_MAIL_FLAGS.propertyName] = "qrCode,imageMontage"
    }

    def 'list properties with and withou comments'() {

        expect:
        output == PropertyCategory.listPropertiesAsString(project, ProjectReleaseProperty, comments)

        where:
        output | comments
        '''###########################################################
# Release properties
###########################################################
release.project.icon.file=Icon.png
release.project.url=http://example.com/subproject
release.project.language=pl
release.project.country=PL
release.mail.from=test@apphance.com
release.mail.to=no-reply@apphance.com
release.mail.flags=qrCode,imageMontage
''' | false
        '''###########################################################
# Release properties
###########################################################
# Path to project's icon file
release.project.icon.file=Icon.png
# Base project URL where the artifacts will be placed. This should be folder URL where last element (after last /) is used as subdirectory of ota dir when artifacts are created locally.
release.project.url=http://example.com/subproject
# Language of the project [optional] default: <en>
release.project.language=pl
# Project country [optional] default: <US>
release.project.country=PL
# Sender email address
release.mail.from=test@apphance.com
# Recipient of release email
release.mail.to=no-reply@apphance.com
# Flags for release email [optional] default: <qrCode,imageMontage>
release.mail.flags=qrCode,imageMontage
''' | true

    }
}