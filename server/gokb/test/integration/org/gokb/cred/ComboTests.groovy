package org.gokb.cred

import static org.junit.Assert.*
import org.junit.*

class ComboTests extends GroovyTestCase {

  Package pkg;
  TitleInstancePackagePlatform tipp;
  Platform platform
  def sessionFactory

  void setUp() {
    // Setup logic here.
    super.setUp()
    sessionFactory.currentSession.flush()
    sessionFactory.currentSession.clear()
    
    pkg = Package.first()
    tipp = TitleInstancePackagePlatform.first()
    platform = Platform.first()
  }

  void tearDown() {
    sessionFactory.currentSession.flush()
    sessionFactory.currentSession.clear()
  }
  
//  void testDynamicMethods() {
//    
//    pkg.setTipps([tipp])
//    
//    def tipps = pkg.getTipps()
//    
//    assert tipps[0] == tipp
//  }
//  
//  void testMapConstructor() {
//    TitleInstancePackagePlatform nTipp = new TitleInstancePackagePlatform(
//      "pkg" : (pkg),
//      "platform" : (platform)
//    ).save()
//    
//    def tipps = pkg.getTipps()
//    assert tipps.contains(nTipp)
//    
//    tipps = pkg.tipps
//    assert tipps.contains(nTipp)
//    
//    
//  }
  
  void testDynamicProperties() {
    
    pkg.tipps = [tipp]
    
    def tipps = pkg.tipps
    
    assert tipps[0] == tipp
  }
  
//  void testReverseLookups() {
//    pkg.tipps = [tipp]
//    
//    def pkgMeth = tipp.getPkg()
//    def pkgProp = tipp.pkg
//    
//    assert pkgProp == pkg
//    assert pkgMeth == pkg
//  }
//  
//  void testExceptions() {
//    System.out.println(shouldFail(MissingPropertyException) {
//      def tpkg = tipp.pkgNotFoundOnHere
//    })
//    
//    System.out.println(shouldFail(MissingMethodException) {
//      def tpkg = tipp.getPkgNotFoundOnHere()
//    })
//  }
}
