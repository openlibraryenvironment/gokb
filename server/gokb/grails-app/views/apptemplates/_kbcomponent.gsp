<dl class="dl-horizontal">

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="id">GoKB Internal Id</g:annotatedLabel></dt>
    <dd>
      ${d.id?:'New Record'}
    </dd>
  </div>

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="shortcode">GoKB Shortcode</g:annotatedLabel></dt> 
    <dd>
      <g:xEditable class="ipe" owner="${d}" field="shortcode"/>
    </dd>
  </div>

  <g:if test="${ d.ids?.size() > 0 }">
    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="identifiers">Identifiers</g:annotatedLabel></dt>
      <dd>
        <ul>
          <g:each in="${d.ids}" var="id">
            <li>
              ${id.namespace.value}:${id.value}
            </li>
          </g:each>
        </ul>
      </dd>
    </div>
  </g:if>
  <g:if test="${!d.id || (d.id && d.name)}" >
    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="name">${ d.getNiceName() } Name</g:annotatedLabel></dt>
      <dd>
        <g:xEditable class="ipe" owner="${d}" field="name"/>
      </dd>
    </div>
  </g:if>
  
  <g:if test="${d.id != null}">
    <g:if test="${ d.tags?.size() > 0 }">
      <div class="control-group">
        <dt><g:annotatedLabel owner="${d}" property="tags">Tags</g:annotatedLabel></dt>
        <dd>
          &nbsp;
          <ul>
            <g:each in="${d.tags}" var="t">
              <li>
                ${t.value}
              </li>
            </g:each>
          </ul>
        </dd>
      </div>
    </g:if>

    <g:render template="refdataprops" contextPath="../apptemplates" model="${[d:(d), rd:(rd), dtype:(dtype)]}"/>

  </g:if>

</dl>
