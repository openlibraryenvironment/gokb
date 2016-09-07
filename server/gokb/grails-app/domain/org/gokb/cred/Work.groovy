package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*

@Log4j
class Work extends KBComponent {

  static mappedBy = [
    instances: 'work'
  ]

  static hasMany = [
    instances:TitleInstance
  ]

}

