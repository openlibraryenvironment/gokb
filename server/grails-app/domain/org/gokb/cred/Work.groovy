package org.gokb.cred

import org.gokb.GOKbTextUtils
import groovy.util.logging.*

@Slf4j
class Work extends KBComponent {

  public List getInstances() {
    return TitleInstance.executeQuery('select t from TitleInstance as t where t.work = :work', [work:this])
  }

  def beforeUpdate() {
    super.beforeUpdate()
  }
}
