package org.gokb.cred

class UpdateToken {

  String value
  Package pkg
  User updateUser

  static belongsTo = [Package, User]

  static mapping = {
    id column:'ut_id'
    value column: 'ut_value'
    pkg column:'ut_pkg_fk'
    updateUser column:'ut_update_user_fk'
  }

  static constraints = {
    value(nullable:false, blank:false);
    pkg(nullable:false, blank:false);
    updateUser(nullable:false, blank:false);
  }
}
