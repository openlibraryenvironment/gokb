<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>Refdata Category: ${d.desc}</h1>
<dl class="dl-horizontal">
  <div class="control-group">
    <dt>Internal Id</dt>
    <dd>${d.id?:'New record'}</dd>
  </div>
  <div class="control-group">
    <dt>Caregory Name / Description</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="desc">${d.desc}</g:xEditable></dd>
  </div>

<g:if test="${d.id != null}">
  <div class="control-group">
    <dt>Values</dt>
    <dd>
      <table class="table table-bordered">
        <thead>
          <tr>
            <td>Value</td><td>Deprecate (Use)</td><td>Actions</td>
          </tr>
        </thead>
        <tbody>
          <g:each in="${d.values}" var="v">
            <tr>
              <td>${v.value}</td>
              <td><g:manyToOneReferenceTypedown owner="${d}" field="useInstead" baseClass="org.gokb.cred.RefdataValue" filter1="${d.desc}">${useInstead?.value}</g:manyToOneReferenceTypedown></td>
              <td></td>
            </tr>
          </g:each>
        </tbody>
      </table>

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
    </dd>
  </div>

</g:if>
<g:else>
  <p>Other properties will be editable once the package has been saved</p>
</g:else>


</dl>
