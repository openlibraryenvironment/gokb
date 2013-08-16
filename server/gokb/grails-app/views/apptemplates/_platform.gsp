<dl class="dl-horizontal">
	<div class="control-group">
	  <dt>Primary URL</dt>
	  <dd>
	  	<g:xEditable class="ipe" owner="${d}" field="primaryUrl">${d.primaryUrl}</g:xEditable>
	  </dd>
	</div>
	<g:if test="${d.id != null}">
		<g:render template="refdataprops" contextPath="../apptemplates" model="${[d:(d), rd:(rd), dtype:(dtype)]}"/>
	</g:if>
</dl>

<script language="JavaScript">
	$(document).ready(function() {

		$.fn.editable.defaults.mode = 'inline';
		$('.ipe').editable();
	});
</script>