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
    
    def pkg = tipp.getPkg()
  }
  
//  void testHasRelationship() {
//    tipp.has(pkg)
//    
//    def pkgs = tipp.getChildren(Package);
//    
//    System.out.println(pkgs)
//    
//    // Test appears as child.
//    boolean found = false;
//    pkgs.each {
//      found = (found ? found : (it.id == pkg.id)) 
//    }
//    assert found
//    
//    // Test appears as parent
//    System.out.println("Testing parent lookup")
//    found = false
//    def tipps = pkg.getParents(TitleInstancePackagePlatform)
//    
//    tipps.each {
//      found = (found ? found : (it.id == tipp.id))
//    }
//    assert found
//  }
//  
//  void testBelongsToRelationship() {
//    platform.belongsTo(tipp)
//    
//    def platforms = tipp.getChildren(Platform);
//    
//    System.out.println("Testing child lookup")
//    
//    // Test appears as child.
//    boolean found = false;
//    platforms.each {
//      found = (found ? found : (it.id == platform.id))
//    }
//    assert found
//    
//    // Test appears as parent
//    System.out.println("Testing parent lookup")
//    found = false
//    def tipps = platform.getParents(TitleInstancePackagePlatform)
//    
//    tipps.each {
//      found = (found ? found : (it.id == tipp.id))
//    }
//    assert found
//    
//  }
}
