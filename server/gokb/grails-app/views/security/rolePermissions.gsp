<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="main" />
<r:require modules="gokbstyle" />
<title>GOKb</title>
</head>
<body class="">
	<div id="mainarea" class="container-fluid well">
		<table class="table table-bordered security-table">
			<tr>
			  <th></th>
			  <g:each in="${perms}" var="mask,pMap">
			    <th>${ pMap.name }</th>
			  </g:each>
			</tr>
			<g:each in="${ roles }" var="role" >
			  <tr>
			    <th>${ role }</th>
				  <g:each in="${ perms }" var="mask, pMap">
						<td>
						  <g:if test="${ groupPerms.get(role.authority)?.get(pMap.inst.mask) }" >
						    <i class="icon-ok-sign" ></i>
						  </g:if>
						  <g:else>
                <i class="icon-remove-sign" ></i>
						  </g:else>
						</td>
				  </g:each>
				</tr>
			</g:each>
		</table>
	</div>
</body>
</html>