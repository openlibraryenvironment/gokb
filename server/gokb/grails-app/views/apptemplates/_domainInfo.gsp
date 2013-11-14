<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<dl class="dl-horizontal">

  <div class="control-group">
    <dt>Domain Class Name</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="dcName"/></dd>
  </div>

  <div class="control-group">
    <dt>Display Name</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="displayName"/></dd>
  </div>

  <div class="control-group">
    <dt>Type/Category</dt>
    <dd><g:xEditableRefData owner="${d}" field="type" config='DCType' /></dd>
  </div>

</dl>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#permissions" data-toggle="tab">Permissions</a></li>
  </ul>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="permissions">
      Fill out permissions control - User/Group/Perm - Add/remove
    </div>
  </div>
</div>
