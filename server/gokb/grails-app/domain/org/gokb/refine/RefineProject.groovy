package org.gokb.refine

class RefineProject {

	String 	name
	String 	description
	Date	modified
	String 	file
	Boolean	checkedIn
	String 	checkedOutBy
	Long 	localProjectID
	String 	hash
	
	static constraints = {
		hash 			nullable: true
		checkedOutBy 	nullable: true
		description 	nullable: true
		localProjectID 	nullable: true
	}
}