package com.apphance.flow.di

import com.apphance.flow.configuration.AbstractConfiguration
import com.apphance.flow.configuration.ProjectConfiguration
import com.apphance.flow.configuration.android.AndroidAnalysisConfiguration
import com.apphance.flow.configuration.android.AndroidConfiguration
import com.apphance.flow.configuration.android.AndroidReleaseConfiguration
import com.apphance.flow.configuration.android.AndroidTestConfiguration
import com.apphance.flow.configuration.android.variants.AndroidVariantFactory
import com.apphance.flow.configuration.android.variants.AndroidVariantsConfiguration
import com.apphance.flow.configuration.apphance.ApphanceConfiguration
import com.apphance.flow.configuration.ios.IOSConfiguration
import com.apphance.flow.configuration.ios.IOSReleaseConfiguration
import com.apphance.flow.configuration.ios.IOSTestConfiguration
import com.apphance.flow.configuration.ios.variants.IOSVariantFactory
import com.apphance.flow.configuration.ios.variants.IOSVariantsConfiguration
import com.apphance.flow.configuration.reader.GradlePropertiesPersister
import com.apphance.flow.configuration.reader.PropertyPersister
import com.apphance.flow.configuration.release.ReleaseConfiguration
import com.apphance.flow.configuration.variants.VariantsConfiguration
import com.apphance.flow.detection.project.ProjectTypeDetector
import com.google.inject.AbstractModule
import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.multibindings.MapBinder
import groovy.transform.PackageScope
import org.gradle.api.Project

import static com.apphance.flow.detection.project.ProjectType.ANDROID
import static com.apphance.flow.detection.project.ProjectType.IOS

class ConfigurationModule extends AbstractModule {

    def configurations = [
            (ANDROID): [
                    AndroidConfiguration,
                    ApphanceConfiguration,
                    AndroidVariantsConfiguration,
                    AndroidReleaseConfiguration,
                    AndroidAnalysisConfiguration,
                    AndroidTestConfiguration,
            ],
            (IOS): [
                    IOSConfiguration,
                    ApphanceConfiguration,
                    IOSReleaseConfiguration,
                    IOSVariantsConfiguration,
                    IOSTestConfiguration,
            ],
    ]

    def variantFactories = [
            (ANDROID): [
                    new FactoryModuleBuilder().build(AndroidVariantFactory)
            ],
            (IOS): [
                    new FactoryModuleBuilder().build(IOSVariantFactory),
            ],
    ]

    private Project project

    @PackageScope
    ProjectTypeDetector typeDetector = new ProjectTypeDetector()

    def interfaces = [
            (ANDROID): [
                    (ProjectConfiguration): AndroidConfiguration,
                    (ReleaseConfiguration): AndroidReleaseConfiguration,
                    (VariantsConfiguration): AndroidVariantsConfiguration
            ],
            (IOS): [
                    (ProjectConfiguration): IOSConfiguration,
                    (ReleaseConfiguration): IOSReleaseConfiguration,
                    (VariantsConfiguration): IOSVariantsConfiguration
            ],
    ]

    ConfigurationModule(Project project) {
        this.project = project
    }

    @Override
    protected void configure() {
        MapBinder<Integer, AbstractConfiguration> m = MapBinder.newMapBinder(binder(), Integer, AbstractConfiguration)

        def pt = typeDetector.detectProjectType(project.rootDir)
        def index = 0

        configurations[pt].each { m.addBinding(index++).to(it) }
        interfaces[pt].each { bind(it.key).to(it.value) }
        variantFactories[pt].each { install(it) }

        bind(PropertyPersister).to(GradlePropertiesPersister)
    }
}
