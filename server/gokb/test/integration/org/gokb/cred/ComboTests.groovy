package org.gokb.cred

import static org.junit.Assert.*
import org.junit.*

class ComboTests extends GroovyTestCase {

  Package pkg;
  TitleInstancePackagePlatform tipp;
  Platform platform

  void setUp() {
    // Setup logic here.
    pkg = Package.first()
    tipp = TitleInstancePackagePlatform.first()
    platform = Platform.first()
  }

  void tearDown() {
    // Tear down logic here
  }
  
  void testDynamicMethods() {
    
    pkg.setTipps([tipp])
    
    def tipps = pkg.getTipps()
    
    assert tipps[0] == tipp
  }
  
  void testDynamicProperties() {
    
    pkg.tipps = [tipp]
    
    def tipps = pkg.tipps
    
    assert tipps[0] == tipp
  }
  
  void testReverseLookups() {
    def pkgProp = tipp.pkg
    def pkgMeth = tipp.getPkg()
    
    assert pkgProp == pkg
    assert pkgMeth == pkg
  }
  
  void testExceptions() {
    System.out.println(shouldFail(MissingPropertyException) {
      def tpkg = tipp.pkgNotFoundOnHere
    })
    
    System.out.println(shouldFail(MissingMethodException) {
      def tpkg = tipp.getPkgNotFoundOnHere()
    })
  }
}
