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

    
	    <dt>
	      <g:annotatedLabel owner="${d}" property="sourceFile">Source file</g:annotatedLabel>
	    </dt>
	    <dd>
	      <g:if test="${ d.sourceFile }" >
	        <g:link controller="resource" action="download" id="${d.getClassName()}:${d.id}" params="${ ["prop" : "sourceFile"] }">Download the source file</g:link>
	      </g:if>
	      <g:else>
	        No source file	      
	      </g:else>
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
					${ d.createdBy?.displayName ?: d.createdBy?.username }
				</dd>
				
        <dt>
          <g:annotatedLabel owner="${d}" property="projectStatus">Project Status</g:annotatedLabel>
        </dt>
        <dd>
          ${d.projectStatus.name ?: ''}
        </dd>

        <g:if test="${ d.projectStatus == org.gokb.refine.RefineProject.Status.CHECKED_OUT }" >
					<dt>
						<g:annotatedLabel owner="${d}" property="checkedOutBy">Checked Out By</g:annotatedLabel>
					</dt>
				</g:if>
				<g:else>
				  <dt>
            <g:annotatedLabel owner="${d}" property="lastCheckedOutBy">Last Checked Out By</g:annotatedLabel>
          </dt>
				</g:else>
        <dd>
          ${ d.lastCheckedOutBy?.displayName ?: d.lastCheckedOutBy?.username }
        </dd>

				<dt>
					<g:annotatedLabel owner="${d}" property="lastModifiedBy">Last Modified By</g:annotatedLabel>
				</dt>
				<dd>
					${ d.modifiedBy?.displayName ?: d.modifiedBy?.username }
				</dd>

				<g:if test="${d.id != null}">
					<dt>
						<g:annotatedLabel owner="${d}" property="provider">Provider</g:annotatedLabel>
					</dt>
					<dd>
                                        
                                                           <g:manyToOneReferenceTypedown owner="${d}" field="provider"
                                                                baseClass="org.gokb.cred.Org">
                                                                ${d.provider?.name}
                                                        </g:manyToOneReferenceTypedown>
					</dd>

					<dd>
						${d.provider?.name ?: 'Not yet set'}
					</dd>
					<dt>
						<g:annotatedLabel owner="${d}" property="lastValidationResult">Last validation result</g:annotatedLabel>
					</dt>
					<dd>
						${d.lastValidationResult ?: 'Not yet validated'}
					</dd>
				</g:if>
			</dl>
		</div>
	</div>
</div>
