package com.apphance.ameba.runBuilds.ios;

import static org.junit.Assert.*;

import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.junit.After
import org.junit.Before
import org.junit.Test;


class CheckIOSPluginDependenciesTest {
    File testProject = new File("testProjects/test-dependencies")
    File gradleBuild = new File(testProject,"build.gradle")

    @Before
    void before() {
        gradleBuild.delete()
    }


    @After
    void after() {
        gradleBuild.delete()
    }

    String runTests(File gradleBuildToCopy, String expected, String ... tasks) {
        gradleBuild << gradleBuildToCopy.text
        ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(testProject).connect();
        ByteArrayOutputStream os = new ByteArrayOutputStream()
        try {
            BuildLauncher bl = connection.newBuild().forTasks(tasks);
            bl.setStandardOutput(os)
            bl.run();
            def res = os.toString("UTF-8")
            println res
            assertFalse(res.contains('BUILD SUCCESSFUL'))
            return res
        } catch (BuildException e) {
            def res = os.toString("UTF-8")
            def msg = e.cause.cause.cause.message
            println msg
            assertTrue(msg.contains(expected))
            println res
            return res
        } finally {
            connection.close();
        }
    }


    @Test
    public void testIosPluginDependencies() throws Exception {
        String res = runTests(new File(testProject, 'ios-plugin.gradle'), 'ProjectConfigurationPlugin has not been loaded yet')
        println res
    }

    @Test
    public void testIosFrameworkPluginDependencies() throws Exception {
        String res = runTests(new File(testProject, 'ios-framework.gradle'), 'IOSPlugin has not been loaded yet')
        println res
    }

    @Test
    public void testIosReleaseIosPluginDependencies() throws Exception {
        String res = runTests(new File(testProject, 'ios-release-ios.gradle'), 'IOSPlugin has not been loaded yet')
        println res
    }

    @Test
    public void testIosReleaseCommonPluginDependencies() throws Exception {
        String res = runTests(new File(testProject, 'ios-release-common.gradle'), 'ReleasePlugin has not been loaded yet')
        println res
    }


    @Test
    public void testIosOcunitPluginDependencies() throws Exception {
        String res = runTests(new File(testProject, 'ios-ocunit.gradle'), 'IOSPlugin has not been loaded yet')
        println res
    }

    @Test
    public void testIosFonemonkeyPluginDependencies() throws Exception {
        String res = runTests(new File(testProject, 'ios-fonemonkey.gradle'), 'IOSPlugin has not been loaded yet')
        println res
    }
    @Test
    public void testIosCedarPluginDependencies() throws Exception {
        String res = runTests(new File(testProject, 'ios-cedar.gradle'), 'IOSPlugin has not been loaded yet')
        println res
    }

    @Test
    public void testIosKifPluginDependencies() throws Exception {
        String res = runTests(new File(testProject, 'ios-kif.gradle'), 'IOSPlugin has not been loaded yet')
        println res
    }
}