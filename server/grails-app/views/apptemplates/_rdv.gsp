<dl class="dl-horizontal">
  <dt> <g:annotatedLabel owner="${d}" property="value">Value</g:annotatedLabel> </dt>
  <dd> ${d?.value} </dd>

  <dt> <g:annotatedLabel owner="${d}" property="category">Namespace</g:annotatedLabel> </dt>
  <dd> <g:if test="${d.owner?.isReadable()}"><g:link controller="resource" action="show" id="org.gokb.cred.RefdataCategory:${d.owner.id}">${d?.owner?.desc}</g:link></g:if><g:else>${d?.owner?.desc}</g:else> </dd>
</dl>
