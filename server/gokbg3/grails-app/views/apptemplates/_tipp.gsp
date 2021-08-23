<g:set var="editable"
       value="${d.isEditable() && ((request.curator != null ? request.curator.size() > 0 ? true : false : true) || (params.curationOverride == 'true' && request.user.isAdmin()))}"/>
<dl class="dl-horizontal">
    <dt>
        <g:annotatedLabel owner="${d}" property="title">Title</g:annotatedLabel>
    </dt>
    <dd style="max-width:60%">
        <g:link controller="resource" action="show"
                id="${d.title?.class?.name + ':' + d.title?.id}">
            ${(d.title?.name) ?: 'Empty'}
        </g:link>
        <g:if test="${d.title}">(${d.title.niceName})</g:if>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="package">Package</g:annotatedLabel>
    </dt>
    <dd>
        <g:link controller="resource" action="show"
                id="${d.pkg?.class?.name + ':' + d.pkg?.id}">
            ${(d.pkg?.name) ?: 'Empty'}
        </g:link>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="platform">Platform</g:annotatedLabel>
    </dt>
    <dd>
        <g:link controller="resource" action="show"
                id="${d.hostPlatform?.class?.name + ':' + d.hostPlatform?.id}">
            ${(d.hostPlatform?.name) ?: 'Empty'}
        </g:link>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="name">TIPP Name</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" field="name"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditableRefData owner="${d}" field="status"
                            config="KBComponent.Status"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="reference">Reference</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" field="reference"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditableRefData owner="${d}" field="editStatus"
                            config='KBComponent.EditStatus'/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="language">Language</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditableRefData owner="${d}" field="language" config="${org.gokb.cred.KBComponent.RD_LANGUAGE}"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="accessStartDate">Access Start Date</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" type="date"
                     field="accessStartDate"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="accessEndDate">Access End Date</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" type="date"
                     field="accessEndDate"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="publisherName">Publisher Name</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" field="publisherName"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="dateFirstInPrint">Date first in print</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" type="date"
                     field="dateFirstInPrint"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="dateFirstOnline">Date first online</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" type="date"
                     field="dateFirstOnline"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="firstAuthor">First Author</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" field="firstAuthor"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="publicationType">Publication Type</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditableRefData owner="${d}" field="publicationType" config='TitleInstancePackagePlatform.PublicationType'/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="volumeNumber">Volume Number</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" field="volumeNumber"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="editionStatement">Edition statement</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" field="editionStatement"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="parentPublicationTitleId">Parent publication title ID</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" field="parentPublicationTitleId"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="precedingPublicationTitleId">Preceding publication title ID</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" field="precedingPublicationTitleId"/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="medium">Medium</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditableRefData owner="${d}" field="medium" config='TitleInstancePackagePlatform.Medium'/>
    </dd>

    <dt>
        <g:annotatedLabel owner="${d}" property="lastChangedExternal">Last external change</g:annotatedLabel>
    </dt>
    <dd>
        <g:xEditable class="ipe" owner="${d}" field="lastChangedExternal" type='date'/>
    </dd>

</dl>

<ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#tippdetails" data-toggle="tab">TIPP Details</a></li>
    <li>
        <a href="#tippcoverage" data-toggle="tab">Coverage</a>
    </li>
    <li><a href="#identifiers" data-toggle="tab">Identifiers <span
            class="badge badge-warning">${d?.getCombosByPropertyNameAndStatus('ids', 'Active')?.size() ?: '0'}</span>
    </a>
    </li>
    <g:if test="${d.isEditable()}">
        <li>
            <a href="#addprops" data-toggle="tab">Additional Properties
                <span class="badge badge-warning">${d.additionalProperties?.size() ?: '0'}</span>
            </a>
        </li>
        <li>
            <a href="#review" data-toggle="tab">Review Requests
                <span class="badge badge-warning">${d.reviewRequests?.size() ?: '0'}</span>
            </a>
        </li>
    </g:if>
    <li>
      <a href="#subjectArea" data-toggle="tab">Subject Area</a>
    </li>
    <li>
      <a href="#series" data-toggle="tab">Series</a>
    </li>
    <li>
      <a href="#prices" data-toggle="tab">Prices
        <span class="badge badge-warning"> ${d.prices?.size() ?: '0'}</span>
      </a>
    </li>
</ul>


<div id="my-tab-content" class="tab-content">

    <div class="tab-pane active" id="tippdetails">

        <g:if test="${d.id != null}">

            <dl class="dl-horizontal">
                <dt>
                    <g:annotatedLabel owner="${d}" property="url">Host Platform URL</g:annotatedLabel>
                </dt>
                <dd>
                    <g:xEditable class="ipe" owner="${d}" field="url"/>
                    <g:if test="${d.url}">
                        &nbsp;<a href="${d.url}" target="new"><i class="fas fa-external-link-alt"></i></a>
                    </g:if>

                </dd>
                <dt>
                    <g:annotatedLabel owner="${d}" property="format">Format</g:annotatedLabel>
                </dt>
                <dd>
                    <g:xEditableRefData owner="${d}" field="format"
                                        config="TitleInstancePackagePlatform.Format"/>
                </dd>
                <dt>
                    <g:annotatedLabel owner="${d}" property="paymentType">Payment Type</g:annotatedLabel>
                </dt>
                <dd>
                    <g:xEditableRefData owner="${d}" field="paymentType"
                                        config="TitleInstancePackagePlatform.PaymentType"/>
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
                        <th>Note</th>
                        <th>Depth</th>
                        <th>Actions</th>
                    </tr>
                    </thead>
                    <tbody>
                    <g:if test="${d.coverageStatements?.size() > 0}">
                        <g:each var="cs" in="${d.coverageStatements.sort { it.startDate }}">
                            <tr>
                                <td><g:xEditable class="ipe" owner="${cs}" type="date"
                                                 field="startDate"/></td>
                                <td><g:xEditable class="ipe" owner="${cs}"
                                                 field="startVolume"/></td>
                                <td><g:xEditable class="ipe" owner="${cs}"
                                                 field="startIssue"/></td>
                                <td><g:xEditable class="ipe" owner="${cs}" type="date"
                                                 field="endDate"/></td>
                                <td><g:xEditable class="ipe" owner="${cs}" field="endVolume"/></td>
                                <td><g:xEditable class="ipe" owner="${cs}" field="endIssue"/></td>
                                <td><g:xEditable class="ipe" owner="${cs}" field="embargo"/></td>
                                <td><g:xEditable class="ipe" owner="${cs}" field="coverageNote"/></td>
                                <td><g:xEditableRefData owner="${cs}" field="coverageDepth"
                                                        config="TIPPCoverageStatement.CoverageDepth"/>
                                </td>
                                <td><g:if test="${editable}"><g:link controller="ajaxSupport"
                                                                     action="deleteCoverageStatement"
                                                                     params="[id: cs.id, fragment: 'tippcoverage']">Delete</g:link></g:if></td>
                            </tr>
                        </g:each>
                    </g:if>
                    <g:else>
                        <tr><td colspan="8"
                                style="text-align:center">${message(code: 'tipp.coverage.empty', default: 'No coverage defined')}</td>
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
                                class="form-inline" params="[fragment: 'tippcoverage']">
                            <input type="hidden" name="__context"
                                   value="${d.class.name}:${d.id}"/>
                            <input type="hidden" name="__newObjectClass"
                                   value="org.gokb.cred.TIPPCoverageStatement"/>
                            <input type="hidden" name="__recip" value="owner"/>
                            <dt class="dt-label">Start Date</dt>
                            <dd>
                                <input class="form-control" type="date" name="startDate"/>
                            </dd>
                            <dt class="dt-label">Start Volume</dt>
                            <dd>
                                <input class="form-control" type="text" name="startVolume"/>
                            </dd>
                            <dt class="dt-label">Start Issue</dt>
                            <dd>
                                <input class="form-control" type="text" name="startIssue"/>
                            </dd>
                            <dt class="dt-label">End Date</dt>
                            <dd>
                                <input class="form-control" type="date" name="endDate"/>
                            </dd>
                            <dt class="dt-label">End Volume</dt>
                            <dd>
                                <input class="form-control" type="text" name="endVolume"/>
                            </dd>
                            <dt class="dt-label">End Issue</dt>
                            <dd>
                                <input class="form-control" type="text" name="endIssue"/>
                            </dd>
                            <dt class="dt-label">Embargo</dt>
                            <dd>
                                <input class="form-control" type="text" name="embargo"/>
                            </dd>
                            <dt class="dt-label">Coverage Depth</dt>
                            <dd>
                                <g:simpleReferenceTypedown name="coverageDepth" baseClass="org.gokb.cred.RefdataValue"
                                                           filter1="TIPPCoverageStatement.CoverageDepth"/>
                            </dd>
                            <dt class="dt-label">Coverage Note</dt>
                            <dd>
                                <input class="form-control" type="text" name="coverageNote"/>
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
                <g:xEditable class="ipe" owner="${d}" field="coverageNote"/>
            </dd>
            <dt>
                <g:annotatedLabel owner="${d}" property="coverageDepth">Coverage Depth</g:annotatedLabel>
            </dt>
            <dd>
                <g:xEditableRefData owner="${d}" field="coverageDepth"
                                    config='TitleInstancePackagePlatform.CoverageDepth'/>
            </dd>
        </dl>
    </div>


    <div class="tab-pane" id="identifiers">
        <dl>
            <dt>
                <g:annotatedLabel owner="${d}" property="ids">Identifiers</g:annotatedLabel>
            </dt>
            <dd>
                <g:render template="/apptemplates/combosByType"
                          model="${[d: d, property: 'ids', fragment: 'identifiers', combo_status: 'Active', cols: [
                                  [expr: 'toComponent.namespace.value', colhead: 'Namespace'],
                                  [expr: 'toComponent.value', colhead: 'ID', action: 'link']]]}"/>
                <g:if test="${d.isEditable()}">
                    <h4>
                        <g:annotatedLabel owner="${d}" property="addIdentifier">Add new Identifier</g:annotatedLabel>
                    </h4>
                    <g:render template="/apptemplates/addIdentifier" model="${[d: d, hash: '#identifiers']}"/>
                </g:if>
            </dd>
        </dl>

    </div>

    <g:if test="${d.isEditable()}">
        <div class="tab-pane" id="addprops">
            <g:render template="/apptemplates/addprops"
                      model="${[d: d]}"/>
        </div>

        <div class="tab-pane" id="review">
            <g:render template="/apptemplates/revreqtab" model="${[d: d]}"/>
        </div>
    </g:if>
    <div class="tab-pane" id="review">
      <g:render template="/apptemplates/revreqtab" model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="subjectArea">
      <dl class="dl-horizontal">
        <dt>
          <g:annotatedLabel owner="${d}" property="subjectArea">Subject Area</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditable owner="${d}" field="subjectArea"/>
        </dd>
      </dl>
    </div>

    <div class="tab-pane" id="series">

      <dl class="dl-horizontal">
        <dt>
          <g:annotatedLabel owner="${d}" property="series">Series</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditable owner="${d}" field="series"/>
        </dd>
      </dl>
    </div>
    <g:render template="/tabTemplates/showPrices" model="${[d: displayobj, showActions: true]}"/>
</div>
<g:render template="/apptemplates/componentStatus"
          model="${[d: displayobj, rd: refdata_properties, dtype: 'KBComponent']}"/>

