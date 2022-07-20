<g:set var="current_key" value="" />
<table class="table table-bordered">
  <thead>
    <tr><th>Property</th><th>Value</th><th>Actions</th></tr>
  </thead>
  <tbody>
    <g:each in="${d.additionalProperties.sort{it.propertyDefn?.propertyName}}" var="cp">
      <g:if test="${ current_key != cp.propertyDefn?.propertyName }" >
        <tr>
	        <g:set var="current_key" value="${cp.propertyDefn?.propertyName}" />
	        <td>${cp.propertyDefn?.propertyName}</td>
	        <td>${cp.apValue}</td>
	        <td><g:if test="${d.isEditable()}"><g:link controller="ajaxSupport" action="unlinkManyToMany" class="confirm-click" data-confirm-message="Are you sure you wish to unlink this property?" params="${ ["__property":"additionalProperties", "__context":d.getClassName() + ":" + d.id, "__itemToRemove" : cp.getClassName() + ":" + cp.id] }" >Unlink</g:link></g:if></td>
	      </tr>
	    </g:if>
      <g:else>
        <tr class="grouped" >
          <td></td>
	        <td>${cp.apValue}</td>
	        <td></td>
	      </tr>
      </g:else>
    </g:each>
    <g:if test="${d.isEditable()}">
    <tr>
      <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
        <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
        <input type="hidden" name="__newObjectClass" value="org.gokb.cred.KBComponentAdditionalProperty"/>
        <input type="hidden" name="__addToColl" value="additionalProperties"/>
        <td><g:simpleReferenceTypedown class="form-control" name="propertyDefn" baseClass="org.gokb.cred.AdditionalPropertyDefinition"/></td>
        <td><input type="text" class="form-control" name="apValue"/></td>
        <td><button type="submit" class="btn btn-default btn-primary">Add</button></td>
      </g:form>
    </tr>
    </g:if>
  </tbody>
</table>



