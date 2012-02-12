package com.apphance.ameba

import java.io.BufferedReader;
import java.text.SimpleDateFormat
import java.util.ArrayList;

import org.gradle.api.GradleException
import org.gradle.api.Project

import com.apphance.ameba.plugins.projectconfiguration.ProjectBaseProperty;

class PropertyCategory {
    public static List<String> listProperties(Project project, Class<Enum> properties, boolean useComments) {
        String description = properties.getField('DESCRIPTION').get(null)
        List<String> s = []
        s << "###########################################################"
        s << "# ${description}"
        s << "###########################################################"
        properties.each {
            String comment = '# ' + it.description
            String propString = it.propertyName + '='
            if (it.defaultValue != null) {
                comment = comment + " [optional] default: <${it.defaultValue}>"
            }
            if (project.hasProperty(it.propertyName)) {
                propString = propString + project[it.propertyName]
            }
            if (useComments == true) {
                s << comment
            }
            s << propString
        }
        return s
    }

    public static String listPropertiesAsString(Project project, Class<Enum> properties, boolean useComments) {
        StringBuffer sb = new StringBuffer()
        listProperties(project, properties, useComments).each { sb << it + '\n' }
        return sb.toString()
    }

    public static Object readProperty(Project project, String propertyName, Object defaultValue = null) {
        if (project.hasProperty(propertyName)) {
            return project[propertyName]
        } else {
            return defaultValue
        }
    }

    public static Object readProperty(Project project, Enum property) {
        return readProperty(project, property.propertyName, property.defaultValue)
    }

    public static String getProjectPropertyFromUser(Project project, Enum property,
    ArrayList options, boolean useDefault, BufferedReader br) {
        return getProjectPropertyFromUser(project, property.propertyName, property.description, options, useDefault, br)
    }

    public static String getProjectPropertyFromUser(Project project, String propertyName, String description,
    ArrayList options, boolean useDefault, BufferedReader br) {
        String s = propertyName + ' (' + description + ')'
        if (useDefault) {
            s = s + '. Proposed values: ' + options
        }
        if (project.hasProperty(propertyName)) {
            s = s + '. Current value=' + project[propertyName] + '. Leave blank to don\'t change'
            System.out.println(s)
            String newValue = br.readLine()
            if (newValue.isEmpty() && !useDefault) {
                // don't change
            } else if (newValue.isEmpty() && useDefault) {
                project[propertyName] = options[0]
            } else {
                project[propertyName] = newValue
            }
        } else {
            System.out.println(s)
            String newValue = br.readLine()
            if (newValue.isEmpty() && useDefault) {
                project[propertyName] = options[0]
            } else {
                project[propertyName] = newValue
            }
        }
    }

    public static String readReleaseNotes(Project project) {
        if (project.hasProperty('release.notes')) {
            return project['release.notes']
        } else {
            def notes =  System.getenv('RELEASE_NOTES')
            if (notes == null || notes == "") {
                return null
            }
            project['release.notes'] = notes
            return notes
        }
    }

    public static String readPropertyOrEnvironmentVariable(Project project, Enum property) {
        return readPropertyOrEnvironmentVariable(project, property.propertyName)
    }

    public static String readPropertyOrEnvironmentVariable(Project project, String propertyName) {
        if (project.hasProperty(propertyName)) {
            return project[propertyName]
        } else if (System.getProperty(propertyName) != null){
            return System.getProperty(propertyName)
        } else {
            def envVariable = propertyName.toUpperCase().replace(".","_")
            def val = System.getenv(envVariable)
            if (val == null) {
                throw new GradleException("The property ${propertyName} was not defined (neither in project nor system) and ${envVariable} environment variable is missing.")
            }
            return val
        }
    }

    public static String isPropertyOrEnvironmentVariableDefined(Project project, Enum property) {
        return isPropertyOrEnvironmentVariableDefined(project, property.propertyName)
    }

    public static boolean isPropertyOrEnvironmentVariableDefined(Project project, String property) {
        if (project.hasProperty(property)) {
            return true
        } else if (System.getProperty(property) != null){
            return true
        } else {
            def envVariable = property.toUpperCase().replace(".","_")
            def val = System.getenv(envVariable)
            return (val != null)
        }
    }
    public static String readExpectedProperty(Project project, Enum property) {
        return readExpectedProperty(project, property.propertyName)
    }

    public static String readExpectedProperty(Project project, String property) {
        if (!project.hasProperty(property)) {
            throw new GradleException("I need ${property} property to be set on project")
        }
        return project[property]
    }

    public static ProjectConfiguration getProjectConfiguration(Project project){
        if (!project.hasProperty('project.configuration')) {
            project['project.configuration'] = new ProjectConfiguration()
        }
        return project['project.configuration']
    }

    public static void retrieveBasicProjectData(Project project) {
        ProjectConfiguration conf = getProjectConfiguration(project)
        conf.projectDirectoryName = readExpectedProperty(project,ProjectBaseProperty.PROJECT_DIRECTORY)
        conf.baseUrl = new URL(readExpectedProperty(project,ProjectBaseProperty.PROJECT_URL))
        conf.iconFile = new File(project.rootDir,readExpectedProperty(project,ProjectBaseProperty.PROJECT_ICON_FILE))
        retrieveLocale(project)
        conf.releaseNotes = readReleaseNotes(project)?.tokenize(",")
    }

    public static void retrieveLocale(Project project) {
        ProjectConfiguration conf = getProjectConfiguration(project)
        String language = readProperty(project, ProjectBaseProperty.PROJECT_LANGUAGE)
        String country = readProperty(project, ProjectBaseProperty.PROJECT_COUNTRY)
        if (language == null) {
            conf.locale = Locale.getDefault()
        } else {
            if (country == null) {
                conf.locale = new Locale(language)
            } else {
                conf.locale = new Locale(language,country)
            }
        }
        conf.buildDate = new SimpleDateFormat("dd-MM-yyyy HH:mm zzz", conf.locale).format(new Date())
    }
}
