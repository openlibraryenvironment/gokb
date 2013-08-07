package org.gokb

import org.gokb.cred.Org

class OrgLookupService {

  def grailsApplication
  
  public Org lookupOrg(String org_name) {
	
	// The Org
	Org org = null
	
	// Lookup the ID of the Org
	Long org_id = extractOrgIDFromName (org_name)
	if (org_id) org = Org.get(org_id)
	
	org
  }
  
  public Long extractOrgIDFromName(String org_name) {
	
	Long the_id = null
	if (org_name) {
	  def publisher_match = org_name =~ /${grailsApplication.config.validation.regex.looked_up_org}/
	  
	  if (publisher_match) {
		
		// We have a match.
		the_id = Long.parseLong( publisher_match[0][1] )
	  }
	}
	
	the_id
  }
}
