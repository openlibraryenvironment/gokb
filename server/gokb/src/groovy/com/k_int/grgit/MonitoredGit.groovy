package com.k_int.grgit

import groovy.lang.ExpandoMetaClass.ExpandoMetaProperty
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.util.ConfigureUtil
import org.eclipse.jgit.lib.ProgressMonitor

class MonitoredGit {
  private static final Map EXTRA_STATIC_OPERATIONS = [cloneMonitored : CloneOpMonitored]
  
  public static ProgressMonitor monitor
  
  static {
    
    // The closure to use as override.
    Closure newMethod = { name, Object[] args ->
      if (name in EXTRA_STATIC_OPERATIONS && args.size() < 2) {
        def op = EXTRA_STATIC_OPERATIONS[name].newInstance()
        def config = args.size() == 0 ? [:] : args[0]
        
        // Add the monitor.
        config['monitor'] = monitor
        ConfigureUtil.configure(op, config)
        return op.call()
      } else {
        return Grgit.invokeMethod(Grgit, name, args)
      }
    }
      
    // Override for this class and grgit.
    MonitoredGit.metaClass.static.methodMissing = newMethod
  }
}
