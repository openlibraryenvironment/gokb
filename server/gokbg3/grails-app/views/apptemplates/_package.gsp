<g:set var="editable" value="${ d.isEditable() && ((d.curatoryGroups ? (request.curator != null && request.curator.size() > 0) : true) || (params.curationOverride == 'true')) }" />
  <dl class="dl-horizontal">
    <dt>
      <g:annotatedLabel owner="${d}" property="name">Package Name</g:annotatedLabel>
    </dt>
    <dd style="max-width:60%">
      ${d.name}<br/>
      <g:if test="${ editable }">(Modify name through <i>Alternate Names</i> below)</g:if><br/>
      <g:link controller="packages" action="kbart" id="${params.id}">KBart File</g:link> &nbsp;
      <g:link controller="packages" action="packageTSVExport" id="${params.id}">GOKb File</g:link>
    </dd>

    <dt>
      <g:annotatedLabel owner="${d}" property="provider">Provider</g:annotatedLabel>
    </dt>
    <dd>
      <g:manyToOneReferenceTypedown owner="${d}" field="provider" baseClass="org.gokb.cred.Org">${d.provider?.name}</g:manyToOneReferenceTypedown>
    </dd>

    <dt>
      <g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel>
    </dt>
    <dd>
      <g:manyToOneReferenceTypedown owner="${d}" field="source" baseClass="org.gokb.cred.Source">${d.source?.name}</g:manyToOneReferenceTypedown>
    </dd>
    <g:if test="${d.status}">
      <dt>
        <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
      </dt>
      <dd>
        ${d.status?.value}
      </dd>
    </g:if>

    <g:if test="${d.lastProject}">
      <dt>
        <g:annotatedLabel owner="${d}" property="lastProject">Last Project</g:annotatedLabel>
      </dt>
      <dd>
        <g:link controller="resource" action="show"
          id="${d.lastProject?.getClassName()+':'+d.lastProject?.id}">
          ${d.lastProject?.name}
        </g:link>
      </dd>
    </g:if>

    <dt>
      <g:annotatedLabel owner="${d}" property="userListVerifier">List Verifier</g:annotatedLabel>
    </dt>
    <dd>
      <g:manyToOneReferenceTypedown owner="${d}" field="userListVerifier" baseClass="org.gokb.cred.User">${d.userListVerifier?.displayName ?: ''}</g:manyToOneReferenceTypedown>
    </dd>
    <dt> <g:annotatedLabel owner="${d}" property="listVerifierDate">List Verifier Date</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" type="date" field="listVerifiedDate" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="lastUpdateComment">Update Method</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="lastUpdateComment" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel> </dt>
    <dd> <g:xEditableRefData owner="${d}" field="editStatus" config='KBComponent.EditStatus' /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="description">Description</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="description" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="descriptionURL">URL</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="descriptionURL" /> </dd>

    <dt><g:annotatedLabel owner="${d}" property="curatoryGroups">Curatory Groups</g:annotatedLabel></dt>
    <dd> <g:render template="/apptemplates/curatory_groups" model="${[d:d]}" /> </dd>
  </dl>

    <ul id="tabs" class="nav nav-tabs">
      <li class="active"><a href="#packagedetails" data-toggle="tab">Package Details</a></li>
      <li><a href="#titledetails" data-toggle="tab">Titles/TIPPs <span class="badge badge-warning"> ${ d.titles ? d.titles?.size() : '0'}/${d?.tipps?.findAll{ it.status?.value == 'Current'}?.size() ?: '0'} </span></a></li>
      <li><a href="#identifiers" data-toggle="tab">Identifiers <span class="badge badge-warning"> ${d?.ids?.size() ?: '0'} </span></a></li>
      <li><a href="#altnames" data-toggle="tab">Alternate Names 
        <span class="badge badge-warning"> ${d.variantNames?.size() ?: '0'}</span>
      </a></li>
      <li><a href="#ds" data-toggle="tab">Decision Support</a></li>
      <li><a href="#activity" data-toggle="tab">Activity</a></li>
      <li><a href="#review" data-toggle="tab">Review Requests</a></li>
      <li><a href="#pkgCosts" data-toggle="tab">Package Cost Info</a></li>
    </ul>

    <div id="my-tab-content" class="tab-content">

      <div class="tab-pane active" id="packagedetails">
        <dl class="dl-horizontal">
          <g:render template="/apptemplates/refdataprops" 
            model="${[d:(d), rd:(rd), dtype:(dtype)]}" />
          <dt>
            <g:annotatedLabel owner="${d}" property="nominalPlatform">Nominal Platform</g:annotatedLabel>
          </dt>
          <dd>
            <g:manyToOneReferenceTypedown owner="${d}" field="nominalPlatform"
              name="${comboprop}" baseClass="org.gokb.cred.Platform">
              ${d.nominalPlatform?.name ?: ''}
            </g:manyToOneReferenceTypedown>
          </dd>
        </dl>
      </div>

      <div class="tab-pane" id="titledetails">
        <g:if test="${params.controller != 'create'}">
          <g:link class="display-inline" controller="search" action="index"
            params="[qbe:'g:3tipps', qp_pkg_id:d.id, hide:['qp_pkg_id', 'qp_cp', 'qp_pkg', 'qp_pub_id', 'qp_plat', 'qp_status']]"
            id="">Titles in this package</g:link>
        </g:if>
        <g:else>
          TIPPs can be added after the creation process has been finished.
        </g:else>
        <g:if test="${ editable }">
          <div class="panel-body">
            <g:form controller="ajaxSupport" action="addToCollection"
              class="form-inline">
              <input type="hidden" name="__context" value="${d.class?.name}:${d.id}" />
              <input type="hidden" name="__newObjectClass" value="org.gokb.cred.TitleInstancePackagePlatform" />
              <input type="hidden" name="__addToColl" value="tipps" />
              <dl class="dl-horizontal">
                <dt style="margin-top:0.5em;">Title</dt>
                <dd>
                  <g:simpleReferenceTypedown class="form-control" name="title" baseClass="org.gokb.cred.TitleInstance" />
                </dd>
                <dt style="margin-top:0.5em;">Platform</dt>
                <dd>
                  <g:simpleReferenceTypedown class="form-control" name="hostPlatform" baseClass="org.gokb.cred.Platform" />
                </dd>
                <dt></dt>
                <dd>
                  <button type="submit"
                    class="btn btn-default btn-primary btn-sm ">Add</button>
                </dd>
              </dl>
            </g:form>
          </div>
        </g:if>
      </div>

     <g:render template="/tabTemplates/showVariantnames" model="${[d:displayobj, showActions:true]}" />

      <div class="tab-pane" id="identifiers">
        <g:render template="/apptemplates/combosByType"
                                model="${[d:d, property:'ids', cols:[
                  [expr:'toComponent.namespace.value', colhead:'Namespace'],
                  [expr:'toComponent.value', colhead:'ID']], cur: editable]}" />
        <g:if test="${ editable }">
          <g:render template="/apptemplates/addIdentifier" model="${[d:d, hash:'#identifiers']}"/>
        </g:if>
      </div>

      <div class="tab-pane" id="ds">
        <g:render template="/apptemplates/dstab" model="${[d:d]}" />
      </div>

      <div class="tab-pane" id="activity">
        <table class="table table-bordered">
          <thead>
            <tr>
              <th>Date</th>
              <th>Action</th>
              <th>Title</th>
            </tr>
          </thead>
          <tbody>
            <g:each in="${d?.getRecentActivity(40)}" var="h">
              <tr>
                <td>${h[1]}</td>
                <td>${h[2]}</td>
                <td>${h[0].title?.name} (<g:link controller="resource" action="show" id="${h[0].getClassName()+':'+h[0].id}">TIPP ${h[0].id}</g:link>)</td>
              </tr>
            </g:each>
          </tbody>
        </table>
      </div>
      
      <div class="tab-pane" id="review">
        <g:render template="/apptemplates/revreqtab" model="${[d:d]}" />

        <div class="connected-rr">
          <h3>Review Requests for connected Titles</h3>
          <div>
            <span style="margin-right:10px;">
              <button id="rr-only-open">Load Open Requests</button>
              <button id="rr-all">Load All Requests</button>
            </span>
            <span style="white-space:nowrap;">
              <span>TIPP status restriction: </span>
              <select id="rr-tipp-status">
                <option>None</option>
                <option>Current</option>
              </select>
            </span>
          </div>
          <div id="rr-loaded"></div>
        </div>
      </div>

      <div class="tab-pane" id="pkgCosts">
        <g:render template="/apptemplates/componentCosts" model="${[d:d]}" />
      </div>

    </div>

    <g:render template="/apptemplates/componentStatus"
      model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />

    <g:javascript>
      $(document).ready(function(){
        $("#rr-only-open").click(function(e) {
          e.preventDefault();

          var tipp_restrict = $("#rr-tipp-status").val();

          $.ajax({
            url: "/gokb/packages/connectedRRs",
            data: {id: "${d.id}", restrict: tipp_restrict},
            beforeSend: function() {
              $('#rr-loaded').empty();
              $('#rr-loaded').after('<div id="rr-loading" style="height:50px;vertical-align:middle;text-align:center;"><span>Loading list <asset:image src="img/loading.gif" /></span></div>');
            },
            complete: function() {
              $('#rr-loading').remove();
            },
            success: function(result) {
              $("#rr-loaded").html(result);
            }
          });
        });
        $("#rr-all").click(function(e) {
          e.preventDefault();

          var tipp_restrict = $("#rr-tipp-status").val();

          $.ajax({
            url: "/gokb/packages/connectedRRs",
            data: {id: "${d.id}", getAll: true, restrict: tipp_restrict},
            beforeSend: function() {
              $('#rr-loaded').empty();
              $('#rr-loaded').after('<div id="rr-loading" style="height:50px;vertical-align:middle;text-align:center;"><span>Loading list <asset:image src="img/loading.gif" /></span></div>');
            },
            complete: function() {
              $('#rr-loading').remove();
            },
            success: function(result) {
              $("#rr-loaded").html(result);
            }
          });
        });
      });
    </g:javascript>

  </div>
