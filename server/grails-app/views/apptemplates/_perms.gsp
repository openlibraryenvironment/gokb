<table class="table table-bordered">
  <tr>
    <th>Grantee</th>
    <!--
    <th>Permission</th>
    <th>Mask</th>
    <th>Pattern</th>
    -->
    <th>Perm Name</th>
    <th>Action</th>
  </tr>
  <g:each in="${acl?.entries}" var="ent">
    <tr>
      <td>
        <g:if test="${ent.sid instanceof org.springframework.security.acls.domain.PrincipalSid}">
          User: ${ent.sid.principal}
        </g:if>
        <g:else>
          Group: ${ent.sid.grantedAuthority}
        </g:else>
      </td>
      <!--
      <td>${ent.permission}</td>
      <td>${ent.permission.mask}</td>
      <td>${ent.permission.pattern}</td>
      -->
      <td>${grailsApplication.config.permNames[ent.permission.mask]?.name}</td>
      <td>
        <g:if test="${ent.sid instanceof org.springframework.security.acls.domain.PrincipalSid}">
          <g:link controller="ajaxSupport" action="revoke" params="${[__context:d.class.name+':'+d.id,grantee:ent.sid.principal,perm:grailsApplication.config.permNames[ent.permission.mask].name]}">Revoke</g:link>
        </g:if>
        <g:else>
          <g:link controller="ajaxSupport" action="revoke" params="${[__context:d.class.name+':'+d.id,grantee:ent.sid.grantedAuthority,perm:grailsApplication.config.permNames[ent.permission.mask].name]}">Revoke</g:link>
        </g:else>
      </td>
    </tr>
  </g:each>
</table>


<dl class="dl-horizontal">
  Grant User Permission:
  <g:form controller="ajaxSupport" action="grant" class="form-inline">
    <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
    <dt>Perm</dt>
    <dd>
      <select name="perm">
        <option value="READ">READ</option>
        <option value="WRITE">WRITE</option>
        <option value="ADMINISTRATION">ADMINISTRATION</option>
        <option value="DELETE">DELETE</option>
        <option value="CREATE">CREATE</option>
      </select>
    </dd>
    <dt>To</dt><dd><g:simpleReferenceTypedown class="form-control" class="form-control" name="grantee" baseClass="org.gokb.cred.User" /></dd>
    <dt></dt><dd><button type="submit" class="btn btn-default btn-primary btn-sm ">Add</button></dd>
  </g:form>
  Grant Role:
  <g:form controller="ajaxSupport" action="grant" class="form-inline">
    <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
    <dd>
      <select name="perm">
        <option value="READ">READ</option>
        <option value="WRITE">WRITE</option>
        <option value="ADMINISTRATION">ADMINISTRATION</option>
        <option value="DELETE">DELETE</option>
        <option value="CREATE">CREATE</option>
      </select>
    </dd>
    <dt>To</dt><dd><g:simpleReferenceTypedown class="form-control" name="grantee" baseClass="org.gokb.cred.Role" /></dd>
    <dt></dt><dd><button type="submit" class="btn btn-default btn-primary btn-sm ">Add</button></dd>
  </g:form>
</dl>

