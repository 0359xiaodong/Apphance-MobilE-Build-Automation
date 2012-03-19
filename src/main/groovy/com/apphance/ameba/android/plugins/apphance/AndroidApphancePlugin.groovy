package com.apphance.ameba.android.plugins.apphance

import java.io.File

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import com.apphance.ameba.AmebaArtifact;
import com.apphance.ameba.AmebaCommonBuildTaskGroups
import com.apphance.ameba.ProjectConfiguration
import com.apphance.ameba.ProjectHelper
import com.apphance.ameba.PropertyCategory
import com.apphance.ameba.android.AndroidArtifactBuilderInfo
import com.apphance.ameba.android.AndroidManifestHelper
import com.apphance.ameba.android.AndroidProjectConfiguration;
import com.apphance.ameba.android.AndroidProjectConfigurationRetriever;
import com.apphance.ameba.android.AndroidSingleVariantBuilder;
import com.apphance.ameba.android.plugins.buildplugin.AndroidPlugin
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.util.EntityUtils;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset

// Uncomment this annotation for eclipse building, leave commented for gradle build
// Caused by: http://issues.gradle.org/browse/GRADLE-1162
//@Grab(group='org.apache.httpcomponents', module='httpmime', version='4.1.3')
class AndroidApphancePlugin implements Plugin<Project>{

    static Logger logger = Logging.getLogger(AndroidApphancePlugin.class)

    ProjectHelper projectHelper
    ProjectConfiguration conf
    File findbugsHomeDir
    AndroidManifestHelper manifestHelper
    AndroidProjectConfiguration androidConf

    public void apply(Project project) {
        ProjectHelper.checkAllPluginsAreLoaded(project, this.class, AndroidPlugin.class)
        use (PropertyCategory) {
            this.projectHelper = new ProjectHelper()
            this.conf = project.getProjectConfiguration()
            manifestHelper = new AndroidManifestHelper()
            this.androidConf = new AndroidProjectConfigurationRetriever().getAndroidProjectConfiguration(project)
            preprocessBuildsWithApphance(project)
            prepareConvertLogsToApphance(project)
            prepareConvertLogsToAndroid(project)
            prepareRemoveApphaceFromManifest(project)
            prepareRestoreManifestBeforeApphance(project)
            project.prepareSetup.prepareSetupOperations << new PrepareApphanceSetupOperation()
            project.verifySetup.verifySetupOperations << new VerifyApphanceSetupOperation()
            project.showSetup.showSetupOperations << new ShowApphancePropertiesOperation()
        }
    }

    void prepareConvertLogsToApphance(project) {
        def task = project.task('convertLogsToApphance')
        task.description = "Converts all logs to apphance from android logs for Debug builds"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_APPHANCE_SERVICE
        task << {
            androidConf.variants.each { variant ->
                if (androidConf.debugRelease[variant] == 'Debug') {
                    replaceLogsWithApphance(project, variant)
                }
            }
        }
    }

    void prepareConvertLogsToAndroid(project) {
        def task = project.task('convertLogsToAndroid')
        task.description = "Converts all logs to android from apphance logs for Release builds"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_APPHANCE_SERVICE
        task << {
            androidConf.variants.each { variant ->
                if (androidConf.debugRelease[variant] == 'Release') {
                    replaceLogsWithAndroid(project, variant)
                }
            }
        }
    }

    void prepareRemoveApphaceFromManifest(project) {
        def task = project.task('removeApphanceFromManifest')
        task.description = "Remove apphance-only entries from manifest"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_APPHANCE_SERVICE
        task << {
            androidConf.variants.each { variant ->
                if (androidConf.debugRelease[variant] == 'Release') {
                    removeApphanceFromManifest(project, variant)
                }
            }
        }
    }

    void prepareRestoreManifestBeforeApphance(project) {
        def task = project.task('restoreManifestBeforeApphance')
        task.description = "Restore manifest to before apphance replacement"
        task.group = AmebaCommonBuildTaskGroups.AMEBA_APPHANCE_SERVICE
        task << {
            androidConf.variants.each { variant ->
                if (androidConf.debugRelease[variant] == 'Release') {
                    restoreManifestBeforeApphanceRemoval(project, variant)
                }
            }
        }
    }

    private static String EVENT_LOG_WIDGET_PACKAGE = "com.apphance.android.eventlog.widget"
    private static String EVENT_LOG_ACTIVITY_PACKAGE = "com.apphance.android.eventlog.activity"

    private void replaceViewsWithApphance(Project project, String variant) {

        if (project[ApphanceProperty.APPHANCE_LOG_EVENTS.propertyName].equals("true")) {
            logger.lifecycle("Replacing android views with apphance loggable versions for ${variant}")
            replaceViewWithApphance(project, variant, "Button")
            replaceViewWithApphance(project, variant, "CheckBox")
            replaceViewWithApphance(project, variant, "EditText")
            replaceViewWithApphance(project, variant, "ImageButton")
            replaceViewWithApphance(project, variant, "ListView")
            replaceViewWithApphance(project, variant, "RadioGroup")
            replaceViewWithApphance(project, variant, "SeekBar")
            replaceViewWithApphance(project, variant, "Spinner")
            replaceViewWithApphance(project, variant, "TextView")

            replaceActivityWithApphance(project, variant, "Activity")
            replaceActivityWithApphance(project, variant, "ActivityGroup")
        }
    }
    private void replaceActivityWithApphance(Project project, String variant, String activityName) {
        replaceActivityExtendsWithApphance(project, variant, activityName)
    }

    private void replaceViewWithApphance(Project project, String variant, String viewName) {
        replaceViewExtendsWithApphance(project, variant, viewName);
        replaceTagResourcesOpeningTag(project, variant, viewName, EVENT_LOG_WIDGET_PACKAGE+"."+viewName);
        replaceTagResourcesClosingTag(project, variant, viewName, EVENT_LOG_WIDGET_PACKAGE+"."+viewName);
    }

    private void replaceActivityExtendsWithApphance(Project project, String variant, String activityName) {
        String newClassName = EVENT_LOG_ACTIVITY_PACKAGE+"." + activityName
        logger.info("Replacing extends with Apphance for ${activityName} to ${newClassName}")
        project.ant.replace(casesensitive: 'true', token : "extends ${activityName} ",
                        value: "extends ${newClassName} ", summary: true) {
                            fileset(dir: new File(androidConf.tmpDirs[variant], 'src')) { include (name : '**/*.java') }
                        }
    }


    private void replaceViewExtendsWithApphance(Project project, String variant, String viewName) {
        String newClassName = EVENT_LOG_WIDGET_PACKAGE+"." + viewName
        logger.info("Replacing extends with Apphance for ${viewName} to ${newClassName}")
        project.ant.replace(casesensitive: 'true', token : "extends ${viewName} ",
                        value: "extends ${newClassName} ", summary: true) {
                            fileset(dir: new File(androidConf.tmpDirs[variant], 'src')) { include (name : '**/*.java') }
                        }
    }

    private void replaceTagResourcesOpeningTag(Project project, String variant, String tagName, String replacement) {
        logger.info("Replacing tag resources with Apphance for ${tagName} to ${replacement}")
        project.ant.replace(casesensitive: 'true', token : "<${tagName} ",
                        value: "<${replacement} ", summary: true) {
                            fileset(dir: new File(androidConf.tmpDirs[variant], 'res/layout')) { include (name : '**/*.xml') }
                        }
        project.ant.replaceregexp(flags : 'gm') {
            regexp (pattern:"<${tagName}(\\s*)")
            substitution (expression:"<${replacement}\\1")
            fileset(dir: new File(androidConf.tmpDirs[variant], 'res/layout')) { include (name : '**/*.xml') }
        }
        project.ant.replace(casesensitive: 'true', token : "<${tagName}>",
                        value: "<${replacement}>", summary: true) {
                            fileset(dir: new File(androidConf.tmpDirs[variant], 'res/layout')) { include (name : '**/*.xml') }
                        }
    }

    private void replaceTagResourcesClosingTag(Project project, String variant, String tagName, String replacement) {
        logger.info("Replacing tag resources with Apphance for ${tagName} to ${replacement}")
        project.ant.replace(casesensitive: 'true', token : "</${tagName} ",
                        value: "</${replacement} ", summary: true) {
                            fileset(dir: new File(androidConf.tmpDirs[variant], 'res/layout')) { include (name : '**/*.xml') }
                        }
        project.ant.replaceregexp(flags : 'gm') {
            regexp (pattern: "</${tagName}(\\s*)")
            substitution (expression:"</${replacement}\\1")
            fileset(dir: new File(androidConf.tmpDirs[variant], 'res/layout')) { include (name : '**/*.xml') }
        }
        project.ant.replace(casesensitive: 'true', token : "</${tagName}>",
                        value: "</${replacement}>", summary: true) {
                            fileset(dir: new File(androidConf.tmpDirs[variant], 'res/layout')) { include (name : '**/*.xml') }
                        }
    }


    File getMainApplicationFile(Project project, String variant) {
        File tmpDir = androidConf.tmpDirs[variant]
        String mainApplicationFileName = manifestHelper.getApplicationName(project.rootDir)
        mainApplicationFileName = mainApplicationFileName.replace('.', '/')
        mainApplicationFileName = mainApplicationFileName + '.java'
        mainApplicationFileName = 'src/' + mainApplicationFileName
        File f
        if (new File(tmpDir,mainApplicationFileName).exists()) {
            f = new File(tmpDir, mainApplicationFileName)
        } else {
            String mainActivityName = manifestHelper.getMainActivityName(project.rootDir)
            mainActivityName = mainActivityName.replace('.', '/')
            mainActivityName = mainActivityName + '.java'
            mainActivityName = 'src/' + mainActivityName
            if (!(new File(tmpDir, mainActivityName)).exists()) {
                f = null
            } else {
                f = new File(tmpDir, mainActivityName)
            }
        }
        return f
    }

    public void preprocessBuildsWithApphance(Project project) {
        use (PropertyCategory) {
			def tasksToAdd = [:]
            project.tasks.each { task ->
                if (task.name.startsWith('buildDebug')) {
                    def variant = task.name == 'buildDebug' ? 'Debug' : task.name.substring('buildDebug-'.length())
                    task.doFirst {
                        if (!checkIfApphancePresent(project, variant)) {
                            logger.lifecycle("Apphance not found in project")
                            File mainFile = getMainApplicationFile(project, variant)
                            if (mainFile != null) {
                                replaceLogsWithApphance(project, variant)
                                replaceViewsWithApphance(project, variant)
                                addApphanceInit(project, variant, mainFile)
                                copyApphanceJar(project, variant)
                                addApphanceToManifest(project, variant)
                            }
                        } else {
                            logger.lifecycle("Apphance found in project")
                        }
                    }
					tasksToAdd.put(task.name, variant)
					logger.lifecycle("Adding upload task for variant " + variant)
                }
                if (task.name.startsWith('buildRelease')) {
                    def variant = task.name == 'buildRelease' ? 'Release' : task.name.substring('buildRelease-'.length())
                    task.doFirst {
                        removeApphanceFromManifest(project, variant)
                        replaceLogsWithAndroid(project, variant)
                    }
                    task.doLast { restoreManifestBeforeApphanceRemoval(project, variant) }
                }
            }
			tasksToAdd.each { key, value ->
				prepareSingleBuildUpload(project, value, project."${key}")
			}
        }
    }

    private replaceLogsWithAndroid(Project project, String variant) {
        logger.lifecycle("Replacing apphance logs with android for ${variant}")
        project.ant.replace(casesensitive: 'true', token : 'import com.apphance.android.Log;',
                        value: 'import android.util.Log;', summary: true) {
                            fileset(dir: new File(androidConf.tmpDirs[variant], 'src')) { include (name : '**/*.java') }
                        }
    }

    private replaceLogsWithApphance(Project project, String variant) {
        logger.lifecycle("Replacing android logs with apphance for ${variant}")
        project.ant.replace(casesensitive: 'true', token : 'import android.util.Log;',
                        value: 'import com.apphance.android.Log;', summary: true) {
                            fileset(dir: new File(androidConf.tmpDirs[variant], 'src')) { include (name : '**/*.java') }
                        }
    }

    private restoreManifestBeforeApphanceRemoval(Project project, variant) {
        logger.lifecycle("Restoring before apphance was removed from manifest. ")
        manifestHelper.restoreBeforeApphanceRemoval(new File(androidConf.tmpDirs[variant], 'src'))
    }

    private removeApphanceFromManifest(Project project, variant) {
        logger.lifecycle("Remove apphance from manifest")
        manifestHelper.removeApphance(androidConf.tmpDirs[variant])
        File apphanceRemovedManifest = new File(conf.logDirectory,"AndroidManifest-with-apphance-removed.xml")
        logger.lifecycle("Manifest used for this build is stored in ${apphanceRemovedManifest}")
        if (apphanceRemovedManifest.exists()) {
            logger.lifecycle('removed manifest exists')
            apphanceRemovedManifest.delete()
        }
        apphanceRemovedManifest << new File(androidConf.tmpDirs[variant], "AndroidManifest.xml").text
    }

    private addApphanceToManifest(Project project, String variant) {
        logger.lifecycle("Adding apphance to manifest")
        manifestHelper.addApphanceToManifest(androidConf.tmpDirs[variant])
    }

    private def addApphanceInit(Project project, variant, File mainFile) {
        logger.lifecycle("Adding apphance init to file " + mainFile)
        def lineToModify = []
        mainFile.eachLine { line, lineNumber ->
            if (line.contains('super.onCreate')) {
                lineToModify << lineNumber
            }
        }
        boolean addOnCreateInApplication  = lineToModify.empty
        File newMainClass = new File("newMainClassFile.java")
        def mode
        if (project[ApphanceProperty.APPHANCE_MODE.propertyName].equals("QA")) {
            mode = "Apphance.Mode.QA"
        } else {
            mode = "Apphance.Mode.SILENT"
        }
        String appKey = project[ApphanceProperty.APPLICATION_KEY.propertyName]
        String startSession = "Apphance.startNewSession(this, \"${appKey}\", ${mode});"
        String onCreate = " public void onCreate() { super.onCreate(); Apphance.startNewSession(this, \"${appKey}\", ${mode}); } "


        if (project[ApphanceProperty.APPHANCE_LOG_EVENTS.propertyName].equals("true")) {
            startSession = startSession + "com.apphance.android.eventlog.EventLog.setInvertedIdMap(this);";
        }

        String importApphance = 'import com.apphance.android.Apphance;'
        boolean onCreateAdded = false
        newMainClass.withWriter { out ->
            mainFile.eachLine { line ->
                if (line.contains('super.onCreate') && !onCreateAdded) {
                    out.println(line << startSession)
                    onCreateAdded = true
                } else if (addOnCreateInApplication && line.contains('extends Application {') && !onCreateAdded) {
                    out.println(line << onCreate)
                    onCreateAdded = true
                } else if (line.contains('package')) {
                    out.println(line << importApphance)
                } else {
                    out.println(line)
                }
            }
        }
        mainFile.delete()
        mainFile << newMainClass.getText()
        newMainClass.delete()
    }

    private copyApphanceJar(Project project, variant) {
        def libsDir = new File(androidConf.tmpDirs[variant], 'libs')
        libsDir.mkdirs()
        libsDir.eachFileMatch(".*apphance.*\\.jar") {
            logger.lifecycle("Removing old apphance jar: " + it.name)
            it.delete()
        }
        File libsApphance = new File(androidConf.tmpDirs[variant], 'libs/apphance.jar')
        libsApphance.delete()
        logger.lifecycle("Copying apphance jar: to ${libsApphance}")
        URL apphanceUrl = this.class.getResource("apphance-android-library_1.5-event-log.jar")
        libsApphance << apphanceUrl.getContent()
    }

    public boolean checkIfApphancePresent(Project project, String variant) {
        boolean found = false
        File basedir = androidConf.tmpDirs[variant]
        basedir.eachFileRecurse { file ->
            if (file.name.endsWith('.java')) {
                file.eachLine {
                    if (it.contains("Apphance.startNewSession")) {
                        found = true
                    }
                }
            }
        }
        if (!found) {
            new File(basedir, './libs/').eachFileMatch(".*apphance.*\\.jar") { found = true }
        }
		if (!found) {
			found = manifestHelper.isApphanceActivityPresent(basedir)
		}
		if (!found) {
			found = manifestHelper.isApphanceInstrumentationPresent(basedir)
		}
        return found
    }

	void prepareSingleBuildUpload(Project project, String variantName, def buildTask) {

			def uploadTask = project.task("upload${variantName}")
			uploadTask.description = "Uploads .apk to Apphance server"
			uploadTask.group = AmebaCommonBuildTaskGroups.AMEBA_APPHANCE_SERVICE

			uploadTask << {
				AndroidSingleVariantBuilder androidBuilder = new AndroidSingleVariantBuilder(project, androidConf)
				AndroidArtifactBuilderInfo bi = androidBuilder.buildApkArtifactBuilderInfo(project, variantName, "Debug")
				AmebaArtifact apkArtifact = androidBuilder.prepareApkArtifact(bi)

				def host = "apphance-app.appspot.com"
				HttpHost targetHost = new HttpHost(host, 443, "https");
				DefaultHttpClient httpclient = new DefaultHttpClient()
				try {
					String username = project["apphanceUserName"]
					String pass = project["apphancePassword"]
					httpclient.getCredentialsProvider().setCredentials(
						new AuthScope(targetHost.getHostName(), targetHost.getPort()),
						new UsernamePasswordCredentials("${username}", "${pass}"))
					// Create AuthCache instance
					AuthCache authCache = new BasicAuthCache();
					// Generate BASIC scheme object and add it to the local
					// auth cache
					BasicScheme basicAuth = new BasicScheme();
					authCache.put(targetHost, basicAuth);

					// Add AuthCache to the execution context
					BasicHttpContext localcontext = new BasicHttpContext();
					localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache);
					HttpPost post = new HttpPost('/api/application.update_version')
					String apphanceKey = project[ApphanceProperty.APPLICATION_KEY.propertyName]
					def jsonBuilder = new JsonBuilder(
						[
							api_key: apphanceKey,
							version: [
								name: "1",
								number: 1
							],
							current:false,
							update_resources: ["apk"]
						]
					)
					logger.lifecycle("Sending request " + jsonBuilder.toString())
					StringEntity se = new StringEntity(jsonBuilder.toString());
					post.setEntity(se);
					post.setHeader("Accept", "application/json");
					post.setHeader("Content-type", "application/json");
					post.setHeader("Connection", "close")
					post.setHeader("Host", host)
					HttpResponse response = httpclient.execute(targetHost, post, localcontext)
					logger.lifecycle("Response status " + response.getStatusLine())
					if (response.getEntity() != null) {
						JsonSlurper slurper = new JsonSlurper()
						def resp = slurper.parseText(response.getEntity().getContent().getText())
						logger.lifecycle("Response " + resp)
//						MultipartPostMethod method = new MultipartPostMethod( resp.update_urls.apk );
//						HttpMultipart part = new HttpMultipart("aaa", "aaa")
						def boundary = "----------------------------90505c6cdd54"
						MultipartEntity reqEntity = new MultipartEntity(HttpMultipartMode.STRICT, boundary, Charset.forName("UTF-8"));
						reqEntity.addPart(new FormBodyPart("apk", new FileBody(apkArtifact.location, "application/octet-stream")))
						HttpPost uploadReq = new HttpPost(resp.update_urls.apk.replace("https://apphance-app.appspot.com", ""))
						uploadReq.setHeader("Content-type", "multipart/form-data; boundary=" + boundary)
						uploadReq.setHeader("Accept", "*/*")
						uploadReq.setHeader("Connection", "close")
						uploadReq.setHeader("Host", host)
//						uploadReq.setHeader("Expect", "100-continue")
						uploadReq.setEntity(reqEntity)
//						logger.lifecycle("Request entity " + reqEntity.getContent().getText())
						reqEntity.writeTo(System.err)
						DefaultHttpClient uploadClient = new DefaultHttpClient()
//						uploadClient.getCredentialsProvider().setCredentials(
//							new AuthScope(targetHost.getHostName(), targetHost.getPort()),
//							new UsernamePasswordCredentials("${username}", "${pass}"))
						HttpResponse uploadResponse = uploadClient.execute(targetHost, uploadReq)
						logger.lifecycle("Upload response " + EntityUtils.toString(uploadResponse.getEntity()))
					} else {
						logger.lifecycle("Query failed")
					}

				} finally {
					// When HttpClient instance is no longer needed,
					// shut down the connection manager to ensure
					// immediate deallocation of all system resources
					httpclient.getConnectionManager().shutdown();
				}
			}

			uploadTask.dependsOn(buildTask)

	}

	void uploadApk(Project project, String variant) {

	}

    static public final String DESCRIPTION =
    """This is the plugin that links Ameba with Apphance service.

The plugin provides integration with Apphance service. It performs the
following tasks: adding Apphance on-the-fly while building the application
(for all Debug builds), removing Apphance on-the-fly while building the application
(for all Release builds), submitting the application to apphance at release time.
"""
}
