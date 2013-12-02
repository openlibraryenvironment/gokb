<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<dl class="dl-horizontal">
  <dt><g:annotatedLabel owner="${d}" property="domainClassName">Domain Class Name</g:annotatedLabel></dt>
  <dd><g:xEditable class="ipe" owner="${d}" field="dcName"/></dd>

  <dt><g:annotatedLabel owner="${d}" property="displayName">Display Name</g:annotatedLabel></dt>
  <dd><g:xEditable class="ipe" owner="${d}" field="displayName"/></dd>

  <dt><g:annotatedLabel owner="${d}" property="type">Type/Category</g:annotatedLabel></dt>
  <dd><g:xEditableRefData owner="${d}" field="type" config='DCType' /></dd>
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
