<g:render template="/apptemplates/kbcomponent"
	model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />

<g:if test="${d.id != null}">
	<dl class="dl-horizontal">
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
			<g:xEditable class="ipe" owner="${d}" field="frequency" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="defaultSupplyMethod">Default Supply Method</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableRefData owner="${d}" field="defaultSupplyMethod"
				config="Source.DataSupplyMethod" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="defaultDataFormat">Default Data Format</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableRefData owner="${d}" field="defaultDataFormat"
				config="Source.DataFormat" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="responsibleParty">Responsible Party</g:annotatedLabel>
		</dt>
		<dd>
			<g:manyToOneReferenceTypedown owner="${d}" field="responsibleParty"
				baseClass="org.gokb.cred.Org">
				${d.responsibleParty?.name?:''}
			</g:manyToOneReferenceTypedown>
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="zdbMatch">Automated Updates</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableBoolean owner="${d}" field="automaticUpdates" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="targetNamespace">Title ID Namespace</g:annotatedLabel>
		</dt>
		<dd>
			<g:manyToOneReferenceTypedown owner="${d}" field="targetNamespace" baseClass="org.gokb.cred.IdentifierNamespace">${ d.targetNamespace }</g:manyToOneReferenceTypedown>
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="ezbMatch">EZB Matching Enabled</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableBoolean owner="${d}" field="ezbMatch" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="zdbMatch">ZDB Matching Enabled</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableBoolean owner="${d}" field="zdbMatch" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="lastRun">Last Run</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditable class="ipe" owner="${d}" type="date" field="lastRun" />
		</dd>
	</dl>
</g:if>
