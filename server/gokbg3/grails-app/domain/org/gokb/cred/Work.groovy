package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*

@Slf4j
class Work extends KBComponent {


  @Transient
  getInstances() {
    TitleInstance.executeQuery('select t from TitleInstance as t where t.work = :work',[work:this]);
  }

  def beforeUpdate() {
    super.beforeUpdate()
  }
}

