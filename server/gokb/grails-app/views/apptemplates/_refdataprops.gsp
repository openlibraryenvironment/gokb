<g:each var="entry" in="${rd}" >
	<g:if test="${ entry.key.startsWith(dtype + '.' ) }">
		<div class="control-group">
	    <dt><g:annotatedLabel owner="${d}" property="${ entry.value.title }">${ entry.value.title }</g:annotatedLabel></dt>
	    <dd>
	      <g:xEditableRefData owner="${d}" field="${entry.value.name}" config="${entry.key}" />
	    </dd>
	  </div>
	</g:if>
</g:each>
