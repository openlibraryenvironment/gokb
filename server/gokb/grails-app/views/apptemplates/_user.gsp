<g:if test="${d.id != null}">
  <dl class="dl-horizontal">
    <dt><g:annotatedLabel owner="${d}" property="username">User Name</g:annotatedLabel></dt>
    <dd>${d.username}</dd>
  
    <dt><g:annotatedLabel owner="${d}" property="displayName">Display Name</g:annotatedLabel></dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="displayName"/></dd>
  
    <dt><g:annotatedLabel owner="${d}" property="email">Email</g:annotatedLabel></dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="email"/></dd>
    
    <dt><g:annotatedLabel owner="${d}" property="curatoryGroups">Curatory Groups</g:annotatedLabel></dt>
    <dd>
       <g:render template="curatory_groups" contextPath="../apptemplates" model="${[d:d]}" />
    </dd>

    <dt><g:annotatedLabel owner="${d}" property="org">Home Org</g:annotatedLabel></dt>
    <dd>
      <g:manyToOneReferenceTypedown owner="${d}" field="org"
                                baseClass="org.gokb.cred.Org">
                                ${d.org?.name}
                        </g:manyToOneReferenceTypedown>
    </dd>

    <dt><g:annotatedLabel owner="${d}" property="last_alert_check">Last Alert Check</g:annotatedLabel></dt>
    <dd>
      <g:if test="${d.last_alert_check}"><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${d.last_alert_check}" /></g:if>
      <g:else>Never</g:else>
    </dd>

  </dl>
  <div id="content">
    <ul id="tabs" class="nav nav-tabs">
      <li class="active"><a href="#roles" data-toggle="tab">Roles</a></li>
    </ul>
    <div id="my-tab-content" class="tab-content">
      <div class="tab-pane active" id="roles">
        <g:link class="display-inline" controller="security" action="roles" params="${['id': (d.class.name + ':' + d.id) ]}"></g:link>
      </div>
    </div>
  </div>
</g:if>
