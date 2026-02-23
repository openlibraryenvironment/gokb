<p/>
<g:message code="curatoryGroup.alert.daily.greeting"/>
<p/>

<g:message code="curatoryGroup.alert.daily.jobs.intro" locale="${locale ?: 'en'}" args="${[groupName]}" />
<p/>
<table>
  <thead>
    <tr>
      <th>
        <g:message code="curatoryGroup.alert.daily.jobs.header.package" locale="${locale ?: 'en'}"/>
      </th>
      <th>
        <g:message code="curatoryGroup.alert.daily.jobs.header.message" locale="${locale ?: 'en'}"/>
      </th>
    </tr>
  <thead>
  <tbody>
    <g:each in="${items}" var="job">
      <tr>
        <td>
          <a href="${job.editLink}" target="_blank">${job.packageName}</a>
        </td>
        <td>
          <g:message code="${job.messageCode}" locale="${locale ?: 'en'}"/>
        </td>
      </tr>
    </g:each>
  </tbody>
</table>
<p/>
<g:message code="curatoryGroup.alert.daily.jobs.support" locale="${locale ?: 'en'}"/> <a href="mailto:${supportAddress}">${supportAddress}</a>.

<p/>
<g:message code="curatoryGroup.alert.closing" locale="${locale ?: 'en'}"/>
<g:message code="curatoryGroup.alert.signature" locale="${locale ?: 'en'}"/>
<div>________________________________________</div>
<div>Global Open Knowledge Base (GOKB)</div>
<p/>
<div>Mail: ${supportAddress}</div>
<div>Website: https://gokb.org/</div>
