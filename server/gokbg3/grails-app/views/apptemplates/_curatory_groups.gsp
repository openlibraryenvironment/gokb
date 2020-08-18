<%@page import="org.gokb.cred.CuratoryGroup"%>
<g:set var="cur_editable" value="${ CuratoryGroup.isTypeAdministerable(false) || ( ( d.isEditable() && d.class.name != 'org.gokb.cred.User' ) && ((d.curatoryGroups?.size() == 0) || (request.curator?.size() > 0) || (params.curationOverride == "true"))) }" />
<g:set var="editable" value="${d.isAdministerable() || d == user || (d.isEditable() && ((d.curatoryGroups ? (request.curator != null && request.curator.size() > 0) : true) || (params.curationOverride == 'true')) ) }" />
<table class="table table-bordered" style="max-width:100%">
  <thead>
    <tr>
      <th>Curatory Group</th>
      <g:if test="${cur_editable && editable}">
        <th>Actions</th>
      </g:if>
    </tr>
  </thead>
  <tbody>
    <g:if test="${ d.curatoryGroups?.size() > 0 }" >
      <g:each in="${d.curatoryGroups}" var="t">
        <tr>
          <td><g:link controller="resource" action="show" id="${t.getClassName()}:${t.id}"> ${t.name}</g:link></td>
          <g:if test="${cur_editable && editable}">
            <td>
                <g:link controller="ajaxSupport" action="unlinkManyToMany" class="confirm-click" data-confirm-message="Are you sure you wish to unlink ${ t.name }?" params="${ ["__property":"curatoryGroups", "__context":d.getClassName() + ":" + d.id, "__itemToRemove" : t.getClassName() + ":" + t.id, "propagate": "true"] }" >Unlink</g:link>
            </td>
          </g:if>
        </tr>
      </g:each>
    </g:if>
    <g:else>
    	<tr>
    		<td colspan="2">There are currently no linked Curatory Groups</td>
    	</tr>
    </g:else>
    <g:if test="${cur_editable && editable}">
      <tr>
          <th colspan="2">Link a Curatory Group</th>
      </tr>
      <tr>
        <g:form controller="ajaxSupport" action="addToStdCollection" class="form-inline">
          <td colspan="2">
            <input type="hidden" name="__context" value="${d.getClassName()}:${d.id}"/>
            <input type="hidden" name="__property" value="curatoryGroups"/>
              <div class="input-group" style="width:100%;">
                <g:simpleReferenceTypedown class="form-control" name="__relatedObject" baseClass="org.gokb.cred.CuratoryGroup" filter1="Current"/>
                <span class="input-group-btn" style="padding: 0px 10px;vertical-align:top;">
                  <button type="submit" class="btn btn-default">Link</button>
                </span>
              </div>
              <p>
                <g:if test="${CuratoryGroup.isTypeCreatable(false)}">
                  <g:link controller="create" params="${["tmpl": "org.gokb.cred.CuratoryGroup"]}">New Curatory Group</g:link>
                </g:if>
              </p>
          </td>
        </g:form>
      </tr>
    </g:if>
  </tbody>
</table>
