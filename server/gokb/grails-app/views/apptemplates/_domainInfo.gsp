<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<dl class="dl-horizontal">

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="domainClassName">Domain Class Name</g:annotatedLabel></dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="dcName"/></dd>
  </div>

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="displayName">Display Name</g:annotatedLabel></dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="displayName"/></dd>
  </div>

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="type">Type/Category</g:annotatedLabel></dt>
    <dd><g:xEditableRefData owner="${d}" field="type" config='DCType' /></dd>
  </div>

</dl>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#permissions" data-toggle="tab">Permissions</a></li>
  </ul>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="permissions">
      <g:render template="perms" 
                contextPath="../apptemplates" 
                model="${[d:d, acl:acl]}"/>
    </div>
  </div>
</div>
