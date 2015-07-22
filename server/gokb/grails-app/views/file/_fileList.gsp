<ol>
	<g:each in="${locations}" var="location">
		<li>
			<a class="showReportLink" href="${createLink(action: 'index', params: [filePath: location])}">${location}</a>
		</li>
	</g:each>
</ol>

