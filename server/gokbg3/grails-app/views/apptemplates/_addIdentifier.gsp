<g:set var="ctxoid" value="${org.gokb.cred.KBComponent.deproxy(d).class.name}:${d.id}"/>

<g:if test="${d.id}">
  <dl class="dl-horizontal">
    <g:form controller="ajaxSupport" action="addIdentifier" class="form-inline">
      <input type="hidden" name="hash" value="${hash}"/>

      <input type="hidden" name="__context" value="${ctxoid}" />

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
<g:else>
  Identifiers can be added after the creation process is finished.
</g:else>

