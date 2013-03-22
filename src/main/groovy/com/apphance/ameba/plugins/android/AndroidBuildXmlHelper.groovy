package com.apphance.ameba.plugins.android

import groovy.xml.XmlUtil

/**
 * Performs various Android manifest XML operations.
 */
class AndroidBuildXmlHelper {

    private final static String BUILD_XML = 'build.xml'

    static String projectName(File projectDir) {
        def buildXml = new File(projectDir, BUILD_XML)
        def xml = new XmlSlurper().parse(buildXml)
        xml.@'name'.text()
    }

    static void replaceProjectName(File projectDir, String newName) {
        def buildXml = new File(projectDir, BUILD_XML)
        def xml = new XmlSlurper().parse(buildXml)
        xml.@'name' = newName
        buildXml.delete()
        buildXml.write(XmlUtil.serialize(xml))
    }
}