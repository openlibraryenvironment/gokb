package org.gokb.cred

class Role {

	String authority

	static mapping = {
		cache true
		id column:'role_id'
	}

	static constraints = {
		authority blank: false, unique: true
	}
}
