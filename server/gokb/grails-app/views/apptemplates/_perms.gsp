<dl class="dl-horizontal">
  <g:form controller="ajaxSupport" action="grant" class="form-inline">
    <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
    <dt>Perm</dt><dd><g:simpleReferenceTypedown name="permission" baseClass="org.gokb.cred.RefdataValue" filter1="KBComponentVariantName.VariantType" /></dd>
    <dt>To</dt><dd><g:simpleReferenceTypedown name="user" baseClass="org.gokb.cred.User" /></dd>
    <dt></dt><dd><button type="submit" class="btn btn-primary btn-small">Add</button></dd>
  </g:form>
</dl>

