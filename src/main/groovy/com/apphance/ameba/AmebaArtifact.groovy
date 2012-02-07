package com.apphance.ameba

import java.io.File
import java.net.URL

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging


class AmebaArtifact  {
    private static Logger logger = Logging.getLogger(AmebaArtifact.class)
    String name
    URL url
    File location

    String getRelativeUrl(def baseUrl) {
        logger.info("Retrieving relative url from ${url} with base ${baseUrl}")
        String stringUrl = url.toString()
        String currentBaseUrl = getParentPath(baseUrl.toString())
        String prefix = ""
        while (currentBaseUrl != "") {
            if (stringUrl.startsWith(currentBaseUrl)) {
                def relativeUrl = prefix + stringUrl.substring(currentBaseUrl.length() + 1) // exnclude /
                logger.info("Relative url from ${url} with base ${baseUrl} : ${relativeUrl}")
                return relativeUrl
            } else {
                currentBaseUrl = getParentPath(currentBaseUrl);
                prefix = prefix + "../"
            }
        }
        logger.info("Return absolute url - no common url found : ${url}")
        return url.toString()
    }

    public static String getParentPath(String path) {
        if ((path == null) || path.equals("") || path.equals("/")) {
            return "";
        }
        int lastSlashPos = path.lastIndexOf('/')
        if (lastSlashPos >= 0) {
            return path.substring(0, lastSlashPos);
        } else {
            return "";
        }
    }

    @Override
    public String toString() {
        return this.getProperties()
    }
}
