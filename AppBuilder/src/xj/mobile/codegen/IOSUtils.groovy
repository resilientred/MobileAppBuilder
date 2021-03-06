package xj.mobile.codegen

class IOSUtils { 

  //
  // using literals 
  //

  // convert list of strings to NSArray  
  static String toNSArrayWithStrings(list, br = ' ', mutable = false) { 
    if (list) { 
	  String contents = '@[ ' + list.collect{ s -> ('@"' + s + '"') }.join(',' + br) + ' ]'
	  if (mutable) { 
		return "[${contents} mutableCopy]"
	  } else { 
		return contents 
	  }
	} else { 
	  if (mutable) { 
		return '[NSMutableArray array]'
	  } else { 
		return '@[]'
	  }
	}
  }

  // convert list of objects to NSArray  
  static String toNSArrayWithObjects(list, br = ' ', mutable = false) { 
    if (list) { 
	  String contents = '@[ ' + list.join(',' + br) + ' ]'
	  if (mutable) { 
		return "[${contents} mutableCopy]"
	  } else { 
		return contents 
	  }
	} else { 
	  if (mutable) { 
		return '[NSMutableArray array]'
	  } else { 
		return '@[]'
	  }
	}
  }

  // convert list of object-key to NSDictionary  
  static String toNSDictionaryWithObjects(list, br = ' ', mutable = false) { 
	if (list) { 
	  String contents = '@{ ' + list.collect{ kv -> "${kv[1]}: ${kv[0]}"}.join(',' + br) + ' }'
	  if (mutable) { 
		return "[${contents} mutableCopy]"
	  } else { 
		return contents 
	  }
	} else { 
	  if (mutable) { 
		return '[NSMutableDictionary dictionary]'
	  } else { 
		return '@{}'
	  }
	}
  }

  static String valueToCode(value) { 
	if (value != null) { 
	  switch (value) { 
	  case String: return ('@"' + value + '"')
	  case Number: return ('@' + value)
	  case List: return  ('@[ ' + value.collect{ v -> valueToCode(v) }.join(', ') + ' ]')
	  case Map: 
		def map = value.findAll{ k, v -> v != null }
		return ('@{ ' + map.collect{ k, v -> "@\"${k}\": ${valueToCode(v)}"}.join(', ') + ' }')
	  }
	} 
	return 'nil'
  }


  //
  // classic methods  
  //

  // convert list of strings to NSArray  
  static String toNSArrayWithStrings_(list, br = ' ', mutable = false) { 
	def cname = mutable ? 'NSMutableArray' : 'NSArray'
	def contents = ''
    if (list) 
	  contents = br + list.collect{ s -> ('@"' + s + '"') }.join(',' + br) + ', '
	"[${cname} arrayWithObjects:${contents}nil]"
  }

  // convert list of objects to NSArray  
  static String toNSArrayWithObjects_(list, br = ' ', mutable = false) { 
	def cname = mutable ? 'NSMutableArray' : 'NSArray'
	def contents = ''
    if (list) 
	  contents = br + list.join(',' + br) + ', '
	"[${cname} arrayWithObjects:${contents}nil]"
  }

  // convert list of object-key to NSDictionary  
  static String toNSDictionaryWithObjects_(list, br = ' ', mutable = false) { 
	def cname = mutable ? 'NSMutableDictionary' : 'NSDictionary'
	def contents = ''
    if (list) 
	  contents = br + list.collect{ kv -> "${kv[0]}, ${kv[1]}"}.join(',' + br) + ', '
	"[${cname} dictionaryWithObjectsAndKeys:${contents}nil]"
  }


}