<g:set var="editable" value="${ d.isEditable() && ((d.curatoryGroups ? (request.curator != null && request.curator.size() > 0) : true) || (params.curationOverride == 'true' && request.user.isAdmin())) }" />
<dl class="dl-horizontal">
  <dt>
    <g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" field="name" />
  </dd>
  <dt>
    <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
  </dt>
  <dd>
    <g:if test="${d.isDeletable()}">
      <g:xEditableRefData owner="${d}" field="status"
        config='KBComponent.Status' />
    </g:if>
    <g:else>
      ${d.status}
    </g:else>
  </dd>

  <dt> <g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel> </dt>
  <dd> <g:manyToOneReferenceTypedown owner="${d}" field="source" baseClass="org.gokb.cred.Source"> ${d.source?.name} </g:manyToOneReferenceTypedown> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="source">Provider</g:annotatedLabel> </dt>
  <dd> <g:manyToOneReferenceTypedown owner="${d}" field="provider" baseClass="org.gokb.cred.Org"> ${d.provider?.name} </g:manyToOneReferenceTypedown> </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="editStatus"
      config='KBComponent.EditStatus' />
  </dd>

</dl>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#platformdetails" data-toggle="tab">Platform Details</a></li>
    <g:if test="${d.id}">
      <li><a href="#titledetails" data-toggle="tab">Hosted TIPPs</span> </a></li>
      <li><a href="#packages" data-toggle="tab">Packages</a></li>
      <li><a href="#altnames" data-toggle="tab">Alternate Names <span class="badge badge-warning"> ${d.variantNames?.size() ?: '0'}</span> </a></li>
      <g:if test="${grailsApplication.config.gokb.decisionSupport?.active}">
        <li><a href="#ds" data-toggle="tab">Decision Support</a></li>
      </g:if>
      <li><a href="#review" data-toggle="tab">Review Tasks (Open/Total)<span
          class="badge badge-warning">
            ${d.reviewRequests?.findAll { it.status == org.gokb.cred.RefdataCategory.lookup('ReviewRequest.Status','Open') }?.size() ?: '0'}/${d.reviewRequests.size()}
        </span></a></li>
    </g:if>
    <g:else>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Hosted TIPPs </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Packages </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Alternate Names </span></li>
      <g:if test="${grailsApplication.config.gokb.decisionSupport?.active}">
        <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Decision Support </span></li>
      </g:if>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Review Tasks </span></li>
    </g:else>
  </ul>


  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="platformdetails">

      <dl class="dl-horizontal">
        <dt>
          <g:annotatedLabel owner="${d}" property="primaryURL">Primary URL</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditable class="ipe" owner="${d}" field="primaryUrl">${d.primaryUrl}</g:xEditable>
          <g:if test="${d.primaryUrl}">
            <g:if test="${d.primaryUrl.startsWith('http')}">
              &nbsp; <a href="${d.primaryUrl}" target="new"><i class="fas fa-external-link-alt"></i></a>
            </g:if>
            <g:else>
              &nbsp; <span class="badge badge-warning">!Unknown URL format!</span>
            </g:else>
          </g:if>
        </dd>

        <dt>
          <g:annotatedLabel owner="${d}" property="software">Software</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditableRefData owner="${d}" field="software"
            config='Platform.Software' />
        </dd>

        <dt>
          <g:annotatedLabel owner="${d}" property="service">Service</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditableRefData owner="${d}" field="service"
            config='Platform.Service' />
        </dd>

        <dt> <g:annotatedLabel owner="${d}" property="authentication">Authentication</g:annotatedLabel> </dt>
        <dd> <g:xEditableRefData owner="${d}" field="authentication" config='Platform.AuthMethod' /> </dd>

        <dt> <g:annotatedLabel owner="${d}" property="ipAuthentication">IP Auth Supported</g:annotatedLabel> </dt>
        <dd> <g:xEditableRefData owner="${d}" field="ipAuthentication" config='YN' /> </dd>

        <dt> <g:annotatedLabel owner="${d}" property="shibbolethAuthentication">Shibboleth Supported</g:annotatedLabel> </dt>
        <dd> <g:xEditableRefData owner="${d}" field="shibbolethAuthentication" config='YN' /> </dd>

        <dt> <g:annotatedLabel owner="${d}" property="passwordAuthentication">User/Pass Supported</g:annotatedLabel> </dt>
        <dd> <g:xEditableRefData owner="${d}" field="passwordAuthentication" config='YN' /> </dd>


      </dl>
    </div>
    <div class="tab-pane" id="titledetails">
      <g:if test="${params.controller != 'create'}">
        <g:link class="display-inline" controller="search" action="index"
          params="[qbe:'g:3tipps', qp_plat_id:d.id, inline:true, refOid: d.getLogEntityId(), hide:['qp_cp', 'qp_pub_id', 'qp_plat', 'qp_plat_id']]"
          id="">TIPPs on this Platform</g:link>
      </g:if>
      <g:else>
        TIPPs can be added after the creation process has been finished.
      </g:else>
    </div>

    <div class="tab-pane" id="packages">
      <dl>
        <dt>
          <g:annotatedLabel owner="${d}" property="packages">Packages</g:annotatedLabel>
        </dt>
        <dd>
          <g:link class="display-inline" controller="search" action="index"
            params="[qbe:'g:1packages', qp_platform_id:d.id, inline:true, refOid: d.getLogEntityId(), hide:['qp_platform', 'qp_platform_id']]"
            id="">TIPPs on this Platform</g:link>
        </dd>
      </dl>
    </div>

    <g:render template="/tabTemplates/showVariantnames"
      model="${[d:displayobj, showActions:true]}" />

    <div class="tab-pane" id="ds">
      <g:render template="/apptemplates/dstab" model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="/apptemplates/revreqtab"
        model="${[d:d]}" />
    </div>


  </div>
  <g:if test="${d.id}">
    <g:render template="/apptemplates/componentStatus"
      model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
  </g:if>

</div>
