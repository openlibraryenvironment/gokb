<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="main" />
<r:require modules="gokbstyle" />
<title>GOKb</title>
</head>
<body class="">
	<div id="mainarea" class="container-fluid well">
		<table class="table table-bordered">
			<tr>
        <th>Status</th>
				<th>Role</th>
			</tr>
			<g:each in="${ currentRoles }" var="role, status">
				<tr>
          <td class="group-status" >
            <g:if test="${ status }" >
              <g:if test="${editable}" >
	              <g:link class="editable open-inline" controller="security" action="updateRole" params="${ ['id' : (d.class.name + ':' + d.id) ,('role' + role.id) : false ]}" title="remove role" >
	                <i class="group-member icon-ok-sign" ></i>
	              </g:link>
	            </g:if>
	            <g:else>
	              <i class="group-member icon-ok-sign" ></i>
	            </g:else>
            </g:if>
            <g:else>
              <g:if test="${editable}" >
	              <g:link class="editable open-inline" controller="security" action="updateRole" params="${ ['id' : (d.class.name + ':' + d.id) ,('role' + role.id) : true]}" title="Add to role" >
	                <i class="group-member icon-remove-sign" ></i>
	              </g:link>
              </g:if>
              <g:else>
                <i class="group-member icon-remove-sign" ></i>
              </g:else>
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