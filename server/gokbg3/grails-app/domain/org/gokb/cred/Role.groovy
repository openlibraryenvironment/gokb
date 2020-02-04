package org.gokb.cred

import org.springframework.aop.aspectj.RuntimeTestWalker.ThisInstanceOfResidueTestVisitor;

class Role {

	String authority

	static mapping = {
		cache true
		id column:'role_id'
	}

	static constraints = {
		authority blank: false, unique: true
	}

  static def refdataFind(params) {
    def result = [];
    def ql = null;
    // ql = RefdataValue.findAllByValueIlikeOrDescriptionIlike("%${params.q}%","%${params.q}%",params)
    // ql = RefdataValue.findWhere("%${params.q}%","%${params.q}%",params)

    def query = "from Role as r where lower(r.authority) like ?"
    def query_params = ["%${params.q.toLowerCase()}%"]

    ql = Role.findAll(query, query_params, params)

    if ( ql ) {
      ql.each { id ->
        result.add([id:"${id.class.name}:${id.id}",text:"${id.authority}"])
      }
    }

    result
  }
  
  @Override
  public boolean equals (Object obj) {
    return obj instanceof Role && obj?.id?.equals(id)
  }

  public static final String restPath = "/roles"
  
  @Override
  public String toString () {
    return authority;
  }

}
