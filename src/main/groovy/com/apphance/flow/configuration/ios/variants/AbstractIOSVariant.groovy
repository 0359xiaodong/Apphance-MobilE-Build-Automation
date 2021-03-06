package com.apphance.flow.configuration.ios.variants

import com.apphance.flow.configuration.ios.IOSConfiguration
import com.apphance.flow.configuration.ios.IOSReleaseConfiguration
import com.apphance.flow.configuration.ios.XCAction
import com.apphance.flow.configuration.properties.FileProperty
import com.apphance.flow.configuration.properties.IOSBuildModeProperty
import com.apphance.flow.configuration.properties.ListStringProperty
import com.apphance.flow.configuration.properties.StringProperty
import com.apphance.flow.configuration.variants.AbstractVariant
import com.apphance.flow.detection.project.ProjectType
import com.apphance.flow.executor.IOSExecutor
import com.apphance.flow.plugins.ios.parsers.PbxJsonParser
import com.apphance.flow.plugins.ios.parsers.PlistParser
import com.apphance.flow.plugins.ios.parsers.XCSchemeParser
import com.apphance.flow.plugins.ios.scheme.XCSchemeInfo
import com.apphance.flow.plugins.ios.xcodeproj.XCProjLocator
import com.apphance.flow.validation.VersionValidator
import com.google.inject.assistedinject.Assisted
import groovy.transform.PackageScope

import javax.inject.Inject

import static com.apphance.flow.configuration.ios.IOSBuildMode.*
import static com.apphance.flow.configuration.ios.XCAction.*
import static com.apphance.flow.plugins.ios.xcodeproj.XCProjLocator.PROJECT_PBXPROJ
import static com.apphance.flow.util.file.FileManager.relativeTo
import static com.google.common.base.Preconditions.checkArgument
import static java.text.MessageFormat.format
import static org.apache.commons.lang.StringUtils.isNotBlank
import static org.apache.commons.lang.StringUtils.isNotEmpty

abstract class AbstractIOSVariant extends AbstractVariant {

    @Inject IOSReleaseConfiguration releaseConf
    @Inject PlistParser plistParser
    @Inject PbxJsonParser pbxJsonParser
    @Inject IOSExecutor executor
    @Inject XCSchemeParser schemeParser
    @Inject XCSchemeInfo schemeInfo
    @Inject XCProjLocator xcodeprojLocator
    @Inject VersionValidator versionValidator

    @Inject
    AbstractIOSVariant(@Assisted String name) {
        super(name)
    }

    @Override
    @Inject
    void init() {

        mode.name = "ios.variant.${name}.mode"
        mobileprovision.name = "ios.variant.${name}.mobileprovision"

        frameworkName.name = "ios.variant.${name}.framework.name"
        frameworkHeaders.name = "ios.variant.${name}.framework.headers"
        frameworkResources.name = "ios.variant.${name}.framework.resources"
        frameworkLibs.name = "ios.variant.${name}.framework.libs"

        aphMode.doc = { docBundle.getString('ios.variant.apphance.mode') }

        super.init()
    }

    final ProjectType projectType = ProjectType.IOS

    @Override
    String getConfigurationName() {
        "iOS Variant $name"
    }

    @PackageScope
    IOSConfiguration getConf() {
        super.@conf as IOSConfiguration
    }

    def mode = new IOSBuildModeProperty(
            message: "Build mode for the variant",
            doc: { docBundle.getString('ios.variant.mode') },
            required: { true },
            possibleValues: { possibleBuildModes },
            validator: { it in possibleBuildModes }
    )

    @Lazy
    @PackageScope
    List<String> possibleBuildModes = {
        if (schemeInfo.schemeBuildable(schemeFile) && schemeInfo.schemeHasSingleBuildableTarget(schemeFile))
            return [DEVICE, SIMULATOR]*.name()
        else if (!schemeInfo.schemeBuildable(schemeFile))
            return [FRAMEWORK]*.name()
        []
    }()

    protected FileProperty mobileprovision = new FileProperty(
            message: "Mobile provision file for variant defined",
            doc: { docBundle.getString('ios.variant.mobileprovision') },
            interactive: { mobileprovisionEnabled },
            required: { mobileprovisionEnabled },
            possibleValues: { possibleMobileProvisionPaths },
            validator: {
                if (!it) return false
                def file = new File(conf.rootDir, it as String)
                relativeTo(conf.rootDir.absolutePath, file.absolutePath).path in (possibleMobileProvisionPaths) ?: false
            }
    )

    @Lazy
    @PackageScope
    boolean mobileprovisionEnabled = {
        def enabled = mode.value == DEVICE
        if (!enabled)
            this.@mobileprovision.resetValue()
        enabled
    }()

    FileProperty getMobileprovision() {
        new FileProperty(name: this.@mobileprovision.name,
                value: new File((tmpDir.exists() ? tmpDir : conf.rootDir), this.@mobileprovision.value.path))
    }

    @Lazy
    @PackageScope
    List<String> possibleMobileProvisionPaths = {
        def mp = releaseConf.mobileprovisionFiles
        mp ? mp.collect { relativeTo(conf.rootDir.absolutePath, it.absolutePath) }*.path : []
    }()

    def frameworkName = new StringProperty(
            message: 'Framework name',
            doc: { docBundle.getString('ios.variant.framework.name') },
            required: { mode.value == FRAMEWORK },
            interactive: { mode.value == FRAMEWORK },
            validator: { isNotEmpty(it) }
    )
    def frameworkHeaders = new ListStringProperty(
            doc: { docBundle.getString('ios.variant.framework.headers') },
            interactive: { false }, required: { false }
    )

    def frameworkResources = new ListStringProperty(
            doc: { docBundle.getString('ios.variant.framework.resources') },
            interactive: { false }, required: { false }
    )

    def frameworkLibs = new ListStringProperty(
            doc: { docBundle.getString('ios.variant.framework.libs') },
            interactive: { false }, required: { false }
    )

    String getBundleId() {
        plistParser.evaluate(plistParser.bundleId(plist), target, archiveConfiguration) ?: ''
    }

    @Lazy
    List<String> possibleApphanceLibVersions = {
        apphanceArtifactory.iOSLibraries(aphMode.value)
    }()

    @Lazy
    boolean apphanceEnabledForVariant = {
        mode.value == DEVICE
    }()

    String getVersionCode() {
        conf.extVersionCode ?: plistParser.evaluate(plistParser.bundleVersion(plist), target, archiveConfiguration) ?: ''
    }

    String getVersionString() {
        conf.extVersionString ?: plistParser.evaluate(plistParser.bundleShortVersionString(plist), target, archiveConfiguration) ?: ''
    }

    String getFullVersionString() {
        "${versionString}_${versionCode}"
    }

    String getProjectName() {
        if (mode.value in [SIMULATOR, DEVICE]) {
            String bundleDisplayName = plistParser.bundleDisplayName(plist)
            checkArgument(isNotBlank(bundleDisplayName), format(validationBundle.getString('exception.ios.variant.bundleDisplayName'), plist.absolutePath))
            return plistParser.evaluate(bundleDisplayName, target, archiveConfiguration)
        } else if (mode.value == FRAMEWORK) {
            return frameworkName.value
        }
        name
    }

    File getPbxFile() {
        def pbx = xcodeprojLocator.findXCodeproj(schemeParser.xcodeprojName(schemeFile, action), schemeParser.blueprintIdentifier(schemeFile, action))
        def relative = relativeTo(conf.rootDir, pbx)
        def tmpPbx = new File(tmpDir, relative)
        tmpPbx?.exists() ? new File(tmpPbx, PROJECT_PBXPROJ) : new File(conf.rootDir, "$relative/$PROJECT_PBXPROJ")
    }

    XCAction getAction() {
        mode.value == FRAMEWORK ? BUILD_ACTION : LAUNCH_ACTION
    }

    File getPlist() {
        String confName = schemeParser.configuration(schemeFile, LAUNCH_ACTION)
        String blueprintId = schemeParser.blueprintIdentifier(schemeFile)
        def plist = pbxJsonParser.plistForScheme.call(pbxFile, confName, blueprintId)
        tmpDir.exists() ? new File(tmpDir, plist) : new File(conf.rootDir, plist)
    }

    String getTarget() {
        pbxJsonParser.targetForBlueprintId.call(pbxFile, schemeParser.blueprintIdentifier(schemeFile))
    }

    String getArchiveConfiguration() {
        schemeParser.configuration(schemeFile, ARCHIVE_ACTION)
    }

    String getArchiveTaskName() {
        "archive$name".replaceAll('\\s', '')
    }

    String getFrameworkTaskName() {
        "framework$name".replaceAll('\\s', '')
    }

    File getSchemeFile() {
        def s = schemeInfo.schemeFile.call(schemeName)
        def relative = relativeTo(conf.rootDir, s)
        def tmpScheme = new File(tmpDir, relative)
        tmpScheme?.exists() ? tmpScheme : new File(conf.rootDir, relative)
    }

    abstract String getSchemeName()

    abstract List<String> getXcodebuildExecutionPath()

    @Override
    void validate(List<String> errors) {
        super.validate(errors)
        propValidator.with {
            errors << validateCondition(versionValidator.isNumber(versionCode),
                    validationBundle.getString('exception.ios.version.code'))
            errors << validateCondition(versionValidator.hasNoWhiteSpace(versionString),
                    validationBundle.getString('exception.ios.version.string'))

            if (mobileprovisionEnabled)
                errors.addAll(validateProperties(mobileprovision))

            if (mode.value == FRAMEWORK) {
                errors.addAll(validateProperties(frameworkName))
                errors << validateCondition(frameworkHeaders.value.every { new File(conf.rootDir, it).exists() },
                        validationBundle.getString('exception.ios.framework.invalid.headers'))
                errors << validateCondition(frameworkResources.value.every { new File(conf.rootDir, it).exists() },
                        validationBundle.getString('exception.ios.framework.invalid.resources'))
                errors << validateCondition(frameworkLibs.value.every { new File(conf.rootDir, it).exists() },
                        validationBundle.getString('exception.ios.framework.invalid.libs'))
            }
        }
    }
}