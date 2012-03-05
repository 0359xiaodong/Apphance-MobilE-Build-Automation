package com.apphance.ameba.applyPlugins.android;

import static org.junit.Assert.*

import org.gradle.api.Project
import org.junit.Test

import com.apphance.ameba.AmebaCommonBuildTaskGroups
import com.apphance.ameba.android.plugins.buildplugin.AndroidPlugin;

class TestBasicAndroidTasks extends BaseAndroidTaskTest {
    protected Project getProject() {
        Project project = super.getProject()
        project.project.plugins.apply(AndroidPlugin.class)
        return project
    }

    @Test
    public void testBuildTasksAvailable() {
        verifyTasksInGroup(getProject(),[
            'cleanAndroid',
            'cleanClasses',
            'compileAndroid',
            'buildAll',
            'buildAllDebug',
            'buildAllRelease',
            'buildDebug-test',
            'buildRelease-market',
            'installDebug-test',
            'installRelease-market',
            'replacePackage',
            'updateProject',
			'copySources'
        ],AmebaCommonBuildTaskGroups.AMEBA_BUILD)
    }

    @Test
    public void testConfigurationTasksAvailable() {
        verifyTasksInGroup(getProject(),[
            'cleanConfiguration',
            'copyGalleryFiles',
            'readAndroidProjectConfiguration',
            'readAndroidVersionAndProjectName',
            'readProjectConfiguration',
        ],AmebaCommonBuildTaskGroups.AMEBA_CONFIGURATION)
    }

    @Test
    public void testReleaseTasksAvailable() {
        verifyTasksInGroup(getProject(),[],AmebaCommonBuildTaskGroups.AMEBA_RELEASE)
    }
}
