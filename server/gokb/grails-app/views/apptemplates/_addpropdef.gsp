<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>Additional Property Definition</h1>

<dl class="dl-horizontal">
  <div class="control-group">
    <dt>Internal Id</dt>
    <dd>${d.id?:'New record'}</dd>
  </div>

  <div class="control-group">
    <dt>Property Name</dt>
    <dd><g:xEditable owner="${d}" field="propertyName"/></dd>
  </div>
</dl>
