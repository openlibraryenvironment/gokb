<g:set var="ctxoid" value="${org.gokb.cred.KBComponent.deproxy(d).class.name}:${d.id}"/>
<g:set var="pstring" value="${property + '_status'}" />
<g:set var="pstatus" value="${params[pstring] ?: (combo_status ?: 'Active')}" />

<div style="margin:5px 0px;">
  <g:form method="POST" controller="${controllerName}" action="${actionName}" fragment="${fragment}" params="${params.findAll{k, v -> k != pstring}}">

  <span>Hide Deleted:</span> <g:select name="${pstring}" optionKey="key" optionValue="value" from="${['Active':'On']}" value="${pstatus}" noSelection="${[null: 'Off']}" />
  </g:form>
</div>

<table class="table table-striped table-bordered">
  <thead>
    <tr>
      <g:each in="${cols}" var="ch">
        <th>${ch.colhead}</th>
      </g:each>
      <g:if test="${!noaction}">
        <th>Actions</th>
      </g:if>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d.getCombosByPropertyNameAndStatus(property, pstatus)}" var="row">
      <g:set var="combooid" value="${org.gokb.cred.KBComponent.deproxy(row).class.name}:${row.id}"/>
      <g:if test="${d.isComboReverse(property)}">
        <g:set var="linkedoid" value="${org.gokb.cred.KBComponent.deproxy(row.fromComponent).class.name}:${row.fromComponent.id}"/>
      </g:if>
      <g:else>
        <g:set var="linkedoid" value="${org.gokb.cred.KBComponent.deproxy(row.toComponent).class.name}:${row.toComponent.id}"/>
      </g:else>
      <tr>
        <g:each in="${cols}" var="c">
          <td>
            <g:if test="${c.action=='link'}">
              <g:link controller="resource" action="show" id="${linkedoid}">${groovy.util.Eval.x(row, 'x.' + c.expr)}</g:link>
            </g:if>
            <g:elseif test="${c.action=='editRefData'}">
              <g:xEditableRefData owner="${row}" field="${c.expr}" config='Combo.Status' />
            </g:elseif>
            <g:else>
              <span class="${row.status?.value == 'Deleted' ? 'text-deleted' : ''}" title="${row.status?.value == 'Deleted' ? 'This link has been marked as Deleted.' : ''}">
                ${groovy.util.Eval.x(row, 'x.' + c.expr)}
              </span>
            </g:else>
          </td>
        </g:each>
        <td>
          <g:if test="${d.isEditable() && (d.respondsTo('curatoryGroups') ? (!d.curatoryGroups ? true : cur) : true) && !noaction}">
            <span>
              <g:if test="${row.status?.value == 'Deleted'}">
                <g:link
                  controller='ajaxSupport'
                  action='genericSetRel'
                  params="${['pk':'org.gokb.cred.Combo:'+row.id,'name':'status', 'fragment':fragment, value: 'org.gokb.cred.RefdataValue:' + org.gokb.cred.RefdataCategory.lookup('Combo.Status', 'Active').id ]}"
                  class="confirm-click btn-delete"
                  title="Reactivate deleted link"
                  data-confirm-message="Are you sure you wish to remove this ${row.toComponent.niceName}?" >Reactivate</g:link>
              </g:if>
              <g:else>
                <g:link
                  controller='ajaxSupport'
                  action='deleteCombo'
                  params="${['id':row.id,'fragment':fragment,'keepLink': true, 'propagate': "true"]}"
                  class="confirm-click btn-delete"
                  title="Mark this link as 'Deleted'. This will prevent future automatic linkage of these components."
                  data-confirm-message="Are you sure you wish to remove this ${row.toComponent.niceName}?" >Delete</g:link>
              </g:else>
            </span>
              &nbsp;–&nbsp;
              <g:link
                controller='ajaxSupport'
                action='deleteCombo'
                params="${['id':row.id,'fragment':fragment, 'propagate': "true"]}"
                class="confirm-click btn-delete"
                title="Delete this link"
                data-confirm-message="Are you sure you wish to delete this ${row.toComponent.niceName}?" >Unlink</g:link>
          </g:if>
        </td>
      </tr>
    </g:each>
  </tbody>
</table>

<asset:script>
  $("select[name='${pstring}']").change(function(event) {
    var form =$(event.target).closest("form")
    form.submit();
  });
</asset:script>
