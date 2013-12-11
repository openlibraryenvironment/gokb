<r:require modules="gokbstyle" />
<r:require modules="editable" />

<h3>
	${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}
</h3>

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
			<g:annotatedLabel owner="${d}" property="reference">Reference</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditable class="ipe" owner="${d}" field="reference" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="shortCode">Short Code</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditable class="ipe" owner="${d}" field="shortcode" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel>
		</dt>
		<dd>
			<g:manyToOneReferenceTypedown owner="${d}" field="source" baseClass="org.gokb.cred.Source">${d.source?.name}</g:manyToOneReferenceTypedown>
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
					<g:annotatedLabel owner="${d}" property="description">Description</g:annotatedLabel>
				</dt>
				<dd>
					<g:xEditable class="ipe" owner="${d}" field="description">
						${d.description}
					</g:xEditable>
				</dd>

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

				<dt>
					<g:annotatedLabel owner="${d}" property="localProjectId">Local Project ID</g:annotatedLabel>
				</dt>
				<dd>
					${d.localProjectID}&nbsp;
				</dd>

				<dt>
					<g:annotatedLabel owner="${d}" property="progress">Progress</g:annotatedLabel>
				</dt>
				<dd>
					${d.progress}&nbsp;
				</dd>

				<g:if test="${d.id != null}">
					<dt>
						<g:annotatedLabel owner="${d}" property="provider">Provider</g:annotatedLabel>
					</dt>
					<dd>
						${d.provider?.name ?: 'Not yet set'}
					</dd>

					<dt>
						<g:annotatedLabel owner="${d}" property="projectStatus">Project Status</g:annotatedLabel>
					</dt>
					<dd>
						${ d.projectStatus?.getName() }
						&nbsp;
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

<script type="text/javascript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
