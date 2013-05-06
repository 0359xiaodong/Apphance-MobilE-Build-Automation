package com.apphance.ameba.configuration.android

import com.apphance.ameba.configuration.AbstractConfiguration
import com.apphance.ameba.configuration.properties.StringProperty
import com.google.inject.Inject

@com.google.inject.Singleton
class AndroidJarLibraryConfiguration extends AbstractConfiguration {

    final String configurationName = 'Android Jar Library Configuration'

    private boolean enabledInternal = false

    private AndroidConfiguration androidConf

    @Inject
    AndroidJarLibraryConfiguration(AndroidConfiguration androidConfiguration) {
        this.androidConf = androidConfiguration
    }

    @Override
    boolean isEnabled() {
        enabledInternal && androidConf.isEnabled()
    }

    @Override
    void setEnabled(boolean enabled) {
        enabledInternal = enabled
    }

    def resourcePrefix = new StringProperty(
            name: 'android.jar.library.resPrefix',
            message: 'Internal directory name used to embed resources in the jar',
            validator: {
                try {
                    def file = new File(androidConf.tmpDir, "${it}-res")
                    return file.mkdirs() || file.directory
                } catch (Exception e) { return false }
            }
    )

    @Override
    void checkProperties() {
        check resourcePrefix.validator(resourcePrefix.value), "Property ${resourcePrefix.name} is not valid! Can not create '${new File(androidConf.tmpDir,"${resourcePrefix.value}-res")}' directory"
    }
}
