package com.apphance.flow.plugins.android.analysis.tasks

import com.apphance.flow.configuration.android.variants.AndroidVariantsConfiguration
import com.apphance.flow.executor.AndroidExecutor
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

public class LintTask extends DefaultTask {

    static String NAME = 'lint'
    String group = 'verification'
    String description = 'Runs lint analysis on main variant of project'

    @Inject AndroidVariantsConfiguration androidVariantsConf
    @Inject AndroidExecutor androidExecutor

    @TaskAction
    public void run() {
        logger.lifecycle "Running lint task"

        def lintReportDir = new File(project.rootDir, 'build/reports/lint')
        lintReportDir.mkdirs()

        androidExecutor.runLint androidVariantsConf.mainVariant.tmpDir, lintReportDir
    }
}
