<%@page import="org.gokb.cred.CuratoryGroup"%>
<g:set var="cur_editable" value="${ CuratoryGroup.isTypeAdministerable(false) && d.isEditable() && ((request.curator?.size() > 0) || (params.curationOverride == "true")) }" />
<table class="table table-bordered">
  <thead>
    <tr>
      <th>Curatory Group</th>
      <g:if test="${ cur_editable }">
        <th>Actions</th>
      </g:if>
    </tr>
  </thead>
  <tbody>
  	<g:if test="${ d.curatoryGroups?.size() > 0 }" >
	    <g:each in="${d.curatoryGroups}" var="t">
		    <tr>
		      <td><g:link controller="resource" action="show" id="${t.getClassName()}:${t.id}"> ${t.name}</g:link></td>
		      <g:if test="${ cur_editable }">
		        <td><g:link controller="ajaxSupport" action="unlinkManyToMany" class="confirm-click" data-confirm-message="Are you sure you wish to unlink ${ t.name }?" params="${ ["__property":"curatoryGroups", "__context":d.getClassName() + ":" + d.id, "__itemToRemove" : t.getClassName() + ":" + t.id] }" >Unlink</g:link></td>
		      </g:if>
		    </tr>
	    </g:each>
    </g:if>
    <g:else>
    	<tr>
    		<td colspan="2">There are currently no linked Curatory Groups</td>
    	</tr>
    </g:else>
    <g:if test="${editable}">
    <tr>
    	<th colspan="2">Link a Curatory Group</th>
    </tr>
    <tr>
      <g:form controller="ajaxSupport" action="addToStdCollection" class="form-inline">
        <td colspan="2">
					<input type="hidden" name="__context" value="${d.getClassName()}:${d.id}"/>
					<input type="hidden" name="__property" value="curatoryGroups"/>
        	<div class="input-group" >
        		<g:simpleReferenceTypedown class="form-control" name="__relatedObject" baseClass="org.gokb.cred.CuratoryGroup" filter1="Current"/>
        		<div class="input-group-btn" >
        			<button type="submit" class="btn btn-default btn-sm ">Link</button>
        		</div>
        	</div>
                <g:if test="${cur_editable}">
                  <p><g:link controller="create" params="${["tmpl": "org.gokb.cred.CuratoryGroup"]}">New Curatory Group</g:link></p>
                </g:if>
        </td>
      </g:form>
    </tr>
    </g:if>
  </tbody>
</table>
