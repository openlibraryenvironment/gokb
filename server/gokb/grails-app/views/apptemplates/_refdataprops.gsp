<g:each var="entry" in="${rd}" >
	<g:if test="${ entry.key.startsWith(dtype + '.' ) }">
		<div class="control-group">
	    <dt>${ entry.value.title }</dt>
	    <dd>
                <g:xEditableRefData owner="${d}" field="${entry.value.name}" config="${entry.key}" />
	    </dd>
	  </div>
	</g:if>
</g:each>
