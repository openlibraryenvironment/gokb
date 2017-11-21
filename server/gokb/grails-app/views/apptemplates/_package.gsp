<g:set var="editable" value="${ d.isEditable() && ((request.curator != null ? request.curator.size() > 0 : true) || (params.curationOverride == "true")) }" />
  <dl class="dl-horizontal">
    <dt>
      <g:annotatedLabel owner="${d}" property="name">Package Name</g:annotatedLabel>
    </dt>
    <dd>
      ${d.name}
      <g:if test="${ editable }">(Modify name through variants below)</g:if><br/>
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
      <g:manyToOneReferenceTypedown owner="${d}" field="userListVerifier" baseClass="org.gokb.cred.User">${d.userListVerifier?.displayName}</g:manyToOneReferenceTypedown>
    </dd>
    <dt> <g:annotatedLabel owner="${d}" property="listVerifierDate">List Verifier Date</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" type="date" field="listVerifiedDate" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="lastUpdateComment">Update Method</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="lastUpdateComment" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel> </dt>
    <dd> <g:xEditableRefData owner="${d}" field="editStatus" config='KBComponent.EditStatus' /> </dd>


    <dt><g:annotatedLabel owner="${d}" property="curatoryGroups">Curatory Groups</g:annotatedLabel></dt>
    <dd> <g:render template="curatory_groups" contextPath="../apptemplates" model="${[d:d]}" /> </dd>
  </dl>


    <ul id="tabs" class="nav nav-tabs">
      <li class="active"><a href="#packagedetails" data-toggle="tab">Package Details</a></li>
      <li><a href="#titledetails" data-toggle="tab">Titles <span class="badge badge-warning"> ${d.tipps?.size()} </span></a></li>
      <li><a href="#identifiers" data-toggle="tab">Identifiers <span class="badge badge-warning"> ${d.ids?.size()} </span></a></li>      
      <li><a href="#altnames" data-toggle="tab">Alternate Names 
        <span class="badge badge-warning"> ${d.variantNames?.size()}</span>
      </a></li>
      <li><a href="#ds" data-toggle="tab">Decision Support</a></li>
      <li><a href="#activity" data-toggle="tab">Activity</a></li>
    </ul>

    <div id="my-tab-content" class="tab-content">

      <div class="tab-pane active" id="packagedetails">
        <dl class="dl-horizontal">
          <g:render template="refdataprops" contextPath="../apptemplates"
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
        <g:link class="display-inline" controller="search" action="index"
          params="[qbe:'g:3tipps', qp_pkg_id:d.id, hide:['qp_pkg_id', 'qp_cp', 'qp_pkg', 'qp_pub_id', 'qp_plat']]"
          id="">Titles in this package</g:link>

        <g:if test="${ editable }">
          <g:form controller="ajaxSupport" action="addToCollection"
            class="form-inline">
            <input type="hidden" name="__context" value="${d.class?.name}:${d.id}" />
            <input type="hidden" name="__newObjectClass" value="org.gokb.cred.TitleInstancePackagePlatform" />
            <input type="hidden" name="__addToColl" value="tipps" />
            <dl class="dl-horizontal">
              <dt>Title</dt>
              <dd>
                <g:simpleReferenceTypedown class="form-control" name="title" baseClass="org.gokb.cred.TitleInstance" />
              </dd>
              <dt>Platform</dt>
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
        </g:if>
      </div>

     <g:render template="showVariantnames" contextPath="../tabTemplates" model="${[d:displayobj, showActions:true]}" />

      <div class="tab-pane" id="identifiers">
        <g:render template="combosByType" contextPath="../apptemplates"
                                model="${[d:d, property:'ids', cols:[
                  [expr:'toComponent.namespace.value', colhead:'Namespace'],
                  [expr:'toComponent.value', colhead:'ID', action:'link']]]}" />
      </div>

      <div class="tab-pane" id="ds">
        <g:render template="dstab" contextPath="../apptemplates" model="${[d:d]}" />
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
                <td>${h[0].title?.name}</td>
              </tr>
            </g:each>
          </tbody>
        </table>
      </div>


    </div>
    <g:render template="componentStatus" contextPath="../apptemplates"
      model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
  </div>
