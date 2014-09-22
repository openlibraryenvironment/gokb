<dl class="dl-horizontal">
	<dt>
		<g:annotatedLabel owner="${d}" property="domainClassName">Domain Class Name</g:annotatedLabel>
	</dt>
	<dd>
		<g:xEditable class="ipe" owner="${d}" field="dcName" />
	</dd>
	<dt>
		<g:annotatedLabel owner="${d}" property="displayName">Display Name</g:annotatedLabel>
	</dt>
	<dd>
		<g:xEditable class="ipe" owner="${d}" field="displayName" />
	</dd>

	<dt>
		<g:annotatedLabel owner="${d}" property="dcSortOrder">Sort Order</g:annotatedLabel>
	</dt>
	<dd>
		<g:xEditable class="ipe" owner="${d}" field="dcSortOrder" />
	</dd>

	<dt>
		<g:annotatedLabel owner="${d}" property="type">Type/Category</g:annotatedLabel>
	</dt>
	<dd>
		<g:xEditableRefData owner="${d}" field="type" config='DCType' />
	</dd>
</dl>

<div id="content">
	<ul id="tabs" class="nav nav-tabs">
		<li class="active"><a href="#role-permissions" data-toggle="tab">Role Permissions</a></li>
	</ul>
	<div id="my-tab-content" class="tab-content">
		<div class="tab-pane active" id="role-permissions">
			<g:link class="display-inline" controller="security"
				action="rolePermissions"
				params="${['id': (d.class.name + ':' + d.id) ]}"></g:link>
		</div>
	</div>
</div>
