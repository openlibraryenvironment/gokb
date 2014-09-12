<div class='no-results' >
  <p>Your search returned no results.</p>
  <g:if test="${ params.qp_name }" >
  	<g:set var="suggestion" value="%${params.qp_name}%" />
	  <p>To broaden your search, try surrounding your query with wildcards
	    (e.g. <g:link params="${ request.getParameterMap() + ['qp_name' : suggestion] }" >${ suggestion }</g:link>).
	  </p>
  </g:if>
  <g:else>
  	<p>You have not provided any search criteria.</p>
  </g:else>
</div>
