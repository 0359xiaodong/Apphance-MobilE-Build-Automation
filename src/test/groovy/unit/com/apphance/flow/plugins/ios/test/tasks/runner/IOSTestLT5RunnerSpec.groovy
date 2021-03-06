package com.apphance.flow.plugins.ios.test.tasks.runner

import com.apphance.flow.configuration.ios.variants.AbstractIOSVariant
import com.apphance.flow.executor.IOSExecutor
import com.apphance.flow.executor.linker.SimpleFileLinker
import com.apphance.flow.plugins.ios.parsers.PbxJsonParser
import com.apphance.flow.plugins.ios.parsers.XCSchemeParser
import com.apphance.flow.plugins.ios.test.tasks.pbx.IOSTestPbxEnhancer
import com.apphance.flow.plugins.ios.test.tasks.results.parser.OCUnitTestSuite
import spock.lang.Specification

import static com.apphance.flow.configuration.ios.XCAction.TEST_ACTION

class IOSTestLT5RunnerSpec extends Specification {

    def runner = new IOSTestLT5Runner()

    def 'error message is generated well'() {
        given:
        runner.variant = GroovyStub(AbstractIOSVariant) { getName() >> 'v1' }
        runner.fileLinker = new SimpleFileLinker()
        def results = new File('results')

        expect:
        runner.errorMessage('t1', 'c1', results) == "Error while executing tests for variant: v1, target: t1," +
                " configuration c1. For further details investigate test results: $results.absolutePath"

        cleanup:
        results.delete()
    }

    def 'exception thrown when failed present'() {
        when:
        runner.verifyTestResults([GroovyMock(OCUnitTestSuite) {
            getFailureCount() >> 1
        }], 'error message')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'error message'
    }

    def 'no exception thrown when all tests passed'() {
        when:
        runner.verifyTestResults([GroovyMock(OCUnitTestSuite) {
            getFailureCount() >> 0
        }], 'error message')

        then:
        noExceptionThrown()
    }

    def 'task action is executed with all interactions'() {
        given:
        def schemeFile = new File('schemeFile')
        def pbxFile = new File('pbxFile')
        def tmpDir = new File('tmpDir')
        tmpDir.mkdirs()
        def variant = GroovyMock(AbstractIOSVariant) {
            getName() >> 'v1'
            getSchemeFile() >> schemeFile
            getPbxFile() >> pbxFile
            getTmpDir() >> tmpDir
            getXcodebuildExecutionPath() >> ['xcodebuild']
        }

        and:
        def schemeParser = GroovyMock(XCSchemeParser)
        def testPbxEnhancer = GroovyMock(IOSTestPbxEnhancer)
        def pbxJsonParser = GroovyMock(PbxJsonParser)
        def executor = GroovyMock(IOSExecutor)

        and:
        runner.schemeParser = schemeParser
        runner.testPbxEnhancer = testPbxEnhancer
        runner.pbxJsonParser = pbxJsonParser
        runner.executor = executor
        runner.fileLinker = new SimpleFileLinker()

        when:
        runner.runTests(variant)

        then:
        1 * schemeParser.findActiveTestableBlueprintIds(schemeFile) >> ['3145']
        1 * testPbxEnhancer.addShellScriptToBuildPhase(variant, ['3145'])
        1 * pbxJsonParser.getTargetForBlueprintId() >> { i, j -> 't1' }.memoize()
        1 * schemeParser.configuration(schemeFile, TEST_ACTION) >> 'c1'
        1 * executor.runTestsLT5(tmpDir, ['xcodebuild', '-target', 't1', '-configuration', 'c1', '-sdk', 'iphonesimulator', 'clean', 'build'], new File(tmpDir, 'test-v1-t1.log').absolutePath)

        and:
        noExceptionThrown()

        cleanup:
        schemeFile.delete()
        pbxFile.delete()
        tmpDir.deleteDir()
    }
}
