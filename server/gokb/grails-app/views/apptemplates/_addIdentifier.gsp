<g:set var="ctxoid" value="${org.gokb.cred.KBComponent.deproxy(d).class.name}:${d.id}"/>


<dl class="dl-horizontal">
  <g:form controller="ajaxSupport" action="addIdentifier" class="form-inline">
    <input type="hidden" name="hash" value="${hash}"/>

    <input type="hidden" name="__context" value="${ctxoid}" />

    <dt>Identifier Namespace</dt>
    <dd>
        <g:simpleReferenceTypedown class="form-control" name="identifierNamespace" baseClass="org.gokb.cred.IdentifierNamespace" />
    </dd>

    <dt>Identifier Value</dt>
    <dd>
      <input type="text" name="identifierValue" />
    </dd>

    <dt></dt>
    <dd>
      <button type="submit" class="btn btn-default btn-primary btn-sm ">Add</button>
    </dd>

  </g:form>
</dl>


