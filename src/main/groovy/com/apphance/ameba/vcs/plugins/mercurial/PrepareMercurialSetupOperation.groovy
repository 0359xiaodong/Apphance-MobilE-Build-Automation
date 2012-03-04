package com.apphance.ameba.vcs.plugins.mercurial

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging

import com.apphance.ameba.AbstractPrepareSetupOperation;
import com.apphance.ameba.PropertyCategory;


class PrepareMercurialSetupOperation extends AbstractPrepareSetupOperation {
    Logger logger = Logging.getLogger(PrepareMercurialSetupOperation.class)

    PrepareMercurialSetupOperation() {
        super(MercurialProperty.class)
    }


    void prepareSetup() {
        logger.lifecycle("Preparing ${propertyDescription}")
        BufferedReader br = getReader()
        use(PropertyCategory) {
            MercurialProperty.each {
                project.getProjectPropertyFromUser(it, null, br)
            }
            appendProperties()
        }
    }
}