<g:each var="entry" in="${rd}" >
	<g:if test="${ entry.key.startsWith(dtype + '.' ) }">
		<div class="control-group">
	    <dt>${ entry.value.title }</dt>
	    <dd><span class="ipe"
	              data-pk="${__oid}" 
	              data-type="select" 
	              data-name="${entry.value.name}"
	              data-url="<g:createLink controller='ajaxSupport' action='setRef'/>",
	              data-source="<g:createLink  controller='ajaxSupport' action='getRefdata' id='${entry.key}'  />">${d[entry.value.name]?.value?:'Not Set'}</span></dd>
	  </div>
	</g:if>
</g:each>
