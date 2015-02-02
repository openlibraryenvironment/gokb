	<div id="my-tab-content" class="tab-content">
		<div class="tab-pane active" id="licsummary">
			<g:if
				test="${((d.summaryStatement != null) && (d.summaryStatement.length() > 0 ) )}">
				<h4>Summary Of License</h4>
				${d.summaryStatement}
			</g:if>
		</div>
		<div class="tab-pane" id="lists">
			<table class="table table-bordered">
				<thead>
					<tr>
						<th>Filename</th>
						<th>Filetype</th>
						<th>Size</th>
						<th>Type</th>
						<th>Action</th>
					</tr>
				</thead>
				<tbody>
					<g:each in="${d.fileAttachments}" var="f">
						<tr>
							<td><g:link controller="workflow" action="download"
									id="${f.guid}">
									${f.uploadName}
								</g:link></td>
							<td>
								${f.uploadMimeType}
							</td>
							<td>
								${f.filesize}
							</td>
							<td>
								${f.doctype}
							</td>
							<td>
			        	<g:link controller="ajaxSupport" action="unlinkManyToMany" params="${["__context": "${d.getClassName()}:${d.id}","__property":"fileAttachments","__itemToRemove":"${f.getClassName()}:${f.id}"]}">Delete</g:link>

							</td>
						</tr>
					</g:each>
				</tbody>
			</table>

			<g:form controller="ajaxSupport" action="addToStdCollection" class="form-inline">
        <td colspan="2">
					<input type="hidden" name="__context" value="${d.getClassName()}:${d.id}"/>
					<input type="hidden" name="__property" value="fileAttachments"/>
        	<div class="input-group col-xs-6" >
        <g:simpleReferenceTypedown class="form-control" name="__relatedObject" baseClass="org.gokb.cred.DataFile" />
    
			<button type="submit" class="btn btn-default btn-primary btn-sm ">Add Link</button>
        	</div>
        </td>
      </g:form>
		</div>
	</div>