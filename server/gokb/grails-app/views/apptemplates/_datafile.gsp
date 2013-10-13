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
      <table class="table table-striped table-bordered">
        <thead>
          <tr>
            <th>Component</th>
          </tr>
        </thead>
        <tbody>
          <g:each in="${d.incomingCombos}" var="r">
            <g:set var="linkedoid" value="${org.gokb.cred.KBComponent.deproxy(r.fromComponent).class.name}:${r.fromComponent.id}"/>
            <tr>
              <td><g:link controller="resource" action="show" id="${linkedoid}">${r.fromComponent.name}</g:link></td>
            </tr>
          </g:each>
        </tbody>
      </table>
    </dd>
  </div>

</dl>
