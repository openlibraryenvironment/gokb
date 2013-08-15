<dl class="dl-horizontal">
	<div class="control-group">
	  <dt>Primary URL</dt>
	  <dd>
	  	<g:xEditable class="ipe" owner="${d}" field="primaryURL">${d.primaryURL}</g:xEditable>
	  </dd>
	</div>
</dl>
<g:if test="${d.id != null}">
	<g:render template="refdataprops" contextPath="../apptemplates" model="${[d:(d), rd:(rd), dtype:(dtype)]}"/>
</g:if>