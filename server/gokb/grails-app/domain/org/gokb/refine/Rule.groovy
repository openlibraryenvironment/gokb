package org.gokb.refine;

import org.gokb.cred.*;

public class Rule {

  String scope   // File, Provider, Global
     Org provider // For provider scope rules
  String fingerprint
  String description
  String ruleJson

  static mapping = {
             id column:'rule_id'
        version column:'rule_version'
       provider column:'rule_prov_scope_fk'
          scope column:'rule_scope'
    description column:'rule_description'
    fingerprint column:'rule_fp', index:'doc_fp_contents'
       ruleJson column:'rule_json', type:'text'
  }

  static constraints = {
    description type:'text'
    ruleJson type:'text'    
  }
}
