package com.apphance.ameba.plugins.android.release

import com.apphance.ameba.configuration.android.AndroidReleaseConfiguration
import com.apphance.ameba.plugins.android.builder.AndroidSingleVariantApkBuilder
import com.apphance.ameba.plugins.android.builder.AndroidSingleVariantJarBuilder
import com.apphance.ameba.plugins.android.release.tasks.AvailableArtifactsInfoTask
import com.apphance.ameba.plugins.android.release.tasks.PrepareMailMessageTask
import com.apphance.ameba.plugins.android.release.tasks.UpdateVersionTask
import com.apphance.ameba.plugins.release.tasks.PrepareForReleaseTask
import spock.lang.Specification

import static com.apphance.ameba.plugins.AmebaCommonBuildTaskGroups.AMEBA_RELEASE
import static org.gradle.testfixtures.ProjectBuilder.builder

class AndroidReleasePluginSpec extends Specification {

    def 'tasks defined in plugin available when configuration is active'() {

        given:
        def project = builder().build()

        and:
        def arp = new AndroidReleasePlugin()
        arp.apkBuilder = Mock(AndroidSingleVariantApkBuilder)
        arp.jarBuilder = Mock(AndroidSingleVariantJarBuilder)

        and: 'create mock android release configuration and set it'
        def arc = Mock(AndroidReleaseConfiguration)
        arc.isEnabled() >> true
        arp.releaseConf = arc

        when:
        arp.apply(project)

        then: 'every single task is in correct group'
        project.tasks[UpdateVersionTask.NAME].group == AMEBA_RELEASE
        project.tasks[AvailableArtifactsInfoTask.NAME].group == AMEBA_RELEASE
        project.tasks[PrepareMailMessageTask.NAME].group == AMEBA_RELEASE

        then: 'every task has correct dependencies'

        project.tasks[PrepareMailMessageTask.NAME].dependsOn.flatten().containsAll(
                AvailableArtifactsInfoTask.NAME,
                PrepareForReleaseTask.NAME)
    }

    def 'no tasks available when configuration is inactive'() {
        given:
        def project = builder().build()

        and:
        def arp = new AndroidReleasePlugin()

        and: 'create mock android release configuration and set it'
        def arc = Mock(AndroidReleaseConfiguration)
        arc.isEnabled() >> false
        arp.releaseConf = arc

        when:
        arp.apply(project)

        then:
        !project.getTasksByName(UpdateVersionTask.NAME, false)
        !project.getTasksByName(AvailableArtifactsInfoTask.NAME, false)
        !project.getTasksByName(PrepareMailMessageTask.NAME, false)
    }
}
