package com.apphance.flow.configuration

import com.apphance.flow.configuration.properties.AbstractProperty
import com.apphance.flow.configuration.reader.PropertyPersister
import com.apphance.flow.validation.PropertyValidator

import javax.inject.Inject
import java.lang.reflect.Field

import static java.util.ResourceBundle.getBundle
import static org.apache.commons.lang.StringUtils.join
import static org.gradle.api.logging.Logging.getLogger

abstract class AbstractConfiguration {

    protected logger = getLogger(getClass())
    protected docBundle = getBundle('doc')
    protected validationBundle = getBundle('validation')

    @Inject PropertyPersister propPersister
    @Inject PropertyValidator propValidator

    abstract String getConfigurationName()

    @Inject
    void init() {
        propertyFields.each {
            logger.debug "Initializing property $it.name to value: ${propPersister.get(it.name)}"
            it.value = propPersister.get(it.name)
        }

        String enabled = propPersister.get(enabledPropKey)
        def enabledValue = Boolean.valueOf(enabled)
        if (enabled && enabledValue != this.enabled) {
            this.enabled = enabledValue
        }
    }

    List<AbstractProperty> getPropertyFields() {
        List<Field> fields = []
        def findDeclaredFields
        findDeclaredFields = { Class c ->
            fields.addAll(c.declaredFields)
            if (c.superclass)
                findDeclaredFields(c.superclass)
        }
        findDeclaredFields(getClass())
        fields.findAll {
            it.accessible = true
            def fieldClass = it.get(this).getClass()
            fieldClass.getClass() == Object ? false : fieldClass?.superclass == AbstractProperty
        }.collect {
            it.accessible = true
            it.get(this) as AbstractProperty
        }
    }

    void setEnabled(boolean enabled) {
        throw new IllegalStateException("Cannot change '$configurationName' enabled status to: $enabled")
    }

    abstract boolean isEnabled()

    String getEnabledPropKey() { configurationName.replace(' ', '.').toLowerCase() + '.enabled' }

    boolean canBeEnabled() { true }

    String explainDisabled() { '' }

    void validate(List<String> errors) {}

    Collection<? extends AbstractConfiguration> getSubConfigurations() {
        []
    }

    @Override
    public String toString() {
        "Configuration '$configurationName'" + (propertyFields ? " \n${join(propertyFields, '\n')}\n" : '');
    }
}