package org.gokb

import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication


@Transactional
class RefineService {
  static scope = "singleton"
  
  GrailsApplication grailsApplication
  
  private FilenameFilter filter = new FilenameFilter() {
    public boolean accept(File dir, String name) {
      return name ==~ (zip_regex + "\\.\\Qzip\\E")
    }
  };
  
  private static final String zip_regex = "\\Qgokb-release-\\E(\\d)\\.(\\d)\\.(\\d|\\d\\.\\d)" 
  
  private final String refine_folder = new File (grailsApplication.mainContext.getResource('WEB-INF').file, "refine").absolutePath
  
  String getCurrentLocalExtension () {
    
    // Open the webapp_dir
    File folder = new File(refine_folder)
    
    // Ensure that the folder is a directory.
    if (folder.isDirectory()) {
      
      // Try and get the file.
      def files = folder.list(filter)
      if (files.length == 1) {
        return files[0]
      }
    }
    
    
  }

  def checkUpdate (String current_version) {
    
    // Default to no update.
    boolean update = false
    
    // Test version number format.
    def matcher = current_version =~ "\\Qgokb-release-\\E(\\d)\\.(\\d)\\.(\\d|\\d\\.\\d)"
    
    if (matcher) {
      // Format match. We now need to test each value in turn.
    }
  }
}
