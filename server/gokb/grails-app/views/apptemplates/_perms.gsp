<pre>
Acl: ${acl?:'NULL'}
</pre>
<table class="table table-bordered">
  <g:each in="${acl?.entries}" var="ent">
    <tr>
      <th>Grantee</th>
      <th>Permission</th>
      <th>Mask</th>
      <th>Pattern</th>
      <th>Perm Name</th>
    </tr>
    <tr>
      <td>
        <g:if test="${ent.sid instanceof org.springframework.security.acls.domain.PrincipalSid}">
          User: ${ent.sid.principal}
        </g:if>
        <g:else>
          Group: ${ent.sid.grantedAuthority}
        </g:else>
      </td>
      <td>${ent.permission}</td>
      <td>${ent.permission.mask}</td>
      <td>${ent.permission.pattern}</td>
      <td>${grailsApplication.config.permNames[ent.permission.mask]}</td>
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
        <option value="VIEW">View</option>
      </select>
    </dd>
    <dt>To</dt><dd><g:simpleReferenceTypedown name="grantee" baseClass="org.gokb.cred.User" /></dd>
    <dt></dt><dd><button type="submit" class="btn btn-primary btn-small">Add</button></dd>
  </g:form>
  Grant Role:
  <g:form controller="ajaxSupport" action="grant" class="form-inline">
    <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
    <dd>
      <select name="perm">
        <option value="VIEW">View</option>
      </select>
    </dd>
    <dt>To</dt><dd><g:simpleReferenceTypedown name="grantee" baseClass="org.gokb.cred.Role" /></dd>
    <dt></dt><dd><button type="submit" class="btn btn-primary btn-small">Add</button></dd>
  </g:form>
</dl>

