package com.apphance.flow.configuration.android

import com.apphance.flow.configuration.ProjectConfiguration
import com.apphance.flow.configuration.properties.StringProperty
import com.apphance.flow.executor.AndroidExecutor
import com.apphance.flow.plugins.android.parsers.AndroidBuildXmlHelper
import com.apphance.flow.plugins.android.parsers.AndroidManifestHelper
import com.google.inject.Singleton

import javax.inject.Inject

import static com.apphance.flow.configuration.android.variants.AndroidVariantsConfiguration.VARIANTS_DIR
import static com.apphance.flow.detection.project.ProjectType.ANDROID
import static com.google.common.base.Strings.isNullOrEmpty
import static java.text.MessageFormat.format

@Singleton
class AndroidConfiguration extends ProjectConfiguration {

    String configurationName = 'Android Configuration'

    @Inject AndroidBuildXmlHelper buildXmlHelper
    @Inject AndroidManifestHelper manifestHelper
    @Inject AndroidExecutor androidExecutor

    @Inject
    @Override
    void init() {
        super.init()
    }

    @Override
    boolean isEnabled() {
        projectTypeDetector.detectProjectType(project.rootDir) == ANDROID
    }

    StringProperty projectName = new StringProperty(
            name: 'android.project.name',
            message: "Project name. This property is used with command: 'android update project --name' before every build.",
            defaultValue: { defaultName() },
            possibleValues: { possibleNames() },
            required: { true }
    )

    private String defaultName() {
        buildXmlHelper.projectName(rootDir)
    }

    private List<String> possibleNames() {
        [rootDir.name, defaultName()].findAll { !it?.trim()?.empty }
    }

    @Override
    String getVersionCode() {
        extVersionCode ?: manifestHelper.readVersion(rootDir).versionCode ?: ''
    }

    @Override
    String getVersionString() {
        extVersionString ?: manifestHelper.readVersion(rootDir).versionString ?: ''
    }

    File getResDir() {
        project.file('res')
    }

    def target = new StringProperty(
            name: 'android.target',
            message: "Android target. This property is used with command: 'android update project --target' before every build.",
            defaultValue: { androidProperties.getProperty('target') ?: '' },
            required: { true },
            possibleValues: { possibleTargets() },
            validator: { it in possibleTargets() }
    )

    private List<String> possibleTargets() {
        androidExecutor.targets
    }

    Collection<String> sourceExcludes = super.sourceExcludes + ['*class', 'bin', VARIANTS_DIR]

    @Lazy
    Properties androidProperties = {
        def p = new Properties()
        ['local', 'build', 'default', 'project'].collect { project.file("${it}.properties") }.
                findAll { it.exists() }.each { p.load(new FileInputStream(it)) }
        p
    }()

    @Override
    void validate(List<String> errors) {
        propValidator.with {
            errors << validateCondition(!isNullOrEmpty(reader.envVariable('ANDROID_HOME')),
                    validationBundle.getString('exception.android.android.home'))
            errors << validateCondition(!isNullOrEmpty(projectName.value),
                    format(validationBundle.getString('exception.android.project.name'), projectName.name))
            errors << validateCondition(versionValidator.isNumber(versionCode),
                    validationBundle.getString('exception.android.version.code'))
            errors << validateCondition(versionValidator.hasNoWhiteSpace(versionString),
                    validationBundle.getString('exception.android.version.string'))
            errors << validateCondition(target.validator(target.value),
                    format(validationBundle.getString('exception.android.target'), target.value))
        }
    }
}
