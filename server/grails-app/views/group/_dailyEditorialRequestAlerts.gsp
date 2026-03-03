<p/>
<g:message code="curatoryGroup.alert.daily.greeting"/>
<p/>

<g:message code="curatoryGroup.alert.daily.externalRequest.intro" locale="${locale ?: 'en'}" args="${[groupName]}" />

<p/>

<g:each in="${items}" var="review">
  <a href="review.editLink" target="_blank"> ${review.editLink} </a> <br/>
</g:each>

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
