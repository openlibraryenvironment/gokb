<dl class="dl-horizontal">
  <dt>
    <g:annotatedLabel owner="${d}" property="id">Internal Id</g:annotatedLabel>
  </dt>
  <dd>
    ${d.id?:'New record'}
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="description">Category Name / Description</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" field="desc" />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="label">Label</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" field="label" />
  </dd>


  <g:if test="${d.id != null}">
    <dt>
      <g:annotatedLabel owner="${d}" property="values">Values</g:annotatedLabel>
    </dt>
    <dd>
      <table class="table table-bordered">
        <thead>
          <tr>
            <td>Value</td>
            <td>Deprecate (Use)</td>
            <td>Sort Key</td>
            <td>Actions</td>
          </tr>
        </thead>
        <tbody>
          <g:each in="${d.values}" var="v">
            <tr>
              <td>
                <g:xEditable class="ipe" owner="${v}" field="value" />
              </td>
              <td><g:manyToOneReferenceTypedown owner="${v}"
                  field="useInstead" baseClass="org.gokb.cred.RefdataValue"
                  filter1="${d.desc}">
                  ${v.useInstead?.value}
                </g:manyToOneReferenceTypedown></td>
              <td><g:xEditable class="ipe" owner="${v}" field="sortKey" /></td>
              <td></td>
            </tr>
          </g:each>
        </tbody>
      </table>

      <hr />

      <h4>
        <g:annotatedLabel owner="${d}" property="addRD">Add refdata value</g:annotatedLabel>
      </h4>
      <dl class="dl-horizontal">
        <g:form controller="ajaxSupport" action="addToCollection"
          class="form-inline">
          <input type="hidden" name="__context"
            value="${d.className}:${d.id}" />
          <input type="hidden" name="__newObjectClass"
            value="org.gokb.cred.RefdataValue" />
          <input type="hidden" name="__recip" value="owner" />
          <dt>Refdata Value</dt>
          <dd>
            <input type="text" name="value" />
          </dd>
          <dt>Display Class</dt>
          <dd>
            <input type="text" name="icon" />
          </dd>
          <dt>Sort Key</dt>
          <dd>
            <input type="text" name="sortKey" />
          </dd>
          <dt></dt>
          <dd>
            <button type="submit" class="btn btn-default btn-primary btn-sm ">Add</button>
          </dd>
        </g:form>
      </dl>
    </dd>
  </g:if>
  <g:else>
    <p>Other properties will be editable once the package has been
      saved</p>
  </g:else>
</dl>
