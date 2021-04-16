<g:set var="editable" value="${ d.isEditable() && ((d.curatoryGroups ? (request.curator != null && request.curator.size() > 0) : true) || (params.curationOverride == 'true' && request.user.isAdmin())) }" />
  <dl class="dl-horizontal">
  <dt>
    <g:annotatedLabel owner="${d}" property="name">Package Name</g:annotatedLabel>
  </dt>
  <dd style="max-width:60%">
    <g:if test="${displayobj?.id != null}">
      <div>
        ${d.name}<br/>
        <span style="white-space:nowrap;">(Modify title through <i>Alternate Names</i> below)</span>
      </div>
      <g:link controller="packages" action="kbart" id="${params.id}">KBart File</g:link>&nbsp;
      (<g:link controller="packages" action="kbart" id="${params.id}" params="[exportType:'title']">Title flavour</g:link>),&nbsp;
        <g:link controller="packages" action="packageTSVExport" id="${params.id}">GOKb File</g:link>
    </g:if>
    <g:else>
      <g:xEditable class="ipe" owner="${d}" field="name" />
    </g:else>
  </dd>
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
    <g:if test="${d}">
      <dt>
        <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
      </dt>
      <dd>
        <sec:ifAnyGranted roles="ROLE_SUPERUSER">
          <g:xEditableRefData owner="${d}" field="status" config='KBComponent.Status' />
        </sec:ifAnyGranted>
        <sec:ifNotGranted roles="ROLE_SUPERUSER">
          ${d.status?.value ?: 'Not Set'}
        </sec:ifNotGranted>
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
    <dt> <g:annotatedLabel owner="${d}" property="listStatus">List Status</g:annotatedLabel> </dt>
    <dd>
      <g:xEditableRefData owner="${d}" field="listStatus" config='Package.ListStatus' />
    </dd>
    <dt>
      <g:annotatedLabel owner="${d}" property="userListVerifier">List Verifier</g:annotatedLabel>
    </dt>
    <dd>
      <g:manyToOneReferenceTypedown owner="${d}" field="userListVerifier" baseClass="org.gokb.cred.User">${d.userListVerifier?.displayName ?: d.userListVerifier?.username}</g:manyToOneReferenceTypedown>
    </dd>
    <dt> <g:annotatedLabel owner="${d}" property="listVerifierDate">List Verifier Date</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" type="date" field="listVerifiedDate" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="lastUpdateComment">Last Update Comment</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="lastUpdateComment" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel> </dt>
    <dd> <g:xEditableRefData owner="${d}" field="editStatus" config='KBComponent.EditStatus' /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="description">Description</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="description" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="descriptionURL">URL</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="descriptionURL" /> </dd>

  </dl>

  <div id="content">
    <ul id="tabs" class="nav nav-tabs">
      <li role="presentation" class="active"><a href="#packagedetails" data-toggle="tab">Package Details</a></li>
      <g:if test="${d.id}">
        <li role="presentation"><a href="#titledetails" data-toggle="tab">Titles/TIPPs <span class="badge badge-warning"> ${d.currentTitleCount}/ ${d.currentTippCount} </span></a></li>
        <li role="presentation"><a href="#identifiers" data-toggle="tab">Identifiers <span class="badge badge-warning"> ${d?.getCombosByPropertyNameAndStatus('ids','Active')?.size() ?: '0'} </span></a></li>

        <li role="presentation"><a href="#altnames" data-toggle="tab">Alternate Names
          <span class="badge badge-warning"> ${d.variantNames?.size() ?: '0'}</span>
        </a></li>
        <li><a href="#relationships" data-toggle="tab">Relations</a></li>
        <g:if test="${grailsApplication.config.gokb.decisionSupport?.active}">
          <li role="presentation"><a href="#ds" data-toggle="tab">Decision Support</a></li>
        </g:if>
        <li role="presentation"><a href="#activity" data-toggle="tab">Activity</a></li>
        <li role="presentation"><a href="#review" data-toggle="tab">Review Requests</a></li>
        <g:if test="${grailsApplication.config.gokb.costInfo}">
          <li role="presentation"><a href="#pkgCosts" data-toggle="tab">Package Cost Info</a></li>
        </g:if>
      </g:if>
      <g:else>
        <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Titles/TIPPs </span></li>
        <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Identifiers </span></li>
        <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Alternate Names </span></li>
        <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Relations </span></li>
        <g:if test="${grailsApplication.config.gokb.decisionSupport?.active}">
          <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Decision Support </span></li>
        </g:if>
        <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Activity </span></li>
        <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Review Requests </span></li>
        <g:if test="${grailsApplication.config.gokb.costInfo}">
          <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Package Cost Info </span></li>
        </g:if>
      </g:else>
    </ul>

    <div id="my-tab-content" class="tab-content">

      <div class="tab-pane active" id="packagedetails">
        <dl class="dl-horizontal">
          <g:render template="/apptemplates/refdataprops"
            model="${[d:(d), rd:(rd), dtype:(dtype)]}" />
          <dt>
            <g:annotatedLabel owner="${d}" property="globalNote">Global Range</g:annotatedLabel>
          </dt>
          <dd>
            <g:xEditable class="ipe" owner="${d}" field="globalNote" />
          </dd>
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
          <dl>
            <dt><g:annotatedLabel owner="${d}" property="tipps">Titles/TIPPs</g:annotatedLabel></dt>
            <dd>
              <g:link class="display-inline" controller="search" action="index"
                params="[qbe:'g:3tipps', qp_pkg_id:d.id, inline:true, refOid: d.getLogEntityId(), hide:['qp_pkg_id', 'qp_cp', 'qp_pkg', 'qp_pub_id']]"
                id="">Titles in this package</g:link>
              <g:if test="${ editable && params.controller != 'create' }">
                <div class="panel-body">
                  <h4>
                    <g:annotatedLabel owner="${d}" property="addTipp">Add new TIPP</g:annotatedLabel>
                  </h4>
                  <g:form controller="ajaxSupport" action="addToCollection"
                    class="form-inline">
                    <input type="hidden" name="__context" value="${d.class?.name}:${d.id}" />
                    <input type="hidden" name="__newObjectClass" value="org.gokb.cred.TitleInstancePackagePlatform" />
                    <input type="hidden" name="__addToColl" value="tipps" />
                    <input type="hidden" name="__showNew" value="true" />
                    <dl class="dl-horizontal">
                      <dt class="dt-label">Title</dt>
                      <dd>
                        <g:simpleReferenceTypedown class="form-control select-m" name="title" baseClass="org.gokb.cred.TitleInstance" />
                      </dd>
                      <dt class="dt-label">Platform</dt>
                      <dd>
                        <g:simpleReferenceTypedown class="form-control select-m" name="hostPlatform" baseClass="org.gokb.cred.Platform" filter1="Current" />
                      </dd>
                      <dt class="dt-label">URL</dt>
                      <dd>
                        <input type="text" class="form-control select-m" name="url" required />
                      </dd>
                      <dt></dt>
                      <dd>
                        <button type="submit"
                          class="btn btn-default btn-primary">Add</button>
                      </dd>
                    </dl>
                  </g:form>
                </div>
              </g:if>
            </dd>
          </dl>
        </g:if>
      </div>

      <g:render template="/tabTemplates/showVariantnames" model="${[d:displayobj, showActions:true]}" />

      <div class="tab-pane" id="identifiers">
        <dl>
          <dt>
            <g:annotatedLabel owner="${d}" property="ids">Identifiers</g:annotatedLabel>
          </dt>
          <dd>
            <g:render template="/apptemplates/combosByType"
              model="${[d:d, property:'ids', fragment:'identifiers', propagateDelete: "true", cols:[
                        [expr:'toComponent.namespace.value', colhead:'Namespace'],
                        [expr:'toComponent.value', colhead:'ID', action:'link']]]}" />
            <g:if test="${editable}">
              <h4>
                <g:annotatedLabel owner="${d}" property="addIdentifier">Add new Identifier</g:annotatedLabel>
              </h4>
              <g:render template="/apptemplates/addIdentifier" model="${[d:d, hash:'#identifiers']}"/>
            </g:if>
          </dd>
        </dl>
      </div>

      <div class="tab-pane" id="relationships">
        <g:if test="${d.id != null}">
          <dl class="dl-horizontal">
            <dt>
              <g:annotatedLabel owner="${d}" property="successor">Successor</g:annotatedLabel>
            </dt>
            <dd>
              <g:manyToOneReferenceTypedown owner="${d}" field="successor" baseClass="org.gokb.cred.Package">${d.successor?.name}</g:manyToOneReferenceTypedown>
            </dd>
            <dt>
              <g:annotatedLabel owner="${d}" property="successor">Predecessor(s)</g:annotatedLabel>
            </dt>
            <dd>
              <ul>
                <g:each in="${d.previous}" var="c">
                  <li>
                    <g:link controller="resource" action="show" id="${c.getClassName()+':'+c.id}">
                      ${c.name}
                    </g:link>
                  </li>
                </g:each>
              </ul>
            </dd>
            <dt>
              <g:annotatedLabel owner="${d}" property="parent">Parent</g:annotatedLabel>
            </dt>
            <dd>
              <g:manyToOneReferenceTypedown owner="${d}" field="parent" baseClass="org.gokb.cred.Package">${d.parent?.name}</g:manyToOneReferenceTypedown>
            </dd>

            <g:if test="${d.children?.size() > 0}">
              <dt>
                <g:annotatedLabel owner="${d}" property="children">Subsidiaries</g:annotatedLabel>
              </dt>
              <dd>
                <ul>
                  <g:each in="${d.children}" var="c">
                    <li>
                      <g:link controller="resource" action="show" id="${c.getClassName()+':'+c.id}">
                        ${c.name}
                      </g:link>
                    </li>
                  </g:each>
                </ul>
              </dd>
            </g:if>
          </dl>
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
              <button class="btn btn-default" id="rr-only-open">Load Open Requests</button>
              <button class="btn btn-default" id="rr-all">Load All Requests</button>
            </span>
            <span style="white-space:nowrap;">
              <span>TIPP status restriction: </span>
              <select class="form-control" id="rr-tipp-status">
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
    <g:if test="${d.id}">
      <g:render template="/apptemplates/componentStatus"
        model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
    </g:if>
  </div>

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
