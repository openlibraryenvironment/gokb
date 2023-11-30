<table class="table table-striped table-bordered">
  <thead>
    <tr>
      <th>TIPP</th>
      <th>Status</th>
      <th>Package</th>
      <th>Platform</th>
      <th>Start</th>
      <th>End</th>
      <th>Embargo</th>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d}" var="tipp">
      <g:if test="${!onlyCurrent || d.status.value != 'Current'}">
        <tr>
          <td>
            <g:if test="${tipp != null}"><g:link controller="resource" action="show"
                    id="${tipp?.getClassName()+':'+tipp.id}">
              ${tipp.id}
              </g:link></g:if><g:else>ERROR</g:else>
          </td>
          <td>
            ${tipp.status?.value}
          </td>
          <td><g:if test="${tipp.pkg != null}"><g:link controller="resource" action="show"
              id="${tipp.pkg?.getClassName()+':'+tipp.pkg.id}">
              ${tipp.pkg.name}
            </g:link></g:if><g:else>ERROR</g:else></td>
          <td><g:if test="${tipp.hostPlatform != null}"><g:link controller="resource" action="show"
              id="${tipp.hostPlatform?.getClassName()+':'+tipp.hostPlatform.id}">
              ${tipp.hostPlatform.name}
            </g:link></g:if><g:else>ERROR: hostPlatform is null</g:else></td>
          <td>Date: <g:formatDate
              format="${session.sessionPreferences?.globalDateFormat}"
              date="${tipp.startDate}" /><br /> Volume: ${tipp.startVolume}<br />
            Issue: ${tipp.startIssue}
          </td>
          <td>Date: <g:formatDate
              format="${session.sessionPreferences?.globalDateFormat}"
              date="${tipp.endDate}" /><br /> Volume: ${tipp.endVolume}<br />
            Issue: ${tipp.endIssue}
          </td>
          <td>
            ${tipp.embargo}
          </td>
        </tr>
      </g:if>
    </g:each>
  </tbody>
</table>

