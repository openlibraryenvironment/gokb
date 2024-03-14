<p/>
<g:message code="user.activation.email.greeting" locale="${locale ?: 'en'}" args="${[username]}"/>,
<p/>
<g:message code="user.activation.email.line1" locale="${locale ?: 'en'}" args="${[hostname]}"/>
<p/>
<g:message code="user.activation.email.line2" locale="${locale ?: 'en'}"/> <a href="mailto:${supportAddress}">${supportAddress}</a>.
<p>
<g:message code="user.activation.email.closing" locale="${locale ?: 'en'}"/>
<br/>
<g:message code="user.activation.email.signature" locale="${locale ?: 'en'}"/>