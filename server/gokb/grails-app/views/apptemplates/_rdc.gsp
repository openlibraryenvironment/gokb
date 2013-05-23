<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>Refdata Category: ${d.desc}</h1>
<dl class="dl-horizontal">
  <div class="control-group">
    <dt>Internal Id</dt>
    <dd>${d.id}</dd>
  </div>
  <div class="control-group">
    <dt>Description</dt>
    <dd>${d.desc}</dd>
  </div>
  <div class="control-group">
    <dt>Values</dt>
    <dd>
      <ul>
        <g:each in="${d.values}" var="v">
          <li>${v.value}</li>
        </g:each>
        <hr/>
        <h4>Add refdata value</h4>
        <dl class="dl-horizontal">
          <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
            <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
            <input type="hidden" name="__newObjectClass" value="org.gokb.cred.RefdataValue"/>
            <input type="hidden" name="__recip" value="owner"/>
            <dt>Refdata Value</dt><dd><input type="text" name="value"/></dd>
            <dt>Display Class</dt><dd><input type="text" name="icon"/></dd>
            <dt></dt><dd><button type="submit" class="btn btn-primary btn-small">Add</button></dd>
          </g:form>
        </dl>
      </ul>
    </dd>
  </div>
</dl>
