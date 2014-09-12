<table class="table table-bordered">
  <thead>
    <tr><th>Territory</th><th>Actions</th></tr>
  </thead>
  <tbody>
  	<g:if test="${ d.territories?.size() > 0 }" >
	    <g:each in="${d.territories}" var="t">
		    <tr>
		      <td><g:link controller="resource" action="show" id="${t.getClassName()}:${t.id}"> ${t.name}</g:link></td>
		      <td><g:link controller="ajaxSupport" action="unlinkManyToMany" class="confirm-click" data-confirm-message="Are you sure you wish to unlink ${ t.name }?" params="${ ["__property":"territories", "__context":d.getClassName() + ":" + d.id, "__itemToRemove" : t.getClassName() + ":" + t.id] }" >Unlink</g:link></td>
		    </tr>
	    </g:each>
    </g:if>
    <g:else>
    	<tr>
    		<td colspan="2">There are currently no linked territories</td>
    	</tr>
    </g:else>
    <g:if test="${d.isEditable()}">
    <tr>
    	<th colspan="2">Link a Territory</th>
    </tr>
    <tr>
      <g:form controller="ajaxSupport" action="addToStdCollection" class="form-inline">
        <td colspan="2">
					<input type="hidden" name="__context" value="${d.getClassName()}:${d.id}"/>
					<input type="hidden" name="__property" value="territories"/>
        	<div class="input-group" >
        		<g:simpleReferenceTypedown class="form-control" name="__relatedObject" baseClass="org.gokb.cred.Territory" />
        		<div class="input-group-btn" >
        			<button type="submit" class="btn btn-default btn-sm ">Link</button>
        		</div>
        	</div>
        	<p><g:link controller="create" params="${["tmpl": "org.gokb.cred.Territory"]}">New Territory</g:link></p>
        </td>
      </g:form>
    </tr>
    </g:if>
  </tbody>
</table>