<p/>
<g:message code="curatoryGroup.alert.cancelledImport.greeting"/>
<p/>
<g:if test="jobs">
  <g:message code="curatoryGroup.alert.daily.jobs.intro" locale="${locale ?: 'en'}"/>
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
      <g:each in="jobs" var="job">
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
  <g:message code="curatoryGroup.alert.daily.reviews.intro" locale="${locale ?: 'en'}"/>
  <g:each in="reviews" var="pkg">
    <p/>
    <h3><a href="${pkg.editLink}">${pkg.packageName}</a></h3>
    </p>
    <g:if test="pkg.reviews">
      <table>
        <thead>
          <tr>
            <th>
              <g:message code="curatoryGroup.alert.daily.reviews.header.type" locale="${locale ?: 'en'}"/>
            </th>
            <th>
              <g:message code="curatoryGroup.alert.daily.reviews.header.dateCreated" locale="${locale ?: 'en'}"/>
            </th>
          </tr>
        <thead>
        <tbody>
          <g:each in="pkg.reviews" var="review">
            <tr>
              <td>
                <g:if test="${review.editLink}">
                  <a href="${review.editLink}">
                    <g:message code="${review.typeDesc}" locale="${locale ?: 'en'}"/>
                  </a>
                </g:if>
                <g:else>
                  <g:message code="${review.typeDesc}" locale="${locale ?: 'en'}"/>
                </g:else>
              </td>
              <td>
                ${review.dateCreated}
              </td>
            </tr>
          </g:each>
        </tbody>
      </table>
    </g:if>
  </g:each>
  <p/>
  <g:message code="curatoryGroup.alert.daily.reviews.support" locale="${locale ?: 'en'}"/> <a href="mailto:${supportAddress}">${supportAddress}</a>.
</g:if>

<br/>
<g:message code="curatoryGroup.alert.closing" locale="${locale ?: 'en'}"/>
<g:message code="curatoryGroup.alert.signature" locale="${locale ?: 'en'}"/>
<div>________________________________________</div>
<div>Global Open Knowledge Base (GOKB)</div>
<p/>
<div>Mail: ${supportAddress}</div>
<div>Website: https://gokb.org/</div>
