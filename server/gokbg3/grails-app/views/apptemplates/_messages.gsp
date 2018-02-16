<div id="msg" style="display: none;">
	<g:set var="errors" value="${ flash.error }" />
	<g:if test="${ errors }">
		<div class="text-error">
			<g:if test="${ preMessage }">
				<p>
					${ preMessage }
				</p>
			</g:if>
			<g:if test="${ errors instanceof String }">
				<p class='error'>
					${ errors }
				</p>
			</g:if>
			<g:elseif test="${ errors instanceof Collection }">
				<ul>
					<g:each var="error" in="${ errors }">
						<li><g:message error="${ error }" /></li>
					</g:each>
				</ul>
			</g:elseif>
		</div>
	</g:if>
</div>
