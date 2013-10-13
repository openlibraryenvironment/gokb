<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h1>

<dl class="dl-horizontal">
  <div class="control-group">
    <dt>Name</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="name"/></dd>
  </div>
</dl>
