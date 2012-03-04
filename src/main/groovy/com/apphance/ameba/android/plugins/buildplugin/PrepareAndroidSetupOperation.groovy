package com.apphance.ameba.android.plugins.buildplugin


import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import com.apphance.ameba.AbstractPrepareSetupOperation
import com.apphance.ameba.PropertyCategory
import com.apphance.ameba.android.AndroidCommandParser;
import com.apphance.ameba.android.AndroidProjectConfiguration;
import com.apphance.ameba.android.AndroidProjectConfigurationRetriever;


class PrepareAndroidSetupOperation extends AbstractPrepareSetupOperation {
    Logger logger = Logging.getLogger(PrepareAndroidSetupOperation.class)

    AndroidProjectConfigurationRetriever retriever = new AndroidProjectConfigurationRetriever()
    AndroidProjectConfiguration androidConf

    PrepareAndroidSetupOperation() {
        super(AndroidProjectProperty.class)
    }

    void prepareSetup() {
        logger.lifecycle("Preparing ${propertyDescription}")
        androidConf = retriever.getAndroidProjectConfiguration(project)
        use (PropertyCategory) {
            BufferedReader br = getReader()
            AndroidProjectProperty.each {
                switch (it) {
                    case AndroidProjectProperty.MAIN_VARIANT:
                        project.getProjectPropertyFromUser(it, androidConf.variants, br)
                        break;
                    case AndroidProjectProperty.MIN_SDK_TARGET:
                        List targets = AndroidCommandParser.getTargets()
                        project.getProjectPropertyFromUser(it, targets, br)
                        break;
                    default:
                        project.getProjectPropertyFromUser(it, null, br)
                }
            }
            appendProperties()
        }
    }
}