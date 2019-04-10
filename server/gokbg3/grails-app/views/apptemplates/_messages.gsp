
<g:if test="${flash.error}">
  <div id="error" style="display:none">
    <ul>
    <g:if test="${flash.error instanceof String}">
      <li>${flash.error}</li>
    </g:if>
    <g:else>
      <g:each in="${flash.error}" var="error">
        <li>${error}</li>
      </g:each>
    </g:else>
    </ul>
  </div>
</g:if>
<g:elseif test="${flash.success}">
  <div id="success" style="display:none">
    <ul>
    <g:if test="${flash.success instanceof String}">
      <li>${flash.success}</li>
    </g:if>
    <g:else>
      <g:each in="${flash.success}" var="success">
        <li>${success}</li>
      </g:each>
    </g:else>
    </ul>
  </div>
</g:elseif>
<g:elseif test="${flash.message}">
  <div id="msg" style="display:none">
    <ul>
    <g:if test="${flash.message instanceof String}">
      <li>${flash.message}</li>
    </g:if>
    <g:else>
      <g:each in="${flash.message}" var="msg">
        <li>${msg}</li>
      </g:each>
    </g:else>
    </ul>
  </div>
</g:elseif>
