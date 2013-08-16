<dl class="dl-horizontal">
	<g:if test="${d.id != null}">
		<div class="control-group">
			<dt>Provider</dt>
			<dd>
				${d.provider?.name?:'Provider Not Set'}
			</dd>
		</div>
		
		<g:if test="${d.lastProject}">
			<div class="control-group">
				<dt>Last Project</dt>
				<dd>
					<g:link controller="resource" action="show"
						id="${d.lastProject?.getClassName()+':'+d.lastProject?.id}">${d.lastProject?.name}</g:link>
				</dd>
			</div>
		</g:if>
		
		<g:render template="refdataprops" contextPath="../apptemplates" model="${[d:(d), rd:(rd), dtype:(dtype)]}"/>

		<table class="table table-bordered table-striped" style="clear: both">
			<tbody>
				<tr>
					<td><g:link controller="search" action="index"
							params="[qbe:'g:tipps', qp_pkg_id:d.id]" id="">Titles in this package</g:link></td>
				</tr>
			</tbody>
		</table>
	</g:if>
</dl>

<script language="JavaScript">
	$(document).ready(function() {

		$.fn.editable.defaults.mode = 'inline';
		$('.ipe').editable();
	});
</script>
