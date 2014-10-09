package org.gokb

import grails.transaction.Transactional


@Transactional
class RefineService {
  static scope = "singleton"

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
