<dl class="dl-horizontal">
  <dt> <g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="name" /> </dd>
</dl>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#details" data-toggle="tab">Datafile Details</a></li>
    <li><a href="#addprops" data-toggle="tab">Custom Fields <span class="badge badge-warning">${d.additionalProperties?.size()}</span></a></li>
    <li><a href="#review" data-toggle="tab">Review Requests <span class="badge badge-warning">${d.reviewRequests?.size()}</span></a></li>
  </ul>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="details">
      <dl class="dl-horizontal">
        <dt> <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel> </dt>
        <dd> <g:xEditableRefData owner="${d}" field="status" config='KBComponent.Status' /> </dd>
        <dt> <g:annotatedLabel owner="${d}" property="canEdit">Editable</g:annotatedLabel> </dt>
        <dd> <g:xEditableRefData owner="${d}" field="canEdit" config='YN' /> </dd>
        <dt> <g:annotatedLabel owner="${d}" property="guid">GUID</g:annotatedLabel> </dt>
        <dd> ${d.guid} </dd>
        <dt> <g:annotatedLabel owner="${d}" property="md5">MD5</g:annotatedLabel> </dt>
        <dd> ${d.md5} </dd>
        <dt> <g:annotatedLabel owner="${d}" property="uploadFilename">Upload Filename</g:annotatedLabel> </dt>
        <dd> ${d.uploadName} </dd>
        <dt> <g:annotatedLabel owner="${d}" property="mime">Upload Mime Type</g:annotatedLabel> </dt>
        <dd> ${d.uploadMimeType} </dd>
        <dt> <g:annotatedLabel owner="${d}" property="filesize">Filesize</g:annotatedLabel> </dt>
        <dd> ${d.filesize} </dd>
        <dt> <g:annotatedLabel owner="${d}" property="doctype">Doctype</g:annotatedLabel> </dt>
        <dd> ${d.doctype} </dd>
        <dt> <g:annotatedLabel owner="${d}" property="fileData">File</g:annotatedLabel> </dt>
        <dd> <g:link controller="workflow" action="download" id="${d.guid}">  Download file </g:link></dd>

        <dt> <g:annotatedLabel owner="${d}" property="attachedTo">Attached To</g:annotatedLabel> </dt>
        <dd>
          <table class="table table-striped table-bordered">
            <thead>
              <tr>
                <th>Component</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${d.incomingCombos}" var="r">
                <g:set var="linkedoid"
                  value="${org.gokb.cred.KBComponent.deproxy(r.fromComponent).class.name}:${r.fromComponent.id}" />
                <tr>
                  <td><g:link controller="resource" action="show"
                      id="${linkedoid}">
                      ${r.fromComponent.name}
                    </g:link></td>
                </tr>
              </g:each>
            </tbody>
          </table>
        </dd>
      </dl>
    </div>

    <div class="tab-pane" id="addprops">
       <g:render template="/apptemplates/addprops" model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="/apptemplates/revreqtab" model="${[d:d]}" />
    </div>


  </div>
  <g:render template="/apptemplates/componentStatus" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
</div>


