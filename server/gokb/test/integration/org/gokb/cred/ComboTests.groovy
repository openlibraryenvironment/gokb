package org.gokb.cred

import static org.junit.Assert.*
import org.junit.*

class ComboTests {

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
    
    tipp.setPkg(pkg)
    
    def tpkg = tipp.getPkg()
    
    assert tpkg == pkg
  }
  
  void testDynamicProperties() {
    
    tipp.pkg = pkg
    
    def tpkg = tipp.pkg
    
    assert tpkg == pkg
  }
}
