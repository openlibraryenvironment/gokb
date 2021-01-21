<g:set var="json" value="${d.resultJson}" />
<dl class="dl-horizontal">
  <dt>
    <g:annotatedLabel owner="${d}" property="uuid">Internal ID</g:annotatedLabel>
  </dt>
  <dd>
    ${d.uuid}
  </dd>
  <dt>
    <g:annotatedLabel owner="${d}" property="description">Description</g:annotatedLabel>
  </dt>
  <dd>
    ${d.description}
  </dd>
  <dt>
    <g:annotatedLabel owner="${d}" property="linkedItemId">Linked Component</g:annotatedLabel>
  </dt>
  <dd>
    <g:set var="item" value="${ d.linkedItemId ? org.gokb.cred.KBComponent.get(d.linkedItemId) : null }" />
    <g:if test="item">
      <g:link controller="resource" action="show" id="${item?.uuid}">${item?.name}</g:link>
    </g:if>
  </dd>
  <dt>
    <g:annotatedLabel owner="${d}" property="startTime">Start Time</g:annotatedLabel>
  </dt>
  <dd>
    ${d.startTime}
  </dd>
  <dt>
    <g:annotatedLabel owner="${d}" property="endTime">End Time</g:annotatedLabel>
  </dt>
  <dd>
    ${d.endTime}
  </dd>
  <dt>
    <g:annotatedLabel owner="${d}" property="resultJson">Result Message</g:annotatedLabel>
  </dt>
  <dd>
    ${json?.message}
  </dd>
  <dt>
    <g:annotatedLabel owner="${d}" property="resultJson">Errors</g:annotatedLabel>
  </dt>
  <dd>
    <g:if test="${json?.errors?.global}">
      <div>Global</div>
      <ul>
        <g:each in="${json?.errors?.global}" var="ge">
          <li>${ge.message}</li>
        </g:each>
      </ul>
    </g:if>
    <g:if test="${json?.errors?.tipps}">
      <div>Titles</div>
      <ul>
        <g:each in="${json?.errors?.tipps}" var="te">
          <li>${ge.message}</li>
        </g:each>
      </ul>
    </g:if>
    <g:if test="${!json?.errors}">
      No Errors
    </g:if>
  </dd>
</dl>