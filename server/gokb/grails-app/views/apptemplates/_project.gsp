<div id="content">

	<dl class="dl-horizontal">
		<dt>
			<g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditable class="ipe" owner="${d}" field="name" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableRefData owner="${d}" field="status"
				config="KBComponent.Status" />
		</dd>

		<dt>
			<g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel>
		</dt>
		<dd>
			<g:manyToOneReferenceTypedown owner="${d}" field="source"
				baseClass="org.gokb.cred.Source">
				${d.source?.name}
			</g:manyToOneReferenceTypedown>
		</dd>

		<dt>
			<g:annotatedLabel owner="${d}" property="accessUrl">Access URL</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditable owner="${d}" field="accessUrl" />
		</dd>

		<dt>
			<g:annotatedLabel owner="${d}" property="dataUrl">Data URL</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditable owner="${d}" field="dataUrl" />
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
	</dl>

	<ul id="tabs" class="nav nav-tabs">
		<li class="active"><a href="#projdetails" data-toggle="tab">Project
				Details</a></li>
	</ul>

	<div id="my-tab-content" class="tab-content">
		<div class="tab-pane active" id="projdetails">
			<dl class="dl-horizontal">
				<dt>
					<g:annotatedLabel owner="${d}" property="createdBy">Created By</g:annotatedLabel>
				</dt>
				<dd>
					${d.createdBy?.displayName}&nbsp;
				</dd>

				<dt>
					<g:annotatedLabel owner="${d}" property="checkedOutBy">Checked Out By</g:annotatedLabel>
				</dt>
				<dd>
					${d.lastCheckedOutBy?.displayName}&nbsp;
				</dd>

				<dt>
					<g:annotatedLabel owner="${d}" property="lastModifiedBy">Last Modified By</g:annotatedLabel>
				</dt>
				<dd>
					${d.modifiedBy?.displayName}&nbsp;
				</dd>

				<g:if test="${d.id != null}">
					<dt>
						<g:annotatedLabel owner="${d}" property="provider">Provider</g:annotatedLabel>
					</dt>
					<dd>
						${d.provider?.name ?: 'Not yet set'}
					</dd>

					<g:if test="${d.lastCheckedOutBy}">
						<dt>
							<g:annotatedLabel owner="${d}" property="lastCheckedOutBy">Last Checked Out By</g:annotatedLabel>
						</dt>
						<dd>
							<g:link url="mailto:${ d.lastCheckedOutBy.email }">
								${ d.lastCheckedOutBy.displayName ?: d.lastCheckedOutBy.username }
							</g:link>
						</dd>
					</g:if>
					<dt>
						<g:annotatedLabel owner="${d}" property="lastValidationResult">Last validation result</g:annotatedLabel>
					</dt>
					<dd>
						${d.lastValidationResult?:'Not yet validated'}
					</dd>
				</g:if>
			</dl>
		</div>
	</div>
</div>
