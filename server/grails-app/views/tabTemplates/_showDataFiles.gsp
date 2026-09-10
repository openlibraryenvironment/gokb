
<div class="tab-pane" id="files">
	<table class="table table-bordered">
		<thead>
			<tr>
				<th>Filename</th>
				<th>Filetype</th>
				<th>Size</th>
				<th>Type</th>
				<th>Link Date</th>
			</tr>
		</thead>
		<tbody>
			<g:each in="${d.fileAttachments}" var="f">
				<tr>
					<td><g:link controller="workflow" action="download"
							id="${f.file.guid}">
							${f.file.uploadName}
						</g:link></td>
					<td>
						${f.file.uploadMimeType}
					</td>
					<td>
						${f.file.filesize}
					</td>
					<td>
						${f.file.doctype}
					</td>
					<td>
						${f.dateCreated}
					</td>
				</tr>
			</g:each>
		</tbody>
	</table>
</div>
