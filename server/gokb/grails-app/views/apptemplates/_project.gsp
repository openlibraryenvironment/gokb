<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h3>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h3>

<div id="content">

  <dl class="dl-horizontal">

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="name"/></dd>
    </div>

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel></dt>
      <dd><g:xEditableRefData owner="${d}" field="status" config="KBComponent.Status" /></dd>
    </div>

    <!--
    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="id">Internal ID</g:annotatedLabel></dt>
      <dd>${d.id}</dd>
    </div>
    -->

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="reference">Reference</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="reference"/></dd>
    </div>

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="shortCode">Short Code</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="shortcode"/></dd>
    </div>

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel></dt>
      <dd><g:manyToOneReferenceTypedown owner="${d}" field="source" baseClass="org.gokb.cred.Source"/></dd>
    </div>

  </dl>

  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#projdetails" data-toggle="tab">Project Details</a></li>
  </ul>

  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="projdetails">
      <dl class="dl-horizontal">
  
    
        <div class="control-group">
          <dt><g:annotatedLabel owner="${d}" property="description">Description</g:annotatedLabel></dt>
          <dd><g:xEditable class="ipe" owner="${d}" field="description">${d.description}</g:xEditable></dd>
        </div>
      
        <div class="control-group">
          <dt><g:annotatedLabel owner="${d}" property="createdBy">Created By</g:annotatedLabel></dt>
          <dd>${d.createdBy?.displayName}&nbsp;</dd>
        </div>
    
        <div class="control-group">
          <dt><g:annotatedLabel owner="${d}" property="checkedOutBy">Checked Out By</g:annotatedLabel></dt>
          <dd>${d.lastCheckedOutBy?.displayName}&nbsp;</dd>
        </div>
    
        <div class="control-group">
          <dt><g:annotatedLabel owner="${d}" property="lastModifiedBy">Last Modified By</g:annotatedLabel></dt>
          <dd>${d.modifiedBy?.displayName}&nbsp;</dd>
        </div>
    
        <div class="control-group">
          <dt><g:annotatedLabel owner="${d}" property="localProjectId">Local Project ID</g:annotatedLabel></dt>
          <dd>${d.localProjectID}&nbsp;</dd>
        </div>
    
        <div class="control-group">
          <dt><g:annotatedLabel owner="${d}" property="progress">Progress</g:annotatedLabel></dt>
          <dd>${d.progress}&nbsp;</dd>
        </div>
    
        <g:if test="${d.id != null}">
          <div class="control-group">
            <dt><g:annotatedLabel owner="${d}" property="provider">Provider</g:annotatedLabel></dt>
            <dd>${d.provider?.name ?: 'Not yet set'}</dd>
          </div>
          <div class="control-group">
            <dt><g:annotatedLabel owner="${d}" property="projectStatus">Project Status</g:annotatedLabel></dt>
            <dd>
              ${ d.projectStatus?.getName() } &nbsp;
          </dd>
          </div>
          <g:if test="${d.lastCheckedOutBy}" >
            <div class="control-group">
              <dt><g:annotatedLabel owner="${d}" property="lastCheckedOutBy">Last Checked Out By</g:annotatedLabel></dt>
              <dd>
                <g:link url="mailto:${ d.lastCheckedOutBy.email }">${ d.lastCheckedOutBy.displayName ?: d.lastCheckedOutBy.username }</g:link> 
              </dd>
            </div>
          </g:if>
          <div class="control-group">
            <dt><g:annotatedLabel owner="${d}" property="lastValidationResult">Last validation result</g:annotatedLabel></dt>
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
    </div>
  </div>
</div>


<script language="JavaScript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
