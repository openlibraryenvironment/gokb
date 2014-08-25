<%@page import="org.gokb.cred.TitleInstancePackagePlatform"%>
<g:if test="${d?.id != null}">
  <g:if test="${ d instanceof TitleInstancePackagePlatform }" >
    ${d.getNiceName()} : ${d.name ?: d.id} -
    <g:if test="${d.title != null}">
      <g:link controller="resource" action="show" id="${d.title?.class.name+':'+d.title.id}"> ${d.title.name} </g:link> in
    </g:if>
    <g:if test="${d.pkg != null}">
      <g:link controller="resource" action="show" id="${d.pkg?.class.name+':'+d.pkg.id}"> ${d.pkg.name} </g:link> via
    </g:if>
    <g:if test="${d.hostPlatform != null}">
      <g:link controller="resource" action="show" id="${d.hostPlatform?.class.name+':'+d.hostPlatform.id}"> ${d.hostPlatform.name} </g:link>
    </g:if>
  </g:if>
  <g:else>
    ${d?.getNiceName() ?: 'Component'}:

    <g:if test="${ d?.respondsTo('getDisplayName') && d.getDisplayName()}">
      ${d.getDisplayName()}
    </g:if>
    <g:elseif test="${ d?.respondsTo('getName') }">
      ${d?.getName() ?: displayobj?.getId()}
    </g:elseif>
    <g:else>
      ${d?.getId() ?: ''}
    </g:else>

    <g:if test="${ !d?.isEditable() }">
      <small><i>&lt;Read only&gt;</i></small>
    </g:if>
  </g:else>
</g:if>
<g:else>
  Create New ${d?.getNiceName() ?: 'Component'}
</g:else>
