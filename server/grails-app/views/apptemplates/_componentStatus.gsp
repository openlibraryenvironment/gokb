
<dl class="dl-horizontal">
	<dt><g:annotatedLabel owner="${d}" property="dateCreated">Date Created</g:annotatedLabel></dt>
	<dd>
	  ${d?.dateCreated?:''}
	</dd>
	<dt><g:annotatedLabel owner="${d}" property="lastUpdated">Last Updated</g:annotatedLabel></dt>
	<dd>
	  ${d?.lastUpdated?:''}
	</dd>
	<sec:ifAnyGranted roles="ROLE_SUPERUSER">
		<dt><g:annotatedLabel owner="${d}" property="lastUpdatedBy">Last updated by</g:annotatedLabel></dt>
		<dd>
			${d?.lastUpdatedBy ? d?.lastUpdatedBy.displayName?: d?.lastUpdatedBy.username : ''}
		</dd>
	</sec:ifAnyGranted>
	<dt><g:annotatedLabel owner="${d}" property="uuid">UUID</g:annotatedLabel></dt>
	<dd>
          ${d?.uuid?:''}
	</dd>
</dl>
