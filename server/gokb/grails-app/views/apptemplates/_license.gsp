<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h1>

<g:render template="kbcomponent" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />

<g:if test="${d.id != null}">
  <dl class="dl-horizontal">

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="licenseURL">License URL</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="url"/></dd>
    </div>

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="licenseDoc">License Doc</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="file"/></dd>
    </div>

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="licenseType">License Type</g:annotatedLabel></dt>
      <dd><g:xEditableRefData owner="${d}" field="type" config='License.Type' /></dd>
    </div>
  </dl>

  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#licsummary" data-toggle="tab">License Summary</a></li>
    <li><a href="#lists" data-toggle="tab">Files</a></li>
  </ul>

  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="licsummary">
      <g:if test="${((d.summaryStatement != null) && (d.summaryStatement.length() > 0 ) )}">
        <h4>Summary Of License</h4>
        ${d.summaryStatement}
      </g:if>
    </div>
    <div class="tab-pane" id="lists">
      <table class="table table-bordered">
        <thead>
          <tr>
            <th>Filename</th>
            <th>Filetype</th>
            <th>Size</th>
            <th>Type</th>
          </tr>
        </thead>
        <tbody>
          <g:each in="${d.fileAttachments}" var="f">
            <tr>
              <td><g:link controller="workflow" action="download" id="${f.guid}">${f.uploadName}</g:link></td>
              <td>${f.uploadMimeType}</td>
              <td>${f.filesize}</td>
              <td>${f.doctype}</td>
            </tr>
          </g:each>
        </tbody>
      </table>
    </div>
  </div>



</g:if>



<script language="JavaScript">

  function showHidden(hidden) {
    var objHidden = document.getElementById(hidden).style;
    if(objHidden.display=="block") {
      objHidden.display="none";
      objHidden.visibility="hidden";
    } else {
        objHidden.display="block";
        objHidden.visibility="visible";
    }
  }

  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>

<script language="JavaScript" src="http://gokb.k-int.com/wz_tooltip.js"/>
