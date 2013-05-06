package com.apphance.ameba.plugins.ios.buildplugin

import com.apphance.ameba.PropertyCategory
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Shared
import spock.lang.Specification

import static com.apphance.ameba.plugins.ios.buildplugin.IOSProjectProperty.*

class IOSPropertySpec extends Specification {

    @Shared
    def project = ProjectBuilder.builder().build()

    def setup() {
        project.properties.clear()
    }

    def 'lists ios properties without comments'() {
        when:
        project[PROJECT_DIRECTORY.propertyName] = "Project.xcodeproj"
        project[PLIST_FILE.propertyName] = "test"
        project[EXCLUDED_BUILDS.propertyName] = ".*"
        project[IOS_FAMILIES.propertyName] = "iPhone,iPad"
        project[DISTRIBUTION_DIR.propertyName] = "release/distribution_resources"
        project[MAIN_TARGET.propertyName] = "MainTarget"
        project[MAIN_CONFIGURATION.propertyName] = "MainConfiguration"
        project[IOS_SDK.propertyName] = "iphoneos"
        project[IOS_SIMULATOR_SDK.propertyName] = "iphonesimulator"

        and:
        def properties = PropertyCategory.listPropertiesAsString(project, IOSProjectProperty, false)

        then:
        '''###########################################################
# iOS properties
###########################################################
ios.project.directory=Project.xcodeproj
ios.plist.file=test
ios.excluded.builds=.*
ios.families=iPhone,iPad
ios.distribution.resources.dir=release/distribution_resources
ios.mainTarget=MainTarget
ios.mainConfiguration=MainConfiguration
ios.sdk=iphoneos
ios.simulator.sdk=iphonesimulator
''' == properties
    }

    def 'lists ios properties with comments'() {
        when:
        project[PROJECT_DIRECTORY.propertyName] = "Project.xcodeproj"
        project[PLIST_FILE.propertyName] = "test"
        project[EXCLUDED_BUILDS.propertyName] = ".*"
        project[IOS_FAMILIES.propertyName] = "iPhone,iPad"
        project[DISTRIBUTION_DIR.propertyName] = "release/distribution_resources"
        project[MAIN_TARGET.propertyName] = "MainTarget"
        project[MAIN_CONFIGURATION.propertyName] = "MainConfiguration"
        project[IOS_SDK.propertyName] = "iphoneos"
        project[IOS_SIMULATOR_SDK.propertyName] = "iphonesimulator"

        and:
        def properties = PropertyCategory.listPropertiesAsString(project, IOSProjectProperty, true)

        then:
        '''###########################################################
# iOS properties
###########################################################
# Path to appropriate .xcodeproj directory
ios.project.directory=Project.xcodeproj
# Path to plist file of the project
ios.plist.file=test
# List of excluded builds. These are coma-separated regular expressions (matched against target-configuration) [optional] default: <>
ios.excluded.builds=.*
# List of iOS families used (iPhone/iPad) [optional] default: <iPhone,iPad>
ios.families=iPhone,iPad
# Path to distribution resources directory. In this directory mobile provision file should be placed.
ios.distribution.resources.dir=release/distribution_resources
# Main target for releasable build
ios.mainTarget=MainTarget
# Main configuration for releasable build
ios.mainConfiguration=MainConfiguration
# SDK used to build iOS targets (-sdk option of xcodebuild) [optional] default: <iphoneos>
ios.sdk=iphoneos
# SDK used to build simulator targets (-sdk option of xcodebuild) [optional] default: <iphonesimulator>
ios.simulator.sdk=iphonesimulator
''' == properties
    }
}
