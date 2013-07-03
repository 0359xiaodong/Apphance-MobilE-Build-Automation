package com.apphance.flow.plugins.ios.buildplugin

import com.apphance.flow.configuration.ios.variants.AbstractIOSVariant
import com.apphance.flow.executor.IOSExecutor
import com.apphance.flow.plugins.ios.builder.IOSArtifactProvider
import com.apphance.flow.plugins.ios.parsers.PlistParser
import com.apphance.flow.plugins.ios.release.IOSReleaseListener
import com.google.inject.Singleton

import javax.inject.Inject

import static com.apphance.flow.util.file.FileManager.MAX_RECURSION_LEVEL
import static groovy.io.FileType.FILES
import static org.apache.commons.lang.StringUtils.isNotBlank
import static org.gradle.api.logging.Logging.getLogger

/**
 * Builds single variant for iOS projects.
 *
 */
@Singleton
class IOSSingleVariantBuilder {

    def logger = getLogger(getClass())

    final Set<IOSBuildListener> buildListeners = [] as Set<IOSReleaseListener>

    @Inject IOSExecutor executor
    @Inject IOSArtifactProvider artifactProvider
    @Inject PlistParser plistParser

    void registerListener(IOSReleaseListener listener) {
        buildListeners << listener
    }

    void buildVariant(AbstractIOSVariant variant) {
        def newBundleId = variant.bundleId.value
        if (isNotBlank(newBundleId)) {
            def oldBundleId = plistParser.bundleId(variant.plist)
            plistParser.replaceBundledId(variant.plist, oldBundleId, newBundleId)
            replaceBundleInAllSourceFiles(variant.tmpDir, oldBundleId, newBundleId)
        }
        executor.buildVariant(variant.tmpDir, variant.buildCmd())
        buildListeners.each {
            it.buildDone(artifactProvider.builderInfo(variant))
        }
    }

    private void replaceBundleInAllSourceFiles(File dir, String newBundleId, String oldBundleId) {
        String valueToFind = 'bundleWithIdentifier:@"' + oldBundleId
        String valueToReplace = 'bundleWithIdentifier:@"' + newBundleId
        findAllSourceFiles(dir).each { file ->
            String t = file.text
            if (t.contains(valueToFind)) {
                file.write(t.replace(valueToFind, valueToReplace))
                logger.lifecycle("Replaced the $valueToFind with $valueToReplace in $file")
            }
        }
    }

    private Collection<File> findAllSourceFiles(File dir) {
        def result = []
        dir.traverse([type: FILES, maxDepth: MAX_RECURSION_LEVEL]) {
            if ((it.name.endsWith('.m') || it.name.endsWith('.h')) && !it.path.contains('/External/')) {
                logger.info("Adding source file ${it} to processing list")
                result << it
            }
        }
        result
    }
}
