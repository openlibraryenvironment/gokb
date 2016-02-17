<div id="content">

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
      <g:xEditableRefData owner="${d}" field="status"
        config="KBComponent.Status" />
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

    <dt><g:annotatedLabel owner="${d}" property="curatoryGroups">Curatory Groups</g:annotatedLabel></dt>
    <dd>
       <g:render template="curatory_groups" contextPath="../apptemplates" model="${[d:d]}" />
    </dd>


  </dl>

  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#platformdetails" data-toggle="tab">Platform Details</a></li>
    <li><a href="#altnames" data-toggle="tab">Alternate Names <span class="badge badge-warning"> ${d.variantNames?.size()}</span> </a></li>
    <li><a href="#ds" data-toggle="tab">Decision Support</a></li>
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
            &nbsp; <a href="${d.primaryUrl}" target="new">Follow Link</a>
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

        <dt> <g:annotatedLabel owner="${d}" property="passwordAuthenitcation">User/Pass Supported</g:annotatedLabel> </dt>
        <dd> <g:xEditableRefData owner="${d}" field="passwordAuthenitcation" config='YN' /> </dd>


      </dl>
    </div>
    <g:render template="showVariantnames" contextPath="../tabTemplates"
      model="${[d:displayobj, showActions:true]}" />
            
    <div class="tab-pane" id="ds">
      <g:render template="dstab" contextPath="../apptemplates" model="${[d:d]}" />
    </div>


  </div>
  <g:render template="componentStatus" contextPath="../apptemplates"
    model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />

</div>
