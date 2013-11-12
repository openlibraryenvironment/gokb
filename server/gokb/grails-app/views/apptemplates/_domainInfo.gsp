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
