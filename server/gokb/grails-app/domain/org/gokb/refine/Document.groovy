package org.gokb.refine;

import org.gokb.cred.KBComponent;

public class Document {

//	String fingerprintStructure
	String fingerprintContents
	String description
	
	static mapping = {
						 id column:'doc_id'
					version column:'doc_version'
		   		description column:'doc_description', index:'doc_description_idx'
//	   fingerprintStructure column:'doc_fp_structure', index:'doc_fp_index'
	    fingerprintContents column:'doc_fp_contents', index:'doc_fp_contents'
	}
}
