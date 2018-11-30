<g:if test="${recset != null}">
<g:set var="s_action" value="${s_action?:'index'}"/>
<g:set var="s_controller" value="${s_controller?:'search'}"/>
<g:set var="jumpToPage" value="${jumpToPage?:'jumpToPage'}"/>
<g:set var="custom_offset" value="${offset_param?:'offset'}"/>
  <nav class="navbar navbar-inverse">
    <div class="container-fluid">
      <div class="navbar-header">
        <span class="navbar-text">
          Showing records ${offset.toInteger() +1} to ${lasthit.toInteger() as int} of
          ${reccount.toInteger() as int}
        </span>
      </div>
      <ul class="nav navbar-nav navbar-right">
        <g:if test="${ !request.isAjax() }">

          <!-- see grails-app/assets/javascripts/gokb/action-forms.js for code relating to bulk actions -->
          <g:if test="${!hideActions}">
            <li class="dropdown"><a href="#" class="dropdown-toggle" data-toggle="dropdown">Actions <b class="caret"></b></a>
              <ul class="dropdown-menu actions"></ul>
            </li>
            <li class="divider-vertical"></li>
          </g:if>

          <li><span class="navbar-text search-page-index"><g:form controller="${s_controller}" action="${s_action}" params="${withoutJump}" method="post">Page <input type="text" class="search-page-index-input" name="${jumpToPage}" size="5" value="${page}" style="color:#000000;" /> of ${page_max}</g:form></span></li>
        </g:if>

        <g:if test="${ page == 1 }">
          <li class='disabled'><a class='disabled' href='#'><i
              class="fas fa-chevron-left"></i></a></li>
        </g:if>
        <g:else>
          <li><g:link title="Previous Page" controller="${s_controller}"
              action="${s_action}"
              params="${params+["${custom_offset}":(offset.toInteger() - max.toInteger()),det:null]}">
              <i class="fas fa-chevron-left"></i>
            </g:link></li>
        </g:else>
        <g:if test="${ page == page_max }">
          <li class='disabled'><a href='#'>
          <i class="fas fa-chevron-right"></i></a></li>
        </g:if>
        <g:else>
          <li><g:link title="Next Page" controller="${s_controller}"
              action="${s_action}"
              params="${params+["${custom_offset}":(offset.toInteger() + max.toInteger()),det:null]}">
              <i class="fas fa-chevron-right"></i>
            </g:link></li>
        </g:else>
      </ul>
    </div>
  </nav>
</g:if>
