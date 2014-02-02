package xj.mobile.tool

import xj.mobile.lang.*

import xj.mobile.model.properties.*
import xj.mobile.api.*

import org.ho.yaml.Yaml

import static xj.mobile.lang.Attributes.*
import static xj.mobile.lang.Language.*

/*
 * Requires: test/lang/PlatformTypeNames.yml
 *           (produced by Lang)
 * Generates: groovy classes 
 */
class ProcessType { 

  static String base = 'test/lang'

  static platformTypeNames

  static main(args) { 
	platformTypeNames = Yaml.load(new File(base + '/PlatformTypeNames.yml').text)

	platformTypeNames['iOS'] += [ 
	  'UIModalTransitionStyle' : [ 
		[ 'Button', 'transition' ], 
	  ] + Gestures.collect { g -> [ 'on' + g, 'transition' ] },

	  'UISwipeGestureRecognizerDirection' : [
		[ 'onSwipe', 'direction' ],
		[ 'onFling', 'direction' ],
	  ]
	]

	processIOSTypes(platformTypeNames['iOS'].keySet())
  }

  static simpleTypes = [ 'id', 'float', 'double', 'int', 'boolean', 'String' ]

  static specialTypes = [ 'UIColor', 'UIFont', 'CGAffineTransform',
						  'NSCalendar', 'NSDate', 'NSLocale', 'NSTimeZone', 'NSTimeInterval' ]

  static collectionTypes = [ 
	'NSArray', 'NSMutableArray', 
	'NSDictionary', 'NSMutableDictionary',
	'NSSet', 'NSMutableSet',  
  ]

  static generateTypes = [ 
	'AlertViewStyle', 
	'KeyboardType',
	'ModalTransitionStyle',
	'SwipeGestureRecognizerDirection',
  ]

  static canonicalType(type) { 
	type = IOSAPIResolver.nativeTypeMap[type] ?: type
	if (type instanceof String)
	  type = Lang.typeMap[type] ?: type
	return type
  }

  static widgetNames

  static isWidgetClass(type) { 
	if (widgetNames == null)
	  widgetNames = IOSAPIResolver.UIKitRef.keySet().collect { c -> IOSAPIResolver.isViewClass(c) }
	return type in widgetNames
  }

  static boolean isCommonTypeName(type) { 
	if (type) { 
	  type = canonicalType(type)
	  if (type instanceof String) { 
		return (type in simpleTypes ||
				type in collectionTypes ||
				isWidgetClass(type)) 
	  } else { 
		return true
	  }
	}
	return false
  }

  static findCommonPrefix(list) { 
	if (list?.size() > 1) { 
	  def prefix = ''
	  for (int i = 1; i < list[0].length(); i++) { 
		def p = list[0][0 .. i]
		if (!list.every{ it.startsWith(p) }) break
		prefix = p
	  }
	  return prefix 
	}
	return null
  }

  static existingPropertyClasses = [ 
	'AccessoryType',
	'CellStyle',
	'Color',
	'DatePickerMode',
	'Font',
	'ListStyle',
	'Property',
	'SystemButton',
  ]

  static processIOSTypes(names) { 
	//widgetNames = IOSAPIResolver.UIKitRef.keySet().collect { c -> IOSAPIResolver.isViewClass(c) }

	println '===== All Type Names =====\n' + names.join('\n')

	def nameHas = []
	names.each { n ->
	  def has = IOSAPIResolver.hasType(n)
	  if (has != null && !has.isEmpty()) { 
		//nameHas.addAll(has)
		has.each { t ->
		  nameHas.addAll(IOSAPIResolver.allSubtypes(t))
		}
	  }
	}

	println '===== Has Names =====\n' + nameHas.join('\n')

	def nameHasPropTypes = [] as Set
	nameHas.each { n -> 
	  APIResolver iosAPIResolver = APIResolver.getAPIResolver('iOS')
	  def propDefs = iosAPIResolver.findAllPropertyDefs(n)
	  if (propDefs) { 
		propDefs.each { p, pdef -> nameHasPropTypes.add(pdef.type) }
	  }
	}

	println '===== Has Names Prop Types =====\n' + nameHasPropTypes.join('\n')

	def uncommonNames = (names + nameHasPropTypes).findAll { !isCommonTypeName(it) && !(it in specialTypes) } 

	def enumDef = [:]
	uncommonNames.each { 
	  def edef = IOSAPIResolver.getEnumDef(it)
	  if (edef) { 
		enumDef[it] = edef
	  }
	}

	def enumClass = [:]
	enumDef.each { name, edef -> 
	  def cname = name[2 .. -1]
	  def prefix = findCommonPrefix(edef.defValues)
	  int n = prefix.length() 
	  def values = edef.defValues.collect { it[n..-1] }
	  enumClass[cname] = [ prefix: prefix, values : values ]
	}

	println '===== Uncommon Type Names =====\n' + uncommonNames.join('\n')
	println '===== Enum Type Names =====\n' + enumDef.keySet().join('\n')
	println '===== Other Type Names =====\n' + (uncommonNames - enumDef.keySet()).join('\n')

	println '===== Class Def ====='
	enumClass.each { name, entry ->
	  println name
	  println '--prefix : ' +  entry.prefix
	  println '--values : '
	  println entry.values.collect { '  ' + it}.join('\n')

	  if (!(name in existingPropertyClasses) &&
		  name in generateTypes) { 
		generatePropertyClass(name, entry.prefix, entry.values)
	  }
	}
	generateContextMap(names)

  }

  static generatePropertyClass(className, prefix, values, names = null) { 
	def packageName = 'xj.mobile.model.properties'
	def var = 'name'

	def file = "src/${packageName.replace('.', '/')}/${className}.groovy"
	println "Write ${file}"

	def valuesDecl = values.collect { v ->
	  "  static final ${className} ${v} = new ${className}('${v}')"
	}.join('\n')

	def valueNames = (names ?: values).collect { v -> "'${v}'" }.join(', ')

	def javaPrefix = toJavaPrefix(className)

	def code = """package ${packageName}

/*
 *  Generated by xj.mobile.tool.ProcessType
 *  Created on: ${new Date()}
 */
class ${className} extends Property { 

  static values = [:]
  static names = [ ${valueNames} ]

${valuesDecl}

  String ${var}
  
  private ${className}(${var}) { 
    this.${var} = ${var}
    values[${var}] = this
  }

  String toIOSString() { 
    \"${prefix}\${${var}}\"
  }

  String toAndroidJavaString() { 
    \"${javaPrefix}\${${var}}\"
  }

  String toShortString() { 
    ${var}
  }

  String toString() { 
    \"${className}.\${${var}}\"
  }

  static boolean isCompatible(value) { 
	(value instanceof String) || 
	(value instanceof List) 
  }

  static boolean hasValue(name) { 
    values.hasKey(name)
  }

  static ${className} getValue(name) { 
    values[name]
  }

}
"""

	new File(file).write(code)

  }
  
  static generateContextMap(names) { 
	def packageName = 'xj.mobile.model'

	def file = "src/${packageName.replace('.', '/')}/PropertyContextMap.groovy"
	println "Write ${file}"

	def context = [:]
	names.each { name -> 
	  if (name.size() > 2 && name[2 .. -1] in generateTypes) { 
		def type = name[2 .. -1]
		platformTypeNames['iOS'][name].each { c ->
		  def owner = c[0]
		  def attr = c[1]
		  if (context[owner] == null) {
			context[owner] = [:]
		  } 
		  context[owner][attr] = type
		}
	  }
	}

	def content = context.keySet().collect { owner ->
	  def list = context[owner].keySet().collect { attr -> 
		def type = context[owner][attr]
		"\t\t${attr}: '${type}'"
	  }.join(',\n')
	  "\t${owner}: [\n${list}\n\t]"
	}.join(',\n')	

	def code = """package ${packageName}

/*
 *  Generated by xj.mobile.tool.ProcessType
 *  Created on: ${new Date()}
 */
class PropertyContextMap { 

  static contextMap = [
${content}
  ]

}
"""

	new File(file).write(code)
  }

  static subs = [
	GestureRecognizer: 'Gesture'
  ]

  static String toJavaPrefix(prefix) { 
	  String result = prefix 
	if (prefix) { 
	  subs.each { k, v -> 
		result = result.replaceAll(k, v)
	  }
	}
	return result
  }

}