<dl class="dl-horizontal">
  <dt>
          <g:annotatedLabel owner="${d}" property="name">Imprint Name</g:annotatedLabel>
  </dt>
  <dd>
          <g:xEditable class="ipe" owner="${d}" field="name" />
  </dd>
  <dt>
    <g:annotatedLabel owner="${d}" property="org">Represented Org</g:annotatedLabel>
  </dt>
  <dd>
    <g:manyToOneReferenceTypedown owner="${d}" field="org" baseClass="org.gokb.cred.Org">${d.org?.name}</g:manyToOneReferenceTypedown>
  </dd>
  <dt>
    <g:annotatedLabel owner="${d}" property="currentOwner">Current Owner</g:annotatedLabel>
  </dt>
  <dd>
    ${d.currentOwner}&nbsp;
  </dd>

  <dt class="dt-label">
    <g:annotatedLabel owner="${d}" property="owners">Owners</g:annotatedLabel>
  </dt>

  <dd>
    <table class="table table-striped table-bordered">
      <thead>
        <tr>
          <th>Owner Name</th>
          <th>Combo Status</th>
          <th>Owner From</th>
          <th>Owner To</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <g:each in="${d.getCombosByPropertyName('owners')}" var="p">
          <tr>
            <td><g:link controller="resource" action="show" id="${p.fromComponent.class.name}:${p.fromComponent.id}"> ${p.fromComponent.name} </g:link></td>
            <td><g:xEditableRefData owner="${p}" field="status" config='Combo.Status' /></td>
            <td><g:xEditable class="ipe" owner="${p}" field="startDate" type="date" /></td>
            <td><g:xEditable class="ipe" owner="${p}" field="endDate" type="date" /></td>
            <td><g:link controller="ajaxSupport" action="deleteCombo" id="${p.id}">Delete</g:link></td>
          </tr>
        </g:each>
      </tbody>
    </table>
  </dd>
  <g:if test="${d.id}">
    <g:form controller="ajaxSupport" action="addToStdCollection" class="form-inline">
      <input type="hidden" name="__context" value="${d.class.name}:${d.id}" />
      <input type="hidden" name="__property" value="owners" />
      <dt class="dt-label">Add Owner:</td>
      <dd>
        <g:simpleReferenceTypedown class="form-inline select-ml" name="__relatedObject" baseClass="org.gokb.cred.Org" filter1="Current"/>&nbsp;<button type="submit" class="btn btn-default btn-primary">Add</button>
      </dd>
    </g:form>
  </g:if>
</dl>
