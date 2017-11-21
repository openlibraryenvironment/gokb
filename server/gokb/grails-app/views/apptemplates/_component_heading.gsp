<%@page import="org.gokb.cred.TitleInstancePackagePlatform"%>

<g:if test="${d?.id != null}">
  ${d?.getNiceName() ?: 'Component'}:

  <g:if test="${ d?.respondsTo('getDisplayName') && d.getDisplayName()}">
    ${d.getDisplayName()}
  </g:if>
  <g:elseif test="${ d?.respondsTo('getName') }">
    ${d?.getName()?.trim() ?: displayobj?.getId()}
  </g:elseif>
  <g:else>
    ${d?.getId() ?: ''}
  </g:else>

  <g:if test="${ !d?.isEditable() }">
    <small><i>&lt;Read only&gt;</i></small>
  </g:if>
</g:if>
<g:else>
  Create New ${d?.getNiceName() ?: 'Component'}
</g:else>
