<dl>
  <dt>
    <g:annotatedLabel owner="${d}" property="publishers">Publishers</g:annotatedLabel>
  </dt>
  <div style="margin:5px 0px;">
    <g:form method="POST" controller="${controllerName}" action="${actionName}" fragment="publishers" params="${params.findAll{k, v -> k != 'publisher_status'}}">

    <span>Hide Deleted:</span> <g:select name="publisher_status" optionKey="key" optionValue="value" from="${[null:'Off','Active':'On']}" value="${params.publisher_status}" />
    </g:form>
  </div>

  <dd>
    <table class="table table-striped table-bordered">
      <thead>
        <tr>
          <th>Publisher Name</th>
          <th>Combo Status</th>
          <th>Publisher From</th>
          <th>Publisher To</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <g:each in="${d.getCombosByPropertyNameAndStatus('publisher',params.publisher_status)}" var="p">
          <tr>
            <td><g:link controller="resource" action="show" id="${p.toComponent.class.name}:${p.toComponent.id}"> ${p.toComponent.name} </g:link></td>
            <td><g:xEditableRefData owner="${p}" field="status" config='Combo.Status' /></td>
            <td><g:xEditable class="ipe" owner="${p}" field="startDate" type="date" /></td>
            <td><g:xEditable class="ipe" owner="${p}" field="endDate" type="date" /></td>
            <td><g:if test="${d.isEditable()}"><g:link controller="ajaxSupport" action="deleteCombo" id="${p.id}"  onclick="return confirm('Are you sure you want to delete this link?')">Delete</g:link></g:if></td>
          </tr>
        </g:each>
      </tbody>
    </table>
    <g:if test="${d.isEditable()}">
      <h4>
        <g:annotatedLabel owner="${d}" property="addPublisher">Add new Publisher</g:annotatedLabel>
      </h4>
      <dl class="dl-horizontal">
        <g:form controller="ajaxSupport" action="addToStdCollection">
          <input type="hidden" name="__context" value="${d.class.name}:${d.id}" />
          <input type="hidden" name="__property" value="publisher" />
          <input type="hidden" name="fragment" value="#publishers" />
          <dt class="dt-label">Organization</dt>
          <dd>
            <g:simpleReferenceTypedown class="form-control select-ml" name="__relatedObject" baseClass="org.gokb.cred.Org" style="display:block;" />
          </dd>
          <dt></dt>
          <dd>
            <button type="submit" class="btn btn-default btn-primary">Add</button>
          </dd>
        </g:form>
      </dl>
    </g:if>
  </dd>
</dl>
