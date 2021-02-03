<dl class="dl-horizontal">
  <dt>
    <g:annotatedLabel owner="${d}" property="name">Title</g:annotatedLabel>
  </dt>
  <dd style="max-width:60%">
    <g:if test="${displayobj?.id != null}">
      <div>
        ${d.name}<br/>
        <span style="white-space:nowrap;">(Modify title through <i>Alternate Names</i> below)</span>
      </div>
    </g:if>
    <g:else>
      <g:xEditable class="ipe" owner="${d}" field="name" />
    </g:else>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="status">Work</g:annotatedLabel>
  </dt>
  <dd style="width:50%">
    <g:if test="${d.work}">
      <g:link controller="resource" action="show" id="${d.work.class.name}:${d.work.id}"> ${d.work.name} </g:link>
    </g:if>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel>
  </dt>
  <dd>
    <g:manyToOneReferenceTypedown owner="${d}" field="source" baseClass="org.gokb.cred.Source">${d.source?.name}</g:manyToOneReferenceTypedown>
  </dd>

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

  <dt>
    <g:annotatedLabel owner="${d}" property="reasonRetired">Status Reason</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="reasonRetired"
      config='TitleInstance.ReasonRetired' />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="editStatus"
      config='KBComponent.EditStatus' />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="currentPubisher">Latest Publisher</g:annotatedLabel>
  </dt>
  <dd>
    ${d.currentPublisher}&nbsp;
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="imprint">Imprint</g:annotatedLabel>
  </dt>
  <dd>
    <g:manyToOneReferenceTypedown owner="${d}" field="imprint"
      baseClass="org.gokb.cred.Imprint">
      ${d.imprint?.name}
    </g:manyToOneReferenceTypedown>
    &nbsp;
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="firstAuthor">First Author</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" field="firstAuthor" />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="firstEditor">First Editor</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" field="firstEditor" />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="publishedFrom">Published From</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" type="date" field="publishedFrom" />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="publishedTo">Published To</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" type="date" field="publishedTo" />
  </dd>

  <g:if test="${d.id != null && d.titleHistory}">
    <dt>
      <g:annotatedLabel owner="${d}" property="titleHistory">Title History</g:annotatedLabel>
    </dt>
    <dd>
      <g:render template="/apptemplates/fullth" model="${[d:d]}" />
    </dd>
  </g:if>
</dl>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#titledetails" data-toggle="tab">Title Details</a></li>
    <li><a href="#bookdetails" data-toggle="tab">Book Details</a></li>

    <g:if test="${d.id}">
      <li><a href="#altnames" data-toggle="tab">Alternate Names <span class="badge badge-warning"> ${d.variantNames?.size() ?: '0'}</span> </a></li>
      <li><a href="#identifiers" data-toggle="tab">Identifiers <span class="badge badge-warning"> ${d?.getCombosByPropertyNameAndStatus('ids','Active')?.size() ?: '0'} </span></a></li>
      <li><a href="#publishers" data-toggle="tab">Publishers <span
          class="badge badge-warning">
            ${d.publisher?.size() ?: '0'}
        </span></a></li>
      <li><a href="#availability" data-toggle="tab">Package Availability <span
        class="badge badge-warning">
          ${d?.tipps?.findAll{ it.status?.value == 'Current'}?.size() ?: '0'}
      </span></a></li>
      <li><a href="#tipls" data-toggle="tab">Platforms <span
          class="badge badge-warning">
            ${d?.tipls?.findAll{ it.status?.value == 'Current'}?.size() ?: '0'}
        </span></a></li>
      <li><a href="#addprops" data-toggle="tab">Custom Fields <span
        class="badge badge-warning">
          ${d.additionalProperties?.size() ?: '0'}
      </span></a></li>
      <li><a href="#review" data-toggle="tab">Review Tasks (Open/Total)
        <span class="badge badge-warning"> 
          ${d.reviewRequests?.findAll { it.status == org.gokb.cred.RefdataCategory.lookup('ReviewRequest.Status','Open') }?.size() ?: '0'}/${d.reviewRequests.size()}
        </span>
      </a></li>
      <g:if test="${grailsApplication.config.gokb.decisionSupport?.active}" >
        <li><a href="#ds" data-toggle="tab">Decision Support</a></li>
      </g:if>
      <g:if test="${grailsApplication.config.gokb.handleSubjects}" >
        <li><a href="#subjects" data-toggle="tab">Subjects <span class="badge badge-warning"> ${d.subjects?.size() ?: '0'} </span></a></li>
      </g:if>
    </g:if>
    <g:else>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Alternate Names </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Identifiers </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Publishers </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Package Availability </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Platforms </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Custom Fields </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Review Tasks </span></li>
      <g:if test="${grailsApplication.config.gokb.decisionSupport?.active}" >
        <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Decision Support </span></li>
      </g:if>
      <g:if test="${grailsApplication.config.gokb.handleSubjects}" >
        <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Subjects </span></li>
      </g:if>
    </g:else>

  </ul>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="titledetails">
      <dl class="dl-horizontal">

        <dt>
          <g:annotatedLabel owner="${d}" property="medium">Medium</g:annotatedLabel>
        </dt>
        <dd>
          <g:if test="${d.id != null}">
            <g:xEditableRefData owner="${d}" field="medium"
              config='TitleInstance.Medium' />
          </g:if>
          <g:else>
            Book
          </g:else>
        </dd>

        <dt>
          <g:annotatedLabel owner="${d}" property="OAStatus">OA Status</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditableRefData owner="${d}" field="OAStatus"
            config='TitleInstance.OAStatus' />
        </dd>

        <dt>
          <g:annotatedLabel owner="${d}" property="continuingSeries">Continuing Series</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditableRefData owner="${d}" field="continuingSeries"
            config='TitleInstance.ContinuingSeries' />
        </dd>
      </dl>
    </div>

    <div class="tab-pane" id="bookdetails">
      <dl class="dl-horizontal">
        <dt> <g:annotatedLabel owner="${d}" property="editionNumber">Edition Number</g:annotatedLabel> </dt>
        <dd> <g:xEditable class="ipe" owner="${d}" field="editionNumber" /> </dd>

        <dt> <g:annotatedLabel owner="${d}" property="coverImage">Cover Image URL</g:annotatedLabel> </dt>
        <dd> <g:xEditable class="ipe" owner="${d}" field="coverImage" /> </dd>

        <dt> <g:annotatedLabel owner="${d}" property="editionDifferentiator">Edition Differentiator</g:annotatedLabel> </dt>
        <dd> <g:xEditable class="ipe" owner="${d}" field="editionDifferentiator" /> </dd>

        <dt> <g:annotatedLabel owner="${d}" property="editionStatement">Edition Statement</g:annotatedLabel> </dt>
        <dd> <g:xEditable class="ipe" owner="${d}" field="editionStatement" /> </dd>

        <dt> <g:annotatedLabel owner="${d}" property="volumeNumber">Volume Number</g:annotatedLabel> </dt>
        <dd> <g:xEditable class="ipe" owner="${d}" field="volumeNumber" /> </dd>

        <dt> <g:annotatedLabel owner="${d}" property="dateFirstInPrint">Date first in print</g:annotatedLabel> </dt>
        <dd> <g:xEditable class="ipe" owner="${d}" type="date" field="dateFirstInPrint" /> </dd>

        <dt> <g:annotatedLabel owner="${d}" property="dateFirstOnline">Date first online</g:annotatedLabel> </dt>
        <dd> <g:xEditable class="ipe" owner="${d}" type="date" field="dateFirstOnline" /> </dd>

        <dt> <g:annotatedLabel owner="${d}" property="summaryOfContent">Summary of content</g:annotatedLabel> </dt>
        <dd> <g:xEditable class="ipe" owner="${d}" field="summaryOfContent" /> </dd>
      </dl>
    </div>

    <g:render template="/tabTemplates/showVariantnames"
      model="${[d:displayobj, showActions:true]}" />

    <div class="tab-pane" id="availability">
      <g:if test="${d.id}">
        <dt>
          <g:annotatedLabel owner="${d}" property="availability">Package Availability</g:annotatedLabel>
        </dt>
        <dd>
          <g:link class="display-inline" controller="search" action="index"
            params="[qbe:'g:3tipps', inline:true, refOid: d.getLogEntityId(), qp_title_id:d.id, hide:['qp_title_id', 'qp_title']]"
            id="">Availability of this Title</g:link>
        </dd>
      </g:if>
    </div>

    <div class="tab-pane" id="tipls">
      <dt>
        <g:annotatedLabel owner="${d}" property="tipls">Platforms</g:annotatedLabel>
      </dt>
      <dd>
        <table class="table table-striped table-bordered">
          <thead>
            <tr>
              <th>Platform</th>
              <th>Url</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <g:each in="${d.tipls}" var="tipl">
              <tr>
                <td><g:link controller="resource" action="show" id="${tipl.tiplHostPlatform.class.name}:${tipl.tiplHostPlatform.id}"> ${tipl.tiplHostPlatform.name} </g:link></td>
                <td>${tipl.url}</td>
                <td><g:xEditableRefData owner="${tipl}" field="status" config='KBComponent.Status' /></td>
              </tr>
            </g:each>
          </tbody>
        </table>
      </dd>

    </div>

    <div class="tab-pane" id="publishers">
      <g:render template="/tabTemplates/showPublishers"
      model="${[d:displayobj]}" />
    </div>

    <div class="tab-pane" id="identifiers">
      <dl>
        <dt>
          <g:annotatedLabel owner="${d}" property="ids">Identifiers</g:annotatedLabel>
        </dt>
        <dd>
          <g:render template="/apptemplates/combosByType"
            model="${[d:d, property:'ids', fragment:'identifiers', cols:[
                      [expr:'toComponent.namespace.value', colhead:'Namespace'],
                      [expr:'toComponent.value', colhead:'ID', action:'link']]]}" />
          <g:if test="${d.isEditable()}">
            <h4>
              <g:annotatedLabel owner="${d}" property="addIdentifier">Add new Identifier</g:annotatedLabel>
            </h4>
            <g:render template="/apptemplates/addIdentifier" model="${[d:d, hash:'#identifiers', targetType:'book']}"/>
          </g:if>
        </dd>
      </dl>
    </div>

    <div class="tab-pane" id="addprops">
      <g:render template="/apptemplates/addprops"
        model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="/apptemplates/revreqtab"
        model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="ds">
      <g:render template="/apptemplates/dstab" model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="subjects">
          <dl>
            <g:if test="${d.id}">
                <dt>
                      <g:annotatedLabel owner="${d}" property="subjects">Add Subjects</g:annotatedLabel>
                </dt>
                <dd>
                  <!-- this bit could be better  -->
                  <g:render template="/apptemplates/componentSubject"
                                    model="${[d:d, property:'subjects', cols:[[expr:'subject.name',colhead:'Subject Heading',action:'link-subject'],
                                                                                      [expr:'subject.clsmrk', colhead: 'Classification']],targetClass:'org.gokb.cred.Subject',direction:'in']}" />
                </dd>
            </g:if>
          </dl>
        </div>

  </div>
  <g:if test="${d.id}">
    <g:render template="/apptemplates/componentStatus"
      model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
  </g:if>
</div>


<asset:script type="text/javascript">

  $("select[name='publisher_status']").change(function(event) {
  console.log("In here")
    var form =$(event.target).closest("form")
    form.submit();
  });


</asset:script>
