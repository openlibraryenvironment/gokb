<p/>
  <g:message code="curatoryGroup.alert.daily.greeting"/>
<p/>

<g:message code="curatoryGroup.alert.daily.reviews.intro" locale="${locale ?: 'en'}" args="${[groupName]}" />
<p/>
<table>
  <thead>
    <tr>
      <th>
        <g:message code="curatoryGroup.alert.daily.header.package" locale="${locale ?: 'en'}"/>
      </th>
      <th>
        <g:message code="curatoryGroup.alert.daily.reviews.header.numberOfReviews" locale="${locale ?: 'en'}"/>
      </th>
    </tr>
  <thead>
  <tbody>
    <g:each in="${items}" var="pkg">
      <tr>
        <td>
          <a href="${pkg.editLink}" target="_blank">${pkg.packageName}</a>
        </td>
        <td>
          ${pkg.reviewsTotal}
        </td>
      </tr>
    </g:each>
  </tbody>
</table>
<p/>
<g:message code="curatoryGroup.alert.daily.reviews.info" locale="${locale ?: 'en'}"/>
<p/>
<g:message code="curatoryGroup.alert.daily.reviews.note" locale="${locale ?: 'en'}"/>
<p/>
<g:message code="curatoryGroup.alert.daily.reviews.support" locale="${locale ?: 'en'}"/> <a href="mailto:${supportAddress}">${supportAddress}</a>.

<p/>
<g:message code="curatoryGroup.alert.closing" locale="${locale ?: 'en'}"/>
<g:message code="curatoryGroup.alert.signature" locale="${locale ?: 'en'}"/>
<div>________________________________________</div>
<div>Global Open Knowledge Base (GOKB)</div>
<p/>
<div>Mail: ${supportAddress}</div>
<div>Website: https://gokb.org/</div>
