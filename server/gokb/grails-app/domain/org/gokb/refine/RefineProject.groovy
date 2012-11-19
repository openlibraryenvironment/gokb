package org.gokb.refine

import java.io.Serializable
import java.util.Map

import org.gokb.cred.KBComponent

class RefineProject extends KBComponent {

	String checksum
	String description
	String file
	Map<String, Serializable> metadata = [:]
	
	static hasMany = [metadata: Serializable]
	static mapping = {
					 metadata type:'java.util.Map'
						 id column:'proj_id'
					version column:'proj_version'
		   		description column:'proj_description', index:'doc_checksum_idx'
				   checksum column:'proj_checksum', index:'proj_checksum_idx'
	}
}
