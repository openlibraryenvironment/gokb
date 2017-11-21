<g:set var="ctxoid" value="${org.gokb.cred.KBComponent.deproxy(d).class.name}:${d.id}"/>

<table class="table table-striped table-bordered">
  <thead>
    <tr>
      <g:each in="${cols}" var="ch">
        <th>${ch.colhead}</th>
      </g:each>
      <th>Actions</th>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d.getCombosByPropertyName(property)}" var="row">
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
            <g:else>${groovy.util.Eval.x(row, 'x.' + c.expr)}</g:else>
          </td>
        </g:each>
        <td>
          <g:link 
            controller='ajaxSupport' 
            action='delete' 
            params="${['__context':combooid,'fragment':fragment]}"
            class="confirm-click"
            data-confirm-message="Are you sure you wish to delete this ${row.toComponent.getNiceName()}?" >Delete</g:link>
        </td>
      </tr>
    </g:each>
  </tbody>
</table>
