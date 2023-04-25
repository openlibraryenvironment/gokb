<g:render template="/apptemplates/kbcomponent"
	model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />

<g:if test="${d.id != null}">
	<dl class="dl-horizontal">
		<dt>
			<g:annotatedLabel owner="${d}" property="code">Code</g:annotatedLabel>
		</dt>
		<dd>
			<span> ${d.code} </span>
		</dd>
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
			<g:annotatedLabel owner="${d}" property="automatedUpdates">Automated Updates</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableBoolean owner="${d}" field="automatedUpdates" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="lastRun">Last Run</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditable class="ipe" owner="${d}" type="date" field="lastRun" />
		</dd>
	</dl>
</g:if>
