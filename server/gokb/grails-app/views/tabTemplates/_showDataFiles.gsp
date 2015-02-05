
<div class="tab-pane" id="files">
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
					<g:if test="${allowEdit && f.canEdit?.value=='Yes'}">
	        	<g:link controller="ajaxSupport" action="unlinkManyToMany" params="${["__context": "${d.getClassName()}:${d.id}","__property":"fileAttachments","__itemToRemove":"${f.getClassName()}:${f.id}"]}">Delete</g:link>
	        		</g:if>
					</td>
				</tr>
			</g:each>
		</tbody>
	</table>
	<g:if test="${allowEdit}">
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
	</g:if>
</div>
