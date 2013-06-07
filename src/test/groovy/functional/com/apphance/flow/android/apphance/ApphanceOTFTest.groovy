package com.apphance.flow.android.apphance

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import static com.apphance.flow.configuration.release.ReleaseConfiguration.OTA_DIR
import static org.junit.Assert.assertTrue

class ApphanceOTFTest {

    public static final String[] GRADLE_DAEMON_ARGS = ['-XX:MaxPermSize=1024m', '-XX:+CMSClassUnloadingEnabled',
            '-XX:+CMSPermGenSweepingEnabled', '-XX:+HeapDumpOnOutOfMemoryError', '-Xmx1024m'] as String[]

    static File testNovariantsProject = new File("testProjects/android/android-novariants")
    static ProjectConnection connection

    @BeforeClass
    static void beforeClass() {
        connection = GradleConnector.newConnector().forProjectDirectory(testNovariantsProject).connect();
    }

    @AfterClass
    static public void afterClass() {
        connection.close()
    }


    protected void runGradleNoVariants(String... tasks) {
        def launcher = connection.newBuild().forTasks(tasks)
        launcher.setJvmArguments(GRADLE_DAEMON_ARGS)
        launcher.run()
    }

    @Test
    void testAddApphanceFlow() {
        File mainActivityFile = new File(testNovariantsProject, "src/com/apphance/flowTest/android/TestActivity.java")
        File tmpCopy = new File("tmpCopy")
        tmpCopy.delete()
        tmpCopy << mainActivityFile.getText()

        runGradleNoVariants('updateProject', 'cleanFlow', 'buildAllDebug')
        assertTrue(new File(testNovariantsProject,
                "${OTA_DIR}/TestAndroidProject/1.0.1_42/TestAndroidProject-debug-TestDebug-1.0.1_42.apk").exists())

        mainActivityFile.delete()
        mainActivityFile << tmpCopy.getText()
        tmpCopy.delete()
    }
}