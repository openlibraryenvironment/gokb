<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h1>

<dl class="dl-horizontal">

  <div class="control-group">
    <dt>Name</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="name"/></dd>
  </div>

  <div class="control-group">
    <dt>GUID</dt>
    <dd>${d.guid}</dd>
  </div>

  <div class="control-group">
    <dt>MD5</dt>
    <dd>${d.md5}</dd>
  </div>

  <div class="control-group">
    <dt>Upload Filename</dt>
    <dd>${d.uploadName}</dd>
  </div>

  <div class="control-group">
    <dt>Upload Mime Type</dt>
    <dd>${d.uploadMimeType}</dd>
  </div>

  <div class="control-group">
    <dt>Filesize</dt>
    <dd>${d.filesize}</dd>
  </div>

  <div class="control-group">
    <dt>Doctype</dt>
    <dd>${d.doctype}</dd>
  </div>

  <div class="control-group">
    <dt>Attached To</dt>
    <dd>
      ${d.attachedToComponents}
      <g:render template="combosByType" 
                contextPath="../apptemplates" 
                model="${[d:d, property:'attachedToComponents', cols:[[expr:'fromComponent.name',
                                                                       colhead:'Component',
                                                                       action:'link'],
                                                                     ], direction:'in']}" />
    </dd>
  </div>

</dl>
