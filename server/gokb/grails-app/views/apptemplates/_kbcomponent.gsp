<h1>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h1>

<dl class="dl-horizontal">

  <div class="control-group">
    <dt>Internal Id</dt>
    <dd>
      ${d.id?:'New Record'}
    </dd>
  </div>

  <div class="control-group">
    <dt>Reference</dt>
    <dd>
      <g:xEditable class="ipe" owner="${d}" field="reference"/>
    </dd>
  </div>

  <div class="control-group">
    <dt>Provenance</dt>
    <dd>
      <g:xEditable class="ipe" owner="${d}" field="provenance"/>
    </dd>
  </div>

  <div class="control-group">
    <dt>Shortcode</dt>
    <dd>
      <g:xEditable class="ipe" owner="${d}" field="shortcode"/>
    </dd>
  </div>


  <g:if test="${ d.ids?.size() > 0 }">
    <div class="control-group">
      <dt>Identifiers</dt>
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
      <dt>${ d.getNiceName() } Name</dt>
      <dd>
        <g:xEditable class="ipe" owner="${d}" field="name">
          ${d.name}
        </g:xEditable>
      </dd>
    </div>
  </g:if>
  
  <g:if test="${d.id != null}">
    <g:if test="${ d.tags?.size() > 0 }">
      <div class="control-group">
        <dt>Tags</dt>
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

  <div class="control-group">
    <dt>Date Created</dt>
    <dd>
      ${d?.dateCreated?:'&nbsp;'}
    </dd>
  </div>

  <div class="control-group">
    <dt>Date Created</dt>
    <dd>
      ${d?.lastUpdated?:'&nbsp;'}
    </dd>
  </div>

  <div class="control-group">
    <dt>Last updated by</dt>
    <dd>
      ${d?.lastUpdatedBy?.displayName?:'&nbsp;'}
    </dd>
  </div>



</dl>
