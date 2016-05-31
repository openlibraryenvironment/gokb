package org.gokb.cred

import javax.persistence.Transient

class DSCriterion {

  CuratoryGroup curator
  DSCategory owner
  String title
  String description
  String explanation

  static mapping = {
    curator column:'dscrit_curator_fk'
    owner column:'dscrit_owner_fk'
    title column:'dscrit_title'
    description column:'dscrit_desc'
    explanation column:'dscrit_expl'
  }

  static hasMany = [
    appliedCriterion : DSAppliedCriterion
  ]

  static belongsTo = [
    owner: DSCategory
  ]

  static mappedBy = [
    appliedCriterion : 'criterion'
  ]

  static constraints = {
    curator(nullable:true, blank:false)
    owner(nullable:false, blank:false)
    title(nullable:false, blank:false)
    description(nullable:true, blank:false)
    explanation(nullable:true, blank:false)
  }

 static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = DSCriterion.findAllByDescriptionIlike("${params.q}%",params)

    if ( ql ) {
      ql.each { t ->
        result.add([id:"${t.class.name}:${t.id}",text:"${t.description}"])
      }
    }

    result
  }

  @Transient
  def getDecisionSupportLines() {
    def result = null

    // N.B. for steve.. saying "if id != null" always fails - id is hibernate injected - should investigate this
    if ( getId() != null ) {
      // N.B. Long standing bug in hibernate means that dsac.appliedTo = ? throws a 'can only ref props in the driving table' exception
      // Workaround is to use the id directly
      result = DSCriterion.executeQuery('select dsac from DSAppliedCriterion as dsac where dsac.criterion.id = ?',getId());
    }
    else {
    }
    return result
  }

  @Transient
  def getNiceName() {
    return "Decision Support Criteria ${description?' : '+description:''}"
  }

}
