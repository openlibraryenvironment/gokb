<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb</title>
</head>
<body class="">
	<div id="mainarea" class="container well">
		<table class="table table-bordered security-table">
			<tr>
        <th>Status</th>
				<th>Role</th>
			</tr>
			<g:each in="${ currentRoles }" var="role, status">
				<tr>
          <td class="group-status" >
            <g:if test="${ status }" >
	            <i class="group-member fa fa-check-circle" ></i>
              <g:if test="${editable}" >
                <g:link class="editable open-inline" controller="security" action="updateRole" params="${ ['id' : (d.class.name + ':' + d.id) ,('role' + role.id) : false ]}" title="Remove from role" >
                  <i class="group-member fa fa-times-circle" ></i>
                </g:link>
              </g:if>
            </g:if>
            <g:else>
              <g:if test="${editable}" >
                <g:link class="editable open-inline" controller="security" action="updateRole" params="${ ['id' : (d.class.name + ':' + d.id) ,('role' + role.id) : true ]}" title="Add to role" >
                  <i class="group-member fa fa-check-circle" ></i>
                </g:link>
              </g:if>
              <i class="group-member fa fa-times-circle" ></i>
            </g:else>
          </td>
					<td>
						${ role.authority }
					</td>
				</tr>
			</g:each>
		</table>
	</div>
</body>
</html>