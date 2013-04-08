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

        <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
          <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
          <input type="hidden" name="__newObjectClass" value="org.gokb.cred.RefdataValue"/>
          <input type="hidden" name="__recip" value="owner"/>
          Refdata Value: <input type="text" name="value"/>
          Display Class: <input type="text" name="icon"/>
          <button type="submit" class="btn btn-primary btn-small">Add Refdata Value</button>
        </g:form>

      </ul>
    </dd>
  </div>
</dl>
