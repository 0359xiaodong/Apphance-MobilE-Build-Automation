package com.apphance.flow.configuration.apphance

import com.apphance.flow.configuration.AbstractConfiguration
import com.apphance.flow.configuration.ProjectConfiguration
import com.apphance.flow.configuration.properties.BooleanProperty
import com.apphance.flow.configuration.properties.StringProperty
import com.google.inject.Singleton

import javax.inject.Inject

@Singleton
class ApphanceConfiguration extends AbstractConfiguration {

    String configurationName = 'Apphance Configuration'
    private boolean enabledInternal = false

    @Inject ProjectConfiguration conf

    @Override
    boolean isEnabled() {
        enabledInternal && conf.isEnabled()
    }

    @Override
    void setEnabled(boolean enabled) {
        enabledInternal = enabled
    }

    def user = new StringProperty(
            name: 'apphance.user',
            message: 'Apphance user (used for uploading apk to apphance server)'
    )

    def pass = new StringProperty(
            name: 'apphance.pass',
            message: 'Apphance pass (used for uploading apk to apphance server)'
    )

    def enableShaking = new BooleanProperty(
            name: 'apphance.enableShaking',
            message: "Report bug to apphance by shaking device",
            possibleValues: { ['false', 'true'] as List<String> }
    )
}
