includeTargets << grailsScript("_GrailsDocs")
 
import org.codehaus.groovy.grails.commons.ApplicationHolder

includeTargets << grailsScript("_GrailsBootstrap")

target(main: "Generate the property matrix for KBComponents") {

  //make sure they all compile
  depends( configureProxy, packageApp, classpath, loadApp, configureApp )

  try {
    //def component_class = ApplicationHolder.application.getClassForName("org.gokb.cred.KBComponent")
    def component_class = ApplicationHolder.application.getDomainClass('org.gokb.cred.KBComponent')
    grailsConsole.addStatus "Got KBComponent class(${component_class})... list subclasses"

    // for (dc in ApplicationHolder.application.domainClasses) { 
    //    grailsConsole.addStatus dc.fullName // full name with package 
    //    grailsConsole.addStatus dc.name // simple class name 
    //    if ( dc.isAssignableFrom(org.gokb.cred.KBComponent.class) ) {
    //      grailsConsole.addStatus '** Subclass of component'
    //    }
    // } 

    def propertyNames = []
    

    component_class.getSubClasses().each { csc ->
      grailsConsole.addStatus "Handle component subclass: ${csc.fullName}"
      csc.getProperties().each { cscp ->
        if ( propertyNames.contains( cscp.name ) ) {
          // no action - Property name already in top row of matrix
        }
        else {
          propertyNames.add(cscp.name)
        }
      }
    }

    grailsConsole.addStatus "Full list of properties is ${propertyNames}"

    def matrix = []  // List, 1 row per class

    matrix.add(genMatrixRow(component_class, propertyNames))
    component_class.getSubClasses().each { csc ->
      matrix.add(genMatrixRow(csc, propertyNames))
    }

    println(matrix)
  }
  catch ( Exception e ) {
    grailsConsole.addStatus e.message
  }
  finally {
    grailsConsole.addStatus 'GokbGenerateMatrix completed'
  }
 
  depends(docs)
}

def genMatrixRow(cls,propnames) {
  def newrow = new String[propnames.size()+1]
  newrow[0]=cls.fullName
  cls.getProperties().each { clsp ->
    def idx = propnames.indexOf(clsp.name)
    newrow[idx+1]='Y'
  }
  newrow
}

printMessage = { String message -> event('StatusUpdate', [message]) }
errorMessage = { String message -> event('StatusError', [message]) }
 
setDefaultTarget(main)
