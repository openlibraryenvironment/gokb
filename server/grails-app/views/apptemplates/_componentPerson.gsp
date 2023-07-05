<g:set var="ctxoid" value="${org.gokb.cred.KBComponent.deproxy(d).class.name}:${d.id}"/>

<table class="table table-striped table-bordered">
  <thead>
    <tr>
      <g:each in="${cols}" var="ch">
        <th>${ch.colhead}</th>
      </g:each>
      <g:if test="${delete=='true'}">
        <th>Actions</th>
      </g:if>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d[property]}" var="row">
     <g:set var="rowoid" value="${org.gokb.cred.KBComponent.deproxy(row).class.name}:${row.id}"/>
      <tr>
        <g:each in="${cols}" var="c">           
          <td>
            <g:if test="${c.action=='link-person'}">
              <g:link controller="resource" action="show" id="org.gokb.cred.Person:${row.person.id}">${groovy.util.Eval.x(row, 'x.' + c.expr)}</g:link>
            </g:if>
            <g:else>${groovy.util.Eval.x(row, 'x.' + c.expr)}</g:else>
          </td>
        </g:each>
        <g:if test="${delete=='true'}">
	  <td>
            <g:link controller='ajaxSupport' 
                    action='delete' 
                    params="${[__context:rowoid]}">Delete</g:link>
           </td>
          </g:if>
      </tr>
    </g:each>
  </tbody>
</table>

<g:if test="${targetClass && d.isEditable()}">


  <g:if test="${direction=='in'}">
    <g:set var="recip" value="component"/> 
    <g:set var="comboprop" value="person"/>
  </g:if>
  <g:else>
    <g:set var="recip" value="person"/>
    <g:set var="comboprop" value="component"/>
  </g:else>

  <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
    <input type="hidden" name="__context" value="${ctxoid}"/>
    <input type="hidden" name="__newObjectClass" value="org.gokb.cred.ComponentPerson"/>
													
    <input type="hidden" name="__recip" value="${recip}"/>
	<input type="hidden" name="__refdataName" value="role" />
    
    <input type="hidden" name="type" value="${org.gokb.cred.RefdataCategory.getOID('Combo.Type',d.getComboTypeValue(property))}"/>
    Add Person : <g:simpleReferenceTypedown class="form-control" name="${comboprop}" baseClass="${targetClass}"/> 
    In Role : <g:simpleReferenceTypedown class="form-control" name="__refdataValue" baseClass="org.gokb.cred.RefdataValue" filter1="SPR"/> 
    <button type="submit" class="btn btn-default btn-primary btn-sm ">Add</button>
  </g:form>

</g:if>
