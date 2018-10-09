<g:set var="editable" value="${ d.isEditable() && ((request.curator != null ? request.curator.size() > 0 ? true : false : true) || (params.curationOverride == 'true')) }" />
<dl class="dl-horizontal">
    <dt>
      <g:annotatedLabel owner="${d}" property="title">Title</g:annotatedLabel>
    </dt>
    <dd style="max-width:60%">
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
  <li class="active"><a href="#tippdetails" data-toggle="tab">TIPP Details</a></li>
  <li>
    <a href="#altnames" data-toggle="tab">Alternate Names 
      <span class="badge badge-warning"> ${d.variantNames?.size() ?: '0'}</span>
    </a>
  </li>

  <li>
    <a href="#tippcoverage" data-toggle="tab">Coverage</a>
  </li>
  <g:if test="${ d.isEditable() }">
    <li>
      <a href="#addprops" data-toggle="tab">Additional Properties 
        <span class="badge badge-warning"> ${d.additionalProperties?.size() ?: '0'}</span>
      </a>
    </li>
    <li>
      <a href="#review" data-toggle="tab">Review Requests 
        <span class="badge badge-warning"> ${d.reviewRequests?.size() ?: '0'}</span>
      </a>
    </li>
  </g:if>
</ul>


<div id="my-tab-content" class="tab-content">
    
    <g:render template="/tabTemplates/showVariantnames" 
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
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <g:if test="${d.coverageStatements?.size() > 0}">
              <g:each var="cs" in="${d.coverageStatements.sort { it.startDate }}">
                <tr>
                  <td><g:xEditable class="ipe" owner="${cs}" type="date"
                      field="startDate" /></td>
                  <td><g:xEditable class="ipe" owner="${cs}"
                      field="startVolume" /></td>
                  <td><g:xEditable class="ipe" owner="${cs}"
                      field="startIssue" /></td>
                  <td><g:xEditable class="ipe" owner="${cs}" type="date"
                      field="endDate" /></td>
                  <td><g:xEditable class="ipe" owner="${cs}" field="endVolume" /></td>
                  <td><g:xEditable class="ipe" owner="${cs}" field="endIssue" /></td>
                  <td><g:xEditable class="ipe" owner="${cs}" field="embargo" /></td>
                  <td><g:if test="${editable}"><g:link controller="workflow" action="deleteCoverageStatement" id="${cs.id}">Delete</g:link></g:if></td>
                </tr>
              </g:each>
            </g:if>
            <g:else>
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
                <td></td>
              </tr>
            </g:else>
          </tbody>
        </table>
<g:if test="${editable}">
                <button
                        class="hidden-license-details btn btn-default btn-sm btn-primary "
                        data-toggle="collapse" data-target="#collapseableAddCoverageStatement">
                        Add new <i class="fas fa-plus"></i>
                </button>
                <dl id="collapseableAddCoverageStatement" class="dl-horizontal collapse">
                  <g:form controller="ajaxSupport" action="addToCollection"
                          class="form-inline">
                    <input type="hidden" name="__context"
                            value="${d.class.name}:${d.id}" />
                    <input type="hidden" name="__newObjectClass"
                            value="org.gokb.cred.TIPPCoverageStatement" />
                    <input type="hidden" name="__recip" value="owner" />
                    <dt>Start Date</dt>
                    <dd>
                      <input type="date" name="startDate" />
                    </dd>
                    <dt>Start Volume</dt>
                    <dd>
                      <input type="text" name="startVolume" />
                    </dd>
                    <dt>Start Issue</dt>
                    <dd>
                      <input type="text" name="startIssue" />
                    </dd>
                    <dt>End Date</dt>
                    <dd>
                      <input type="date" name="endDate" />
                    </dd>
                    <dt>End Volume</dt>
                    <dd>
                      <input type="text" name="endVolume" />
                    </dd>
                    <dt>End Issue</dt>
                    <dd>
                      <input type="text" name="endIssue" />
                    </dd>
                    <dt>Embargo</dt>
                    <dd>
                      <input type="text" name="embargo" />
                    </dd>
                    <dt>Coverage Note</dt>
                    <dd>
                      <input type="text" name="coverageNote" />
                    </dd>
                    <dt></dt>
                    <dd>
                      <button type="submit"
                              class="btn btn-default btn-primary btn-sm ">Add</button>
                    </dd>
                  </g:form>
                </dl>
              </g:if>
      </dd>
      <dt>
        <g:annotatedLabel owner="${d}" property="coverageNote">Coverage Note</g:annotatedLabel>
      </dt>
      <dd>
        <g:xEditable class="ipe" owner="${d}" field="coverageNote" />
      </dd>
      <dt>
        <g:annotatedLabel owner="${d}" property="coverageDepth">Coverage Depth</g:annotatedLabel>
      </dt>
      <dd>
        <g:xEditableRefData owner="${d}" field="coverageDepth"
          config='TitleInstancePackagePlatform.CoverageDepth' />
      </dd>
    </dl>
  </div>

  <g:if test="${ d.isEditable() }">
    <div class="tab-pane" id="addprops">
      <g:render template="/apptemplates/addprops"
        model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="/apptemplates/revreqtab" model="${[d:d]}" />
    </div>
  </g:if>
</div>
<g:render template="/apptemplates/componentStatus" 
  model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />

