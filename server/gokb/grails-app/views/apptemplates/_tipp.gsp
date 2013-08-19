<h1>
<g:if test="${d.id != null}">
  ${d.getNiceName()} : ${d.name ?: d.id} - 
     <g:link controller="resource" action="show" id="${d.title?.class.name+':'+d.title.id}">${d.title.name}</g:link> in 
     <g:link controller="resource" action="show" id="${d.pkg?.class.name+':'+d.pkg.id}">${d.pkg.name}</g:link> via 
     <g:link controller="resource" action="show" id="${d.hostPlatform?.class.name+':'+d.title.id}">${d.hostPlatform.name}</g:link>
</g:if>
<g:else>
  Create New ${d.getNiceName()}
</g:else>
</h1>

<div id="content">

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
      <dt>Status</dt>
      <dd><g:xEditableRefData owner="${d}" field="status" config="KBComponent.Status" /></dd>
    </div>
  
    <div class="control-group">
      <dt>Internal ID</dt>
      <dd>${d.id}</dd>
    </div>
  
    <div class="control-group">
      <dt>Reference</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="reference"/></dd>
    </div>
  
    <div class="control-group">
      <dt>Short Code</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="shortcode"/></dd>
    </div>
  
  </dl>
	
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#tippdetails" data-toggle="tab">TIPP Details</a></li>
    <li><a href="#tippcoverage" data-toggle="tab">Coverage</a></li>
    <li><a href="#tippopenaccess" data-toggle="tab">Open Access</a></li>
    <li><a href="#tipplists" data-toggle="tab">Lists</a></li>
    <li><a href="#header" data-toggle="tab">Header</a></li>
    <li><a href="#status" data-toggle="tab">Status</a></li>
  </ul>


  <div id="my-tab-content" class="tab-content">

    <div class="tab-pane" id="tippcoverage">
	<dl class="dl-horizontal">
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
	            <td><g:xEditable class="ipe" owner="${d}" type="date" field="startDate"/></td>
	            <td>${d.startVolume}</td>
	            <td>${d.startIssue}</td>
	            <td><g:xEditable class="ipe" owner="${d}" type="date" field="endDate"/></td>
	            <td>${d.endVolume}</td>
	            <td>${d.endIssue}</td>
	            <td>${d.embargo}</td>
	          </tr>
	        </tbody>
	      </table>
	    </dd>
	  </div>

	  <div class="control-group">
	    <dt>Coverage Note</dt>
	    <dd><g:xEditable class="ipe" owner="${d}" field="coverageNote"/></dd>
	  </div>
		
	</dl>
    </div>

    <div class="tab-pane" id="tippopenaccess">
	
	<dl class="dl-horizontal">
	  <div class="control-group">
	    <dt>Delayed OA ?</dt>
            <dd><g:xEditableRefData owner="${d}" field="delayedOA" config="TitleInstancePackagePlatform.DelayedOA" /></dd>
	  </div>

	  <div class="control-group">
	    <dt>Delayed OA Embargo</dt>
	    <dd><g:xEditable class="ipe" owner="${d}" field="delayedOAEmbargo"/></dd>
	  </div>

	  <div class="control-group">
	    <dt>Hybrid OA ?</dt>
            <dd><g:xEditableRefData owner="${d}" field="hybridOA" config="TitleInstancePackagePlatform.HybridOA" /></dd>
	  </div>

	  <div class="control-group">
	    <dt>Hybrid OA URL</dt>
	    <dd><g:xEditable class="ipe" owner="${d}" field="hybridOAUrl"/></dd>
	  </div>
	</dl>

    </div>

    <div class="tab-pane" id="tipplists">

    </div>

    <div class="tab-pane active" id="tippdetails">

      <g:if test="${d.id != null}">
		
	<dl class="dl-horizontal">
	
	  <div class="control-group">
	    <dt>Host Platform URL</dt>
	    <dd><g:xEditable class="ipe" owner="${d}" field="url"/></dd>
	  </div>

	  <div class="control-group">
	    <dt>Format</dt>
            <dd><g:xEditableRefData owner="${d}" field="format" config="TitleInstancePackagePlatform.Format" /></dd>
	  </div>

	  <div class="control-group">
	    <dt>Primary ?</dt>
            <dd><g:xEditableRefData owner="${d}" field="primary" config="TitleInstancePackagePlatform.Primary" /></dd>
	  </div>

	  <div class="control-group">
	    <dt>Payment Type</dt>
            <dd><g:xEditableRefData owner="${d}" field="paymentType" config="TitleInstancePackagePlatform.PaymentType" /></dd>
	  </div>

	</dl>
      </g:if>
    </div>

    <div class="tab-pane" id="header">
      <g:render template="kbcomponent" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
    </div>

 
    <div class="tab-pane" id="status">
      <g:render template="componentStatus" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
    </div>

  </div>
</div>


<script language="JavaScript">
	$(document).ready(function() {

		$.fn.editable.defaults.mode = 'inline';
		$('.ipe').editable();
	});
</script>
