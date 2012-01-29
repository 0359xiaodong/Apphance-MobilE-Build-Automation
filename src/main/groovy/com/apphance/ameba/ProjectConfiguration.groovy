package com.apphance.ameba

import java.io.File
import java.net.URL
import java.util.Collection
import java.util.Locale

class ProjectConfiguration  {
    String projectName
    String projectDirectoryName
    Collection<String> releaseNotes
    URL baseUrl = new URL("http://example.com")
    File otaDirectory = new File('ota')
    File tmpDirectory = new File('tmp')
    File logDirectory = new File('log')
    File buildDirectory = new File('build')
    String versionString = "NOVERSION"
    Integer versionCode = 0

    String bundleId
    String buildDate
    AmebaArtifact sourcesZip
    AmebaArtifact documentationZip
    AmebaArtifact imageMontageFile
    AmebaArtifact mailMessageFile
    AmebaArtifact qrCodeFile
    Collection<String> releaseMailFlags
    String releaseMailSubject
    String releaseMailFrom
    String releaseMailTo
    File iconFile
    AmebaArtifact galleryCss
    AmebaArtifact galleryJs
    AmebaArtifact galleryTrans
    Locale locale
    Collection<String> sourceExcludes = []
    Collection<String> commitFilesOnVCS = []

    String getFullVersionString() {
        return "${versionString}_${versionCode}"
    }

    String getProjectVersionedName() {
        return "${projectName}-${fullVersionString}"
    }

    File getTargetDirectory() {
        return new File(new File(otaDirectory, projectDirectoryName),fullVersionString)
    }

    URL getVersionedApplicationUrl() {
        return new URL(baseUrl, "${projectDirectoryName}/${fullVersionString}/")
    }

    @Override
    public String toString() {
        return this.getProperties()
    }
}
