<dl>
  <dt>
    <g:annotatedLabel owner="${d}" property="ids">Identifiers</g:annotatedLabel>
  </dt>
  <div style="margin:5px 0px;">
    <g:form method="POST" controller="${controllerName}" action="${actionName}" fragment="ids" params="${params.findAll{k, v -> k != 'ids_status'}}">
      <span>Hide Deleted:</span> <g:select name="ids_status" optionKey="key" optionValue="value" from="${[null:'Off','Active':'On']}" value="${params.ids_status}" />
    </g:form>
  </div>

  <dd>
    <table class="table table-striped table-bordered">
      <thead>
        <tr>
          <g:each in="${cols}" var="ch">
            <th>${ch.colhead}</th>
          </g:each>
          <g:if test="${showActions}">
            <th>Actions</th>
          </g:if>
        </tr>
      </thead>
      <tbody>
        <g:each in="${d.getLinkedIds()}" var="row">
          <tr>
            <g:each in="${cols}" var="c">
              <td>
                <g:if test="${c.action=='link'}">
                  <g:link controller="resource" action="show" id="org.gokb.cred.Identifier:${row.identifier.id}">${row.identifier.value}</g:link>
                </g:if>
                <g:elseif test="${c.action=='editRefData'}">
                  <g:xEditableRefData owner="${row}" field="${c.expr}" config='ComponentIdentifier.Status' />
                </g:elseif>
                <g:else>
                  <span class="${row.status?.value == 'Deleted' ? 'text-deleted' : ''}" title="${row.status?.value == 'Deleted' ? 'This identifier link has been marked as Deleted.' : ''}">
                    ${row.identifier.value}
                  </span>
                </g:else>
              </td>
            </g:each>
            <td>
              <g:if test="${d.isEditable() && showActions}">
                <span>
                  <g:if test="${row.status?.value == 'Deleted'}">
                    <g:link
                      controller='ajaxSupport'
                      action='genericSetRel'
                      params="${['pk':'org.gokb.cred.ComponentIdentifier:'+row.id,'name':'status', 'fragment':fragment, value: 'org.gokb.cred.RefdataValue:' + status_active ]}"
                      class="confirm-click btn-delete"
                      title="Reactivate deleted link"
                      data-confirm-message="Are you sure you wish to reactivate this identifier link?" >Reactivate</g:link>
                  </g:if>
                  <g:else>
                    <g:link
                      controller='ajaxSupport'
                      action='deleteIdLink'
                      params="${['id':row.id,'fragment':fragment,'keepLink': true, 'propagate': "true"]}"
                      class="confirm-click btn-delete"
                      title="Mark this link as 'Deleted'. This will prevent future automatic linkage of these components."
                      data-confirm-message="Are you sure you wish to mark this identifier link as deleted?" >Delete</g:link>
                  </g:else>
                </span>
                  &nbsp;–&nbsp;
                  <g:link
                    controller='ajaxSupport'
                    action='deleteIdLink'
                    params="${['id':row.id,'fragment':fragment, 'propagate': "true"]}"
                    class="confirm-click btn-delete"
                    title="Delete this link"
                    data-confirm-message="Are you sure you wish to delete this identifier link?" >Unlink</g:link>
              </g:if>
            </td>
          </tr>
        </g:each>
      </tbody>
    </table>

    <g:if test="${d.isEditable() && showActions}">
      <h4>
        <g:annotatedLabel owner="${d}" property="addIdentifier">Add new Identifier</g:annotatedLabel>
      </h4>
      <dl class="dl-horizontal">
        <g:form controller="ajaxSupport" action="addIdentifier" class="form-inline">
          <input type="hidden" name="hash" value="${hash}"/>

          <input type="hidden" name="__context" value="${d.class.name}:${d.id}" />

          <dt class="dt-label">Identifier Namespace</dt>
          <dd>
              <g:simpleReferenceTypedown class="form-control" name="identifierNamespace" baseClass="org.gokb.cred.IdentifierNamespace" />
          </dd>

          <dt class="dt-label">Identifier Value</dt>
          <dd>
            <input type="text" class="form-control" name="identifierValue" required />
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

<asset:script type="text/javascript">

  $("select[name='ids_status']").on('change', function(event) {
    var form =$(event.target).closest("form")
    form.submit();
  });


</asset:script>
