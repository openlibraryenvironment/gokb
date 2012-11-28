package org.gokb.refine

import java.io.Serializable
import java.util.Map

import org.gokb.cred.KBComponent

class RefineProject {

	String 	name
	String 	description
	Date	modified
	String 	file
	Boolean	checkedIn
	String 	checkedOutBy
	Long localProjectID
}