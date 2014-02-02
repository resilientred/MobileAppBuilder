package xj.mobile.codegen

import groovy.xml.MarkupBuilder

import xj.mobile.*
import xj.mobile.model.impl.*
import xj.mobile.common.*
import xj.mobile.util.SystemUtils
import xj.mobile.util.PrettyMarkupBuilder

import static xj.mobile.util.CommonUtils.*
import static xj.mobile.android.DefaultViewProcessor.*
import static xj.translate.Logger.info 

class AndroidAppTemplate extends AppTemplate { 

  public static final  tab = '    '

  String version  // SDK version 

  def androidConfig;

  boolean isSDKAvailble = false
  boolean isVersionAvailable = false 

  String sdkHome;
  String templateDir;

  String mainActivity = null
  String target = null
  String theme = null

  // space not allowed in Android project name
  //
  AndroidAppTemplate(AppInfo appInfo) {  
    super(appInfo)
    this.androidConfig = appInfo.platformConfig
    sdkHome = Main.systemConfig.android.sdk.home
    templateDir = androidConfig.template.dir
	version = appInfo.userConfig.platform.android.version ?: '4.0'

    mainActivity = appid

	String androidCommand = SystemUtils.isRunningOnWindows() ? 'android.bat' : 'android'
	String androidPath = "${sdkHome}/tools/${androidCommand}"

	isSDKAvailble = new File(androidPath).exists()
	if (!isSDKAvailble) { 
	  info "${androidPath} does not exist"
	  ant.echo("${androidPath} does not exist.")
	  ant.echo('Android SDK must be installed and the Android SDK location must be set in conf/system.conf')
	} else { 
	  def sdkProp = SystemUtils.getAndroidAPIProp();
	  target = sdkProp?.getProperty(androidConfig.versions[version].name)

	  isVersionAvailable = (target != null) 
	  if (!isVersionAvailable) { 
		ant.echo("Android version ${version} is not supported by the currect installation of Android SDK.")
		ant.echo("Use Android SDK Manager to install version ${version} of Android SDK.")
	  }

	  theme = androidConfig.versions[version].theme
	  info "Android targe: ${target}   theme: ${theme}"
	  if (!mainActivity)
        mainActivity = androidConfig.defaults.activity ?: 'MainActivity'
	}
  }

  boolean generateCode(Project project) { 
	if (isSDKAvailble && isVersionAvailable) { 
	  generateProject()
	  def auxiliaryClasses = [] as Set
	  project.classes.each { c -> 
		if (c.needGenerateCode) { 
		  if (c.auxiliaryClasses) { 
			auxiliaryClasses += c.auxiliaryClasses 
		  }
		  generateClass(c)
		  if (c instanceof ActivityClass) { 
			generateViewLayout(c.resources)
			generateMenuResources(c.resources)
			generateDrawableResources(c.resources)
		  }
		}
	  }
	  if (auxiliaryClasses)
		generateAuxiliaryClasses(auxiliaryClasses)
	
	  generateAppManifest(project)	
	  generateResources(project)  

	  ant.echo('Android App Project Generation Complete.') 
	  return true
	} else { 
	  ant.echo('Android App Project Generation Failed.') 
	  return false
	}
  }

  void generateProject() {  
	new File('ant-log.txt').delete()
	ant.record(name: 'ant-log.txt', action: 'start')
    ant.echo('Create Android App Project')
    ant.delete(dir: projectOutputDir)

    ant.echo("appname: ${appname}")
    ant.echo("appid: ${appid}")
    ant.echo("author: ${author}")
    ant.echo("org: ${org}")
	ant.echo("Android API version: ${version}")
	ant.echo("Android target: ${target}")

    // create android project 

	String androidCommand = SystemUtils.isRunningOnWindows() ? 'android.bat' : 'android'
	String androidPath = "${sdkHome}/tools/${androidCommand}"

    String command = "${androidPath} create project --target ${target} --name ${appid} --path ${projectOutputDir} --activity ${mainActivity} --package ${packageName}"

    def proc = command.execute()                 // Call *execute* on the string
    proc.waitFor()                               // Wait for the command to finish

    // Obtain status and output
    info "Execute command: ${command}"
    info "Return code: ${ proc.exitValue()}"
    info "Std err:\n${proc.err.text}"
    info "Std out:\n${proc.in.text}"    

    ant.echo('Copy res/drawable')
    ant.copy(todir: "${projectOutputDir}",
			 overwrite: true) {
      fileset(dir: templateDir) {
		include(name:"res/drawable*/*.png")
      }
    }
	ant.record(name: 'ant-log.txt', action: 'stop')
	info new File('ant-log.txt').text
  }

  void generateViewLayout(AndroidResources res) { 
    if (res) { 
      res.viewLayouts?.each { layoutName, contents -> 
		def filename = "${projectOutputDir}/res/layout/${layoutName}.xml"
		new File(filename).write(contents)
      }
    }
  }

  void generateMenuResources(AndroidResources res) { 
    if (res.menuResources) { 
      new File("${projectOutputDir}/res/menu").mkdirs()
      res.menuResources.each { name, contents -> 
		def filename = "${projectOutputDir}/res/menu/${name}.xml"
		new File(filename).write(contents)
      }
    }
  }
  void generateDrawableResources(AndroidResources res) { 
    if (res.drawableResources) { 
      res.drawableResources.each { name, contents -> 
		def filename = "${projectOutputDir}/res/drawable-mdpi/${name}.xml"
		new File(filename).write(contents)
      }
    }
  }

  void generateClass(AndroidClass c) { 
    if (c) { 
	  c.process()

	  new File('ant-log.txt').delete()
	  ant.record(name: 'ant-log.txt', action: 'start')
      ant.echo('Create Class')
      def viewname = c.isMainView ? appid : c.name
      def outputDir = "${projectOutputDir}/src/${packageName.replaceAll('\\.', '/')}"
      info "[AndroidAppTemplate] generateViewClass ${viewname}  outputDir = ${outputDir}"

      ant.copy(todir: outputDir,
			   overwrite: true) {
		fileset(dir: templateDir) {
		  include(name: "___CLASSNAME___.java")
		}
		filtermapper { 
		  replacestring(from: '___CLASSNAME___', to: viewname)
		}
		filterset(begintoken: '___', endtoken: '___') { 
		  filter(token: 'PROJECTNAME', value: appname)
		  filter(token: 'PROJECTNAMEASIDENTIFIER', value: appid)
		  filter(token: 'PACKAGE', value: packageName)
		  filter(token: 'CLASSNAME', value: viewname)
		  filter(token: 'SUPERCLASS', value: c.superClassName)
	  
		  filter(token: 'FULLUSERNAME', value: author)
		  filter(token: 'DATE', value: dateString)
		  filter(token: 'YEAR', value: yearString)
		  filter(token: 'ORGANIZATIONNAME', value: org)
		}
		filterset(begintoken: '/**', endtoken: '**/') { 
		  filter(token: 'IMPORT', value: c.importScrap)
		  filter(token: 'DECLARATION', value: indent(c.declarationScrap, 1, tab))
		  filter(token: 'METHOD', value: indent(c.methodScrap, 1, tab))
		}
      }
      
      ant.echo('Class Done')
	  ant.record(name: 'ant-log.txt', action: 'stop')
	  info new File('ant-log.txt').text
    }

  }

  void generateAuxiliaryClasses(auxiliaryClasses) { 
	new File('ant-log.txt').delete()
	ant.record(name: 'ant-log.txt', action: 'start')
	ant.echo('Generate Auxiliary Classes')

	def outputDir = "${projectOutputDir}/src/${packageName.replaceAll('\\.', '/')}"
	ant.copy(todir: outputDir,
			 overwrite: true) {
	  fileset(dir: templateDir) {
		auxiliaryClasses.each { 
		  include(name: "${it}.java")
		} 
	  }

	  filterset(begintoken: '___', endtoken: '___') { 
		filter(token: 'PROJECTNAME', value: appname)
		filter(token: 'PROJECTNAMEASIDENTIFIER', value: appid)
		filter(token: 'PACKAGE', value: packageName)
	  
		filter(token: 'FULLUSERNAME', value: author)
		filter(token: 'DATE', value: dateString)
		filter(token: 'YEAR', value: yearString)
		filter(token: 'ORGANIZATIONNAME', value: org)
	  }

	}

	ant.echo('Auxiliary Classes Done')
	ant.record(name: 'ant-log.txt', action: 'stop')
	info new File('ant-log.txt').text
  }

  void generateAppManifest(Project project) { 
    def writer = new StringWriter()
    writer.println "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
    //def xml = new MarkupBuilder(writer)    
	def xml = new PrettyMarkupBuilder(writer)
    xml.setDoubleQuotes(true)

    xml.manifest('xmlns:android': 'http://schemas.android.com/apk/res/android',
				 'package': packageName,
				 'android:versionCode': '1',
				 'android:versionName': '1.0') { 
	  def sdkAttrs = [ 'android:minSdkVersion': androidConfig.versions[version].minSDK ]
	  if (androidConfig.versions[version].targetSDK) { 
		sdkAttrs['android:targetSdkVersion'] = androidConfig.versions[version].targetSDK
	  }
	  'uses-sdk'(sdkAttrs)

      project.classes.permissions.sum().unique().sort().each { p -> 
		'uses-permission'('android:name' : "android.permission.${p}")
      }

      application('android:label': '@string/app_name', 
				  'android:theme': "@android:style/${theme}",
				  'android:icon': '@drawable/icon') { 
		project.classes.libraries.sum().unique().sort().each { lib -> 
		  'uses-library'('android:name' : lib)
		}

		String titleRes = 'app_name'
		/*
		if (project.mainViewClass.title) { 
		  titleRes = "${project.mainViewClass.name}_title"
		}
		*/
		def activityAttr = ['android:name': "${mainActivity}",
							'android:label': "@string/${titleRes}"]
		if (project.mainViewClass.orientation) {
		  activityAttr['android:screenOrientation'] = project.mainViewClass.orientation
		} 
		if (project.mainViewClass.attributes) activityAttr += project.mainViewClass.attributes

		if (project.mainViewClass.superClassName == 'TabActivity') { 
		  activityAttr['android:theme'] = '@android:style/Theme.NoTitleBar'
		}

        activity(activityAttr) { 
		  'intent-filter'() { 
			action('android:name': 'android.intent.action.MAIN')
			category('android:name': 'android.intent.category.LAUNCHER')
		  }
		}

		project.classes.each { c -> 
		  if (c instanceof ActivityClass) { 
			def attrs = ['android:name':  ".${c.name}"]
			if (c.title) { 
			  attrs['android:label'] = "@string/${c.name}_title"
			}
			if (c.attributes) attrs += c.attributes
			if (c.needGenerateCode && !c.isMainView) { 
			  activity(attrs)
			}
		  }
		}
      }
    }

    def text = writer.toString()
    info '[AbdroidAppTemplate] Android Manifest:\n' + text

    def filename = "${projectOutputDir}/AndroidManifest.xml"
    new File(filename).write(text)
  }

  void generateResources(Project project) { 
    generateStringResources(project)
    addImageFiles(project.imageFiles)
	addSupportLib(project)

	generateAttrsResources(project)
  }

  void generateStringResources(Project project) { 
    def writer = new StringWriter()
    writer.println "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
    def xml = new MarkupBuilder(writer)    
    xml.setDoubleQuotes(true)

    xml.resources { 
      string(name: 'app_name', appname) 
	  project.classes.each { c -> 
		c.resources.stringResources.each { name, value ->
		  string(name: name, value)
		}
	  }
    }

    def text = writer.toString()
    info text

    def filename = "${projectOutputDir}/res/values/strings.xml"
    new File(filename).write(text)
  }

  void generateAttrsResources(Project project) { 
	def styleable = [:]
	project.classes.each { c -> 
	  if (c.styleableResources) { 
		styleable.putAll(c.styleableResources)
	  }
	}

	if (styleable) { 
	  def writer = new StringWriter()
	  writer.println "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
	  def xml = new MarkupBuilder(writer)    
	  xml.setDoubleQuotes(true)

	  xml.resources { 
		styleable.each{ name, values ->
		  'declare-styleable'(name : name) { 
			values.each { n, f ->
			  attr(name: n, format: f)
			}
		  }
		}
	  }

	  def text = writer.toString()
	  info text

	  def filename = "${projectOutputDir}/res/values/attrs.xml"
	  new File(filename).write(text)

	}
  }

  void addImageFiles(imageFiles) { 
    // copy image files
    if (imageFiles) { 
	  /*
      ant.copy(todir: "${projectOutputDir}/res/drawable-mdpi") {
		fileset(dir: "${Main.sourceDir}/images") {
		  imageFiles.each { f -> 
			include(name: f)
		  }
		}
      }
	  */
	  imageFiles.each { f -> 
		def fname = "${Main.sourceDir}/images/${f}"
		if (new File(fname).exists()) { 
		  ant.copy(file: fname,
				   tofile: "${projectOutputDir}/res/drawable-mdpi/${f.toLowerCase()}")
		} else { 
		  println "[Error] ${fname} does not exist."
		}
	  }

    }
  }

  static final String AndroidSupportFile = 'extras/android/support/v4/android-support-v4.jar'
  static final String AndroidSupportFile_Alt = 'extras/android/compatibility/v4/android-support-v4.jar'

  void addSupportLib(Project project) { 
	if (project.classes.any { c -> c.needSupportLibrary }) { 
	  ant.copy(file: "${sdkHome}/${AndroidSupportFile}",
			   todir: "${projectOutputDir}/libs")
	}
  }

}