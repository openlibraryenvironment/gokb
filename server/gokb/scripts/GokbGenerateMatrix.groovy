includeTargets << grailsScript("_GrailsDocs")
 
import org.codehaus.groovy.grails.commons.ApplicationHolder

includeTargets << grailsScript("_GrailsBootstrap")

target(main: "Generate the property matrix for KBComponents") {

  //make sure they all compile
  depends( configureProxy, packageApp, classpath, loadApp, configureApp )

  try {
    def component_class = ApplicationHolder.application.getClassForName("org.gokb.cred.KBComponent")
    grailsConsole.addStatus "Got KBComponent class(${component_class})... list subclasses"

    for (dc in ApplicationHolder.application.domainClasses) { 
       grailsConsole.addStatus dc.fullName // full name with package 
       grailsConsole.addStatus dc.name // simple class name 
    } 


    
    // do something with connection
  }
  finally {
    grailsConsole.addStatus 'GokbGenerateMatrix completed'
  }
 
  depends(docs)
}

printMessage = { String message -> event('StatusUpdate', [message]) }
errorMessage = { String message -> event('StatusError', [message]) }
 
setDefaultTarget(main)
