<dl class="dl-horizontal" >
	<div class="control-group">
	  <dt>Description</dt>
	  <dd><g:xEditable class="ipe" owner="${d}" field="description">${d.description}</g:xEditable></dd>
	</div>
  
  <g:if test="${d.id != null}">
  	<div class="control-group">
		  <dt>Provider</dt>
		  <dd>${d.provider?.name ?: 'Not yet set'}</dd>
		</div>
		<div class="control-group">
		  <dt>Project Status</dt>
		  <dd>
				${ d.projectStatus.getName() }
			</dd>
		</div>
		<g:if test="${d.lastCheckedOutBy}" >
			<div class="control-group">
			  <dt>Last Checked Out By</dt>
			  <dd>
			  	<g:link url="mailto:${ d.lastCheckedOutBy.email }">${ d.lastCheckedOutBy.displayName ?: d.lastCheckedOutBy.username }</g:link> 
				</dd>
			</div>
		</g:if>
		<div class="control-group">
		  <dt>Last validation result</dt>
		  <dd>${d.lastValidationResult?:'Not yet validated'}</dd>
		</div>
	  <!--  dt>Candidate Rules</dt>
	  <dd>
	    <table class="table table table-striped">
	      <thead>
	        <tr>
	          <th>Fingerprint</th>
	          <th>Rule Scope</th>
	          <th>Description</th>
	        </tr>
	        <tr>
	          <th>Rule text</th>
	        </tr>
	      </thead>
	      <tbody>
	        <g:each in="${d.possibleRulesAsList()}" var="r">
	          <tr><td>${r.fingerprint}</td><td>${r.scope}</td><td>${r.description}</td></tr>
	          <tr><td colspan="3">${r.ruleJson}</td></tr>
	        </g:each>
	      </tbody>
	    </table>
	  </dd -->
	</g:if>
</dl>
<script language="JavaScript">
	$(document).ready(function() {

		$.fn.editable.defaults.mode = 'inline';
		$('.ipe').editable();
	});
</script>