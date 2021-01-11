<dl class="dl-horizontal">

  <dt><g:annotatedLabel owner="${d}" property="name">Curatory Group Name</g:annotatedLabel></dt>
  <dd><g:xEditable class="ipe" owner="${d}" field="name" /></dd>

  <g:if test="${d.id != null}">

	  <dt><g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel></dt>
	  <dd><g:xEditableRefData owner="${d}" field="status" config='KBComponent.Status' /></dd>

	  <dt><g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel></dt>
	  <dd><g:xEditableRefData owner="${d}" field="editStatus" config='KBComponent.EditStatus' /></dd>
		<sec:ifAnyGranted roles="ROLE_ADMIN">
			<dt>
				<g:annotatedLabel owner="${d}" property="owner">Owner</g:annotatedLabel>
			</dt>
			<dd>
				<g:manyToOneReferenceTypedown owner="${d}" field="owner" baseClass="org.gokb.cred.User">${d.owner?.username}</g:manyToOneReferenceTypedown>
			</dd>
		</sec:ifAnyGranted>
		<g:if test="${ user.isAdmin() || d.owner == user }">
	  	<dt><g:annotatedLabel owner="${d}" property="users">Members</g:annotatedLabel></dt>
			<dd>
				<g:if test="${ d.users }" >
					<ul>
						<g:each var="u" in="${ d.users }" >
													<sec:ifAnyGranted roles="ROLE_ADMIN">
														<li><a href="mailto:${ u.email }" ><i class="fa fa-envelope"></i>&nbsp;</a><g:link controller="resource" action="show" id="${u.getLogEntityId()}">${u.displayName ?: u.username}</g:link></li>
													</sec:ifAnyGranted>
													<sec:ifNotGranted roles="ROLE_ADMIN">
														<li>${u.displayName ?: u.username}</li>
													</sec:ifNotGranted>
						</g:each>
					</ul>
				</g:if>
				<g:else>
					<p>There are no members of this curatory group.</p>
				</g:else>
			</dd>
		</g:if>
  </g:if>
</dl>
