package com.apphance.flow.plugins.android.test.tasks

import com.apphance.flow.configuration.android.AndroidConfiguration
import com.apphance.flow.configuration.android.AndroidTestConfiguration
import com.apphance.flow.executor.AndroidExecutor
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

import static com.apphance.flow.plugins.FlowTasksGroups.FLOW_TEST

class CreateAVDTask extends DefaultTask {

    static String NAME = 'createAVD'
    String group = FLOW_TEST
    String description = 'Prepares AVDs for emulator'

    @Inject AndroidConfiguration conf
    @Inject AndroidTestConfiguration testConf
    @Inject AndroidExecutor androidExecutor

    @TaskAction
    void createAVD() {
        boolean emulatorExists = androidExecutor.listAvd().any { it == testConf.emulatorName }
        if (!testConf.getAVDDir().exists() || !emulatorExists) {
            testConf.getAVDDir().mkdirs()
            logger.lifecycle("Creating emulator avd: ${testConf.emulatorName}")
            androidExecutor.createAvdEmulator(
                    conf.rootDir,
                    testConf.emulatorName,
                    testConf.emulatorTarget.value,
                    testConf.emulatorSkin.value,
                    testConf.emulatorCardSize.value,
                    testConf.getAVDDir(),
                    testConf.emulatorSnapshotEnabled.value)
            logger.lifecycle("Created emulator avd: ${testConf.emulatorName}")
        } else {
            logger.lifecycle("Skipping creating emulator: ${testConf.emulatorName}. It already exists.")
        }
    }
}