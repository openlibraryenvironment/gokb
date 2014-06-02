<div class='no-results' >
  <g:set var="suggestion" value="${ '%' + request.getParameter("qp_name") + '%' }" />
  <p>Your search returned no results.</p>
  <p>To broaden your search, try surrounding your query with wildcards
    (e.g. <g:link params="${ request.getParameterMap() + ['qp_name' : suggestion] }" >${ suggestion }</g:link>).
  </p>
</div>