<g:if test="${recset != null}">
<g:set var="custom_offset" value="${offset_param?:'offset'}"/>
	<nav class="navbar navbar-inverse">
		<div class="container-fluid">
			<div class="navbar-header">
				<span class="navbar-brand">
					Showing records ${offset.toInteger() +1} to ${lasthit.toInteger() as int} of
					${reccount.toInteger() as int}
				</span>
			</div>
			<ul class="nav navbar-nav navbar-right">
				<g:if test="${ !request.isAjax() }">

<!-- see grails-app/assets/javascripts/gokb/action-forms.js for code relating to bulk actions -->
				<g:if test="${!hideActions}">
					<li class="dropdown"><a href="#" class="dropdown-toggle"
							data-toggle="dropdown">Actions <b class="caret"></b></a>
							<ul class="dropdown-menu actions"></ul>
					</li>
					<li class="divider-vertical"></li>
				</g:if>
				<li class="dropdown page-jump${ dropup ? ' dropup' : ''}"><a
						href="#" class="dropdown-toggle" data-toggle="dropdown">Page
							${page} of ${page_max} <span class="caret"></span></a>
					<ul class="dropdown-menu dropdown-menu-scroll" role="menu">
						<g:each var="p" in="${ 1..page_max }">
							<li><g:link title="Previous Page" controller="search"
									action="index"
									params="${params+["${custom_offset}":((p.toInteger() - 1) * max.toInteger()),det:null]}">
								Page ${p}
								</g:link></li>
						</g:each>
					</ul>
				</li>
				</g:if>
				<g:if test="${ page == 1 }">
					<li class='disabled'><a class='disabled' href='#'><i
							class="glyphicon glyphicon-chevron-left"></i></a></li>
				</g:if>
				<g:else>
					<li><g:link title="Previous Page" controller="search"
							action="index"
							params="${params+["${custom_offset}":(offset.toInteger() - max.toInteger()),det:null]}">
							<i class="glyphicon glyphicon-chevron-left"></i>
						</g:link></li>
				</g:else>
				<g:if test="${ page == page_max }">
					<li class='disabled'><a href='#'><i
							class="glyphicon glyphicon-chevron-right"></i></a></li>
				</g:if>
				<g:else>
					<li><g:link title="Next Page" controller="search"
							action="index"
							params="${params+["${custom_offset}":(offset.toInteger() + max.toInteger()),det:null]}">
							<i class="glyphicon glyphicon-chevron-right"></i>
						</g:link></li>
				</g:else>
			</ul>
		</div>
	</nav>
</g:if>