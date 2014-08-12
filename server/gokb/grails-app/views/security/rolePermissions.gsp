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
	              <i class="fa fa-check-circle" ></i>
                <g:link class="editable open-inline" controller="security" action="revokePerm" params="${ ['id' : (d.class.name + ':' + d.id) , 'perm' : (pMap.inst.mask), 'recipient' : (role.class.name + ':' + role.id) ]}" title="Revoke permission" >
                  <i class="fa fa-minus-circle" ></i>
                </g:link>
	            </g:if>
	            <g:else>
                <g:link class="editable open-inline" controller="security" action="grantPerm" params="${ ['id' : (d.class.name + ':' + d.id) , 'perm' : (pMap.inst.mask), 'recipient' : (role.class.name + ':' + role.id) ]}" title="Grant permission" >
                  <i class="fa fa-check-circle" ></i>
                </g:link>
	              <i class="fa fa-times-circle" ></i>
	            </g:else>
						</td>
				  </g:each>
				</tr>
			</g:each>
		</table>
	</div>
</body>
</html>