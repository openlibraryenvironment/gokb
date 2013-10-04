<table class="table table-bordered">
  <thead>
    <tr><th>Property</th><th>Value</th><th>Actions</th></tr>
  </thead>
  <tbody>
    <g:each in="${d.additionalProperties}" var="cp">
      <tr>
        <td>${cp.propertyDefn.propertyName}</td>
        <td>${cp.apValue}</td>
        <td></td>
      </tr>
    </g:each>
    <tr>
      <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
        <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
        <input type="hidden" name="__newObjectClass" value="org.gokb.cred.KBComponentAdditionalProperty"/>
        <input type="hidden" name="__addToColl" value="additionalProperties"/>
        <td><g:simpleReferenceTypedown name="propertyDefn" baseClass="org.gokb.cred.AdditionalPropertyDefinition"/></td>
        <td><input type="text" name="apValue"/></td>
        <td><button type="submit" class="btn btn-primary btn-small">Add</button></td>
      </g:form>
    </tr>
  </tbody>
</table>



