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
      // grailsConsole.addStatus "Handle component subclass: ${csc.fullName}"
      csc.getProperties().each { cscp ->
        if ( propertyNames.contains( cscp.name ) ) {
          // no action - Property name already in top row of matrix
        }
        else {
          propertyNames.add(cscp.name)
        }
      }
    }

    // grailsConsole.addStatus "Full list of properties is ${propertyNames}"

    def matrix = []  // List, 1 row per class

    matrix.add(genMatrixRow(component_class, propertyNames))
    component_class.getSubClasses().each { csc ->
      matrix.add(genMatrixRow(csc, propertyNames))
    }


    //println "wrapping file ${file.name}"

    //        echo(file: "src/docs/ref/Answers/${file.name}.gdoc", """
    //            {code}
    //                ${file.getText()}
    //            {code}
    //        """)

    def x=1
    def y=1
    def m2 = new String[propertyNames.size()+1][matrix.size()+1]

    propertyNames.each { p ->
      m2[x++][0] = p
    }
  
    matrix.each { m ->
      x=0
      m.each { p ->
        m2[x++][y] = p
      }
      y++
    }

    File f = new File("src/docs/guide/gokbMatrix.gdoc")
    f.delete()
    f.append('This is the gokb KBComponent matrix')
    f.append('{table}\n');
    m2.each { row ->
      def first=true
      row.each { v ->
        if ( first ) {
          first=false
        }
        else {
          f.append(" | ");
        }
        f.append("* ${v?:''} *");
      }
      f.append("\n");
    }

    // 
    f.append('{table}');
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
