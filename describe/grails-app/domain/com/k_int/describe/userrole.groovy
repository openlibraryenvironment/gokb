package com.k_int.describe

import org.apache.commons.lang.builder.HashCodeBuilder

class userrole implements Serializable {

	user user
	role role

	boolean equals(other) {
		if (!(other instanceof userrole)) {
			return false
		}

		other.user?.id == user?.id &&
			other.role?.id == role?.id
	}

	int hashCode() {
		def builder = new HashCodeBuilder()
		if (user) builder.append(user.id)
		if (role) builder.append(role.id)
		builder.toHashCode()
	}

	static userrole get(long userId, long roleId) {
		find 'from userrole where user.id=:userId and role.id=:roleId',
			[userId: userId, roleId: roleId]
	}

	static userrole create(user user, role role, boolean flush = false) {
		new userrole(user: user, role: role).save(flush: flush, insert: true)
	}

	static boolean remove(user user, role role, boolean flush = false) {
		userrole instance = userrole.findByuserAndrole(user, role)
		if (!instance) {
			return false
		}

		instance.delete(flush: flush)
		true
	}

	static void removeAll(user user) {
		executeUpdate 'DELETE FROM userrole WHERE user=:user', [user: user]
	}

	static void removeAll(role role) {
		executeUpdate 'DELETE FROM userrole WHERE role=:role', [role: role]
	}

	static mapping = {
		id composite: ['role', 'user']
		version false
	}
}
