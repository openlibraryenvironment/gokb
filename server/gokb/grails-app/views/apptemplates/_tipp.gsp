
<dl class="dl-horizontal">
    <dt>
      <g:annotatedLabel owner="${d}" property="title">Title</g:annotatedLabel>
    </dt>
    <dd>
      <g:link controller="resource" action="show"
        id="${d.title?.class?.name+':'+d.title?.id}">
        ${(d.title?.name)?:'Empty'}
      </g:link>
    </dd>

    <dt>
      <g:annotatedLabel owner="${d}" property="package">Package</g:annotatedLabel>
    </dt>
    <dd>
      <g:link controller="resource" action="show"
        id="${d.pkg?.class?.name+':'+d.pkg?.id}">
        ${(d.pkg?.name)?:'Empty'}
      </g:link>
    </dd>

    <dt>
      <g:annotatedLabel owner="${d}" property="platform">Platform</g:annotatedLabel>
    </dt>
    <dd>
      <g:link controller="resource" action="show"
        id="${d.hostPlatform?.class?.name+':'+d.hostPlatform?.id}">
        ${(d.hostPlatform?.name)?:'Empty'}
      </g:link>
    </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="status"
      config="KBComponent.Status" />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="reference">Reference</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" field="reference" />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="editStatus"
      config='KBComponent.EditStatus' />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="accessStartDate">Access Start Date</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" type="date"
      field="accessStartDate" />
  </dd>
  <dt>
    <g:annotatedLabel owner="${d}" property="accessStartDate">Access End Date</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" type="date"
      field="accessEndDate" />
  </dd>
</dl>

<ul id="tabs" class="nav nav-tabs">
  <li class="active"><a href="#tippdetails" data-toggle="tab">TIPP
      Details</a></li>
  <li><a href="#altnames" data-toggle="tab">Alternate Names 
    <span class="badge badge-warning"> ${d.variantNames?.size()}</span>
  </a></li>

  <g:if test="${ d.isEditable() }">
    <li><a href="#tippcoverage" data-toggle="tab">Coverage</a></li>
  </g:if>
  <li><a href="#tipplists" data-toggle="tab">Lists</a></li>
  <g:if test="${ d.isEditable() }">
    <li><a href="#addprops" data-toggle="tab">Additional
        Properties <span class="badge badge-warning"> ${d.additionalProperties?.size()}
      </span>
    </a></li>
    <li><a href="#review" data-toggle="tab">Review Requests <span
        class="badge badge-warning">
          ${d.reviewRequests?.size()}
      </span></a></li>
  </g:if>
</ul>


<div id="my-tab-content" class="tab-content">
    
    <g:render template="showVariantnames" contextPath="../tabTemplates"
      model="${[d:displayobj, showActions:true]}" />

  <div class="tab-pane active" id="tippdetails">

    <g:if test="${d.id != null}">

      <dl class="dl-horizontal">
        <dt>
          <g:annotatedLabel owner="${d}" property="url">Host Platform URL</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditable class="ipe" owner="${d}" field="url" />
          <g:if test="${d.url}">
            &nbsp;<a href="${d.url}" target="new">Follow Link</a>
          </g:if>

        </dd>
        <dt>
          <g:annotatedLabel owner="${d}" property="format">Format</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditableRefData owner="${d}" field="format"
            config="TitleInstancePackagePlatform.Format" />
        </dd>
        <dt>
          <g:annotatedLabel owner="${d}" property="paymentType">Payment Type</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditableRefData owner="${d}" field="paymentType"
            config="TitleInstancePackagePlatform.PaymentType" />
        </dd>
      </dl>
    </g:if>
  </div>

  <g:if test="${ d.isEditable() }">
    <div class="tab-pane" id="tippcoverage">
      <dl class="dl-horizontal">
        <dt>
          <g:annotatedLabel owner="${d}" property="coverage">Coverage</g:annotatedLabel>
        </dt>
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
                <td><g:xEditable class="ipe" owner="${d}" type="date"
                    field="startDate" /></td>
                <td><g:xEditable class="ipe" owner="${d}"
                    field="startVolume" /></td>
                <td><g:xEditable class="ipe" owner="${d}"
                    field="startIssue" /></td>
                <td><g:xEditable class="ipe" owner="${d}" type="date"
                    field="endDate" /></td>
                <td><g:xEditable class="ipe" owner="${d}" field="endVolume" /></td>
                <td><g:xEditable class="ipe" owner="${d}" field="endIssue" /></td>
                <td><g:xEditable class="ipe" owner="${d}" field="embargo" /></td>
              </tr>
            </tbody>
          </table>
        </dd>
        <dt>
          <g:annotatedLabel owner="${d}" property="covergaeNote">Coverage Note</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditable class="ipe" owner="${d}" field="coverageNote" />
        </dd>
      </dl>
    </div>
  </g:if>

  <div class="tab-pane" id="tipplists"></div>

  <g:if test="${ d.isEditable() }">
    <div class="tab-pane" id="addprops">
      <g:render template="addprops" contextPath="../apptemplates"
        model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="revreqtab" contextPath="../apptemplates" model="${[d:d]}" />
    </div>
  </g:if>
</div>
<g:render template="componentStatus" contextPath="../apptemplates"
  model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />

