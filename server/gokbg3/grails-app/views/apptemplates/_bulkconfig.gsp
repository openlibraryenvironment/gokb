<g:if test="${d.id != null}">
	<dl class="dl-horizontal">
		<dt>
			<g:annotatedLabel owner="${d}" property="code">Code</g:annotatedLabel>
		</dt>
		<dd>
			<span> ${d.code} </span>
		</dd>
		<sec:ifAnyGranted roles="ROLE_ADMIN">
			<dt>
				<g:annotatedLabel owner="${d}" property="owner">Owner</g:annotatedLabel>
			</dt>
			<dd>
				<g:manyToOneReferenceTypedown owner="${d}" field="owner" baseClass="org.gokb.cred.User">${d.owner?.username}</g:manyToOneReferenceTypedown>
			</dd>
		</sec:ifAnyGranted>
		<dt>
			<g:annotatedLabel owner="${d}" property="url">URL</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditable class="ipe" owner="${d}" field="url" />
			<g:if test="${d.url}">
				&nbsp;<a href="${d.url}" target="new">Follow Link</a>
			</g:if>
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="frequency">Frequency</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableRefData owner="${d}" field="frequency" config='BulkImportListConfig.Frequency' />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="automatedUpdate">Automated Updates</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableBoolean owner="${d}" field="automatedUpdate" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="lastRun">Last Run</g:annotatedLabel>
		</dt>
		<dd>
			<span> ${d.lastRun} </span>
		</dd>
	</dl>
</g:if>
