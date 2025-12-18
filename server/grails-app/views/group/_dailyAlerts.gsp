<p/>
<g:message code="curatoryGroup.alert.daily.greeting"/>
<p/>
<g:if test="jobs">
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
      <g:each in="${jobs}" var="job">
        <tr>
          <td>
            <a href="${job.editLink}">${job.packageName}</a>
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
</g:if>
<g:if test="reviews">
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
      <g:each in="${reviews}" var="pkg">
        <tr>
          <td>
            <a href="${pkg.editLink}">${pkg.packageName}</a>
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
</g:if>

<p/>
<g:message code="curatoryGroup.alert.closing" locale="${locale ?: 'en'}"/>
<g:message code="curatoryGroup.alert.signature" locale="${locale ?: 'en'}"/>
<div>________________________________________</div>
<div>Global Open Knowledge Base (GOKB)</div>
<p/>
<div>Mail: ${supportAddress}</div>
<div>Website: https://gokb.org/</div>
