package com.apphance.flow.plugins.ios.release

import com.apphance.flow.configuration.ios.IOSReleaseConfiguration
import com.apphance.flow.plugins.ios.buildplugin.IOSSingleVariantBuilder
import com.apphance.flow.plugins.ios.release.tasks.AvailableArtifactsInfoTask
import com.apphance.flow.plugins.ios.release.tasks.PrepareMailMessageTask
import com.apphance.flow.plugins.release.tasks.AbstractUpdateVersionTask
import spock.lang.Specification

import static com.apphance.flow.plugins.FlowTasksGroups.FLOW_RELEASE
import static org.gradle.testfixtures.ProjectBuilder.builder

class IOSReleasePluginSpec extends Specification {


    def 'tasks defined in plugin available when configuration is active'() {
        given:
        def project = builder().build()

        and:
        def irc = Mock(IOSReleaseConfiguration)
        irc.isEnabled() >> true

        and:
        def irp = new IOSReleasePlugin()
        irp.releaseConf = irc
        irp.builder = new IOSSingleVariantBuilder()
        irp.listener = GroovyStub(IOSReleaseListener)

        when:
        irp.apply(project)

        then:
        project.tasks[AbstractUpdateVersionTask.NAME].group == FLOW_RELEASE.name()
        project.tasks[PrepareMailMessageTask.NAME].group == FLOW_RELEASE.name()
        project.tasks[AvailableArtifactsInfoTask.NAME].group == FLOW_RELEASE.name()

        and:
        project.tasks[PrepareMailMessageTask.NAME].dependsOn.flatten().contains(AvailableArtifactsInfoTask.NAME)

        and:
        irp.builder.buildListeners.size() > 0
    }

    def 'no tasks available when configuration is inactive'() {
        given:
        def project = builder().build()

        and:
        def irc = Mock(IOSReleaseConfiguration)
        irc.isEnabled() >> false

        and:
        def irp = new IOSReleasePlugin()
        irp.releaseConf = irc

        when:
        irp.apply(project)

        then:
        !project.getTasksByName(AbstractUpdateVersionTask.NAME, false)
        !project.getTasksByName(PrepareMailMessageTask.NAME, false)
        !project.getTasksByName(AvailableArtifactsInfoTask.NAME, false)
    }
}