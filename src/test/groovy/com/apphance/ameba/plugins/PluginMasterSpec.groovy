package com.apphance.ameba.plugins

import com.apphance.ameba.android.plugins.analysis.AndroidAnalysisPlugin
import com.apphance.ameba.android.plugins.apphance.AndroidApphancePlugin
import com.apphance.ameba.android.plugins.buildplugin.AndroidPlugin
import com.apphance.ameba.android.plugins.jarlibrary.AndroidJarLibraryPlugin
import com.apphance.ameba.android.plugins.release.AndroidReleasePlugin
import com.apphance.ameba.android.plugins.test.AndroidTestPlugin
import com.apphance.ameba.detection.ProjectTypeDetector
import com.apphance.ameba.di.CommandExecutorModule
import com.apphance.ameba.di.EnvironmentModule
import com.apphance.ameba.ios.plugins.apphance.IOSApphancePlugin
import com.apphance.ameba.ios.plugins.buildplugin.IOSPlugin
import com.apphance.ameba.ios.plugins.framework.IOSFrameworkPlugin
import com.apphance.ameba.ios.plugins.ocunit.IOSUnitTestPlugin
import com.apphance.ameba.ios.plugins.release.IOSReleasePlugin
import com.apphance.ameba.plugins.projectconfiguration.ProjectConfigurationPlugin
import com.apphance.ameba.plugins.release.ProjectReleasePlugin
import com.google.inject.AbstractModule
import com.google.inject.Guice
import org.gradle.api.Project
import org.gradle.api.plugins.PluginContainer
import spock.lang.Specification
import spock.lang.Unroll

import static com.apphance.ameba.util.ProjectType.ANDROID
import static com.apphance.ameba.util.ProjectType.IOS

class PluginMasterSpec extends Specification {

    @Unroll
    def 'test if all #type plugins are applied'() {
        given:
        def mocks = (plugins + commonPlugins).collect(mockToMap).sum()
        def master = createInjectorForPluginsMocks(mocks).getInstance(PluginMaster)

        and:
        def project = Mock(Project)
        project.plugins >> Mock(PluginContainer)

        and: 'tell that project is Android'
        projectTypeDetectorMock.detectProjectType(_) >> type

        when:
        master.enhanceProject(project)

        then:
        interaction {
            mocks.each { type, instance ->
                1 * instance.apply(project)
            }
        }

        where:
        type    | plugins
        ANDROID | androidPlugins
        IOS     | iosPlugins
    }

    def 'test Android plugins order'() {
        given:
        def mocks = (commonPlugins + androidPlugins).collect(mockToMap).sum()
        def master = createInjectorForPluginsMocks(mocks).getInstance(PluginMaster)

        and:
        def project = Mock(Project)
        project.plugins >> Mock(PluginContainer)

        and: 'tell that project is Android'
        projectTypeDetectorMock.detectProjectType(_) >> ANDROID

        when:
        master.enhanceProject(project)

        then:
        1 * mocks[before].apply(project)

        then:
        1 * mocks[after].apply(project)

        where:
        before                     | after
        ProjectConfigurationPlugin | AndroidPlugin
        AndroidPlugin              | ProjectReleasePlugin
        ProjectReleasePlugin       | AndroidReleasePlugin
    }

    def 'test iOS plugins order'() {
        given:
        def mocks = (commonPlugins + iosPlugins).collect(mockToMap).sum()
        def master = createInjectorForPluginsMocks(mocks).getInstance(PluginMaster)

        and:
        def project = Mock(Project)
        project.plugins >> Mock(PluginContainer)

        and: 'tell that project is iOS'
        projectTypeDetectorMock.detectProjectType(_) >> IOS

        when:
        master.enhanceProject(project)

        then:
        1 * mocks[before].apply(project)

        then:
        1 * mocks[after].apply(project)

        where:
        before                     | after
        ProjectConfigurationPlugin | IOSPlugin
        IOSPlugin                  | ProjectReleasePlugin
        ProjectReleasePlugin       | IOSReleasePlugin
    }

    final projectTypeDetectorMock = Mock(ProjectTypeDetector)

    def mockToMap = { [(it): Mock(it)] }

    static commonPlugins = [ProjectConfigurationPlugin, ProjectReleasePlugin]

    static androidPlugins = [
            AndroidPlugin,
            AndroidAnalysisPlugin,
            AndroidApphancePlugin,
            AndroidJarLibraryPlugin,
            AndroidReleasePlugin,
            AndroidTestPlugin
    ]

    static iosPlugins = [
            IOSPlugin,
            IOSFrameworkPlugin,
            IOSReleasePlugin,
            IOSApphancePlugin,
            IOSUnitTestPlugin
    ]

    def createInjectorForPluginsMocks(mocks) {
        def project = Mock(Project)
        project.file('log') >> new File(System.properties['java.io.tmpdir'])
        return Guice.createInjector(
                new EnvironmentModule(),
                new CommandExecutorModule(project),
                new AbstractModule() {

                    @Override
                    protected void configure() {
                        bind(ProjectTypeDetector).toInstance(projectTypeDetectorMock)
                        mocks.each { type, instance ->
                            bind(type).toInstance(instance)
                        }
                    }
                })
    }
}