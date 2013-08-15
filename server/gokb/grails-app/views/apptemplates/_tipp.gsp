<g:if test="${d.id != null}">
		
	<dl class="dl-horizontal">
	
	  <div class="control-group">
	    <dt>Title</dt>
	    <dd><g:link controller="resource" action="show" id="${d.title?.class.name+':'+d.title?.id}">${d.title?.name}</g:link></dd>
	  </div>
	
	  <div class="control-group">
	    <dt>Package</dt>
	    <dd><g:link controller="resource" action="show" id="${d.pkg?.class.name+':'+d.pkg?.id}">${d.pkg?.name}</g:link></dd>
	  </div>
	
	  <div class="control-group">
	    <dt>Platform</dt>
	    <dd><g:link controller="resource" action="show" id="${d.hostPlatform?.class.name+':'+d.hostPlatform?.id}">${d.hostPlatform?.name}</g:link></dd>
	  </div>
	
	  <div class="control-group">
	    <dt>Coverage</dt>
	    <dd>
	      <table class="table table-striped">
	        <thead>
	          <tr>
	            <th>Start Date</th>
	            <th>Start Volume</th>
	            <th>Start Issue</th>
	            <th>End Date</th>
	            <th>End Volume</th>
	            <th>End Issue</th>
	            <th>Embargo</th>
	          </tr>
	        </thead>
	        <tbody>
	          <tr>
	            <td>${d.startDate}</td>
	            <td>${d.startVolume}</td>
	            <td>${d.startIssue}</td>
	            <td>${d.endDate}</td>
	            <td>${d.endVolume}</td>
	            <td>${d.endIssue}</td>
	            <td>${d.embargo}</td>
	          </tr>
	        </tbody>
	      </table>
	    </dd>
	  </div>
	
	  <div class="control-group">
	    <dt>Coverage Depth</dt>
	    <dd>${d.coverageDepth}</dd>
	  </div>
	
	  <div class="control-group">
	    <dt>Coverage Note</dt>
	    <dd>${d.coverageNote}</dd>
	  </div>
	
	</dl>
	<g:render template="refdataprops" contextPath="../apptemplates" model="${[d:(d), rd:(rd), dtype:(dtype)]}"/>
</g:if>
<script language="JavaScript">
	$(document).ready(function() {

		$.fn.editable.defaults.mode = 'inline';
		$('.ipe').editable();
	});
</script>