includeTargets << grailsScript("_GrailsDocs")
 
includeTargets << grailsScript("_GrailsBootstrap")

target(main: "Generate the property matrix for KBComponents") {

  //make sure they all compile
  depends( configureProxy, packageApp, classpath, loadApp, configureApp )

  try {
    def application = grails.util.Holders.getGrailsApplication()

    //def component_class = ApplicationHolder.application.getClassForName("org.gokb.cred.KBComponent")
    def component_class = application.getDomainClass('org.gokb.cred.KBComponent')
    grailsConsole.addStatus "Got KBComponent class(${component_class})... list subclasses"

    // for (dc in ApplicationHolder.application.domainClasses) { 
    //    grailsConsole.addStatus dc.fullName // full name with package 
    //    grailsConsole.addStatus dc.name // simple class name 
    //    if ( dc.isAssignableFrom(org.gokb.cred.KBComponent.class) ) {
    //      grailsConsole.addStatus '** Subclass of component'
    //    }
    // } 

    def propertyNames = []

    def componentClasses = []
    componentClasses.add(component_class)
    

    component_class.getSubClasses().each { csc ->

      // grailsConsole.addStatus "Handle component subclass: ${csc.fullName}"
      componentClasses.add(csc);

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

    // Add any special additional 
    componentClasses.add(application.getDomainClass('org.gokb.cred.OrgRole'));
    componentClasses.add(application.getDomainClass('org.gokb.cred.User'));


    grailsConsole.addStatus "Calculating GoKB component matrix"
    // Combo relations matrix - 1-M props between components
    def combo_relations_matrix = new String[componentClasses.size()+1][componentClasses.size()+1]
    def x=0;
    def y=0;

    // combo_relations_matrix[from][to]
    def noArgs = [].toArray()

    componentClasses.each { cc1 ->
      combo_relations_matrix[0][x+1] = cc1.clazz.simpleName
      combo_relations_matrix[x+1][0] = cc1.clazz.simpleName
      y=0;
      componentClasses.each { cc2 ->
        def t1 = cc1.clazz.metaClass.getStaticMetaMethod('getManyByCombo', noArgs)
        def t2 = cc1.clazz.metaClass.getStaticMetaMethod('getHasByCombo', noArgs)

        // if ( t1 != null )
        //   grailsConsole.addStatus "${cc1.name} -> ${cc2.name} : test to see if ${cc1.clazz.manyByCombo.values()} contains ${cc2.name}"

        // if ( t2 != null )
        //   grailsConsole.addStatus "${cc1.name} -> ${cc2.name} : test to see if ${cc1.clazz.hasByCombo.values()} contains ${cc2.name}"

        if ( ( t1 != null ) && ( cc1.clazz.manyByCombo.values().contains(cc2.clazz) ) ) {
          // cc1 hasmany cc2
          combo_relations_matrix[x+1][y+1]='M';
        }
        else if ( ( t2 != null ) && ( cc1.clazz.manyByCombo.size() > 0) && ( cc1.clazz.hasByCombo?.values().contains(cc2.clazz) ) ) {
          combo_relations_matrix[x+1][y+1]='1';
        }
        else {
          combo_relations_matrix[x+1][y+1]='-';
        }
        // if ( cc1.hasThrough contains cc2 then it's a 1
      
        y++
      }
      x++
    }
    grailsConsole.addStatus "done Calculating GoKB component matrix"
    

    //println "wrapping file ${file.name}"

    //        echo(file: "src/docs/ref/Answers/${file.name}.gdoc", """
    //            {code}
    //                ${file.getText()}
    //            {code}
    //        """)

    x=1
    y=1
    def m2 = new String[propertyNames.size()+1][matrix.size()+1]

    // Set property names along X axis
    propertyNames.each { p ->
      m2[x++][0] = p
    }
  
    // 
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

    f.append('\n\nComponent Subclass relations\n {table}\n');
    for ( int i1=0; i1<combo_relations_matrix.length; i1++ ) {
      for ( int i2=0; i2<combo_relations_matrix.length; i2++ ) {
        if ( i2 > 0 ) {
          f.append(' | ');
        }
        f.append("* ${combo_relations_matrix[i2][i1]} *");
      }
      f.append('\n');
    }
    
    f.append('\n{table}\n');
  }
  catch ( Exception e ) {
    e.printStackTrace()
    grailsConsole.addStatus e.message
  }
  finally {
    grailsConsole.addStatus 'GokbGenerateMatrix completed'
  }
 
  depends(docs)
}

def genMatrixRow(cls,propnames) {
  def newrow = new String[propnames.size()+1]
  newrow[0]=cls.clazz.simpleName
  cls.getProperties().each { clsp ->
    def idx = propnames.indexOf(clsp.name)
    newrow[idx+1]='Y'
  }
  newrow
}

printMessage = { String message -> event('StatusUpdate', [message]) }
errorMessage = { String message -> event('StatusError', [message]) }
 
setDefaultTarget(main)
