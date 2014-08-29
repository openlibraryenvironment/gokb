<g:if test="${d.id != null}">
  <dl class="dl-horizontal">
    <dt><g:annotatedLabel owner="${d}" property="username">User Name</g:annotatedLabel></dt>
    <dd>${d.username}</dd>
  
    <dt><g:annotatedLabel owner="${d}" property="displayName">Display Name</g:annotatedLabel></dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="displayName"/></dd>
  
    <dt><g:annotatedLabel owner="${d}" property="email">Email</g:annotatedLabel></dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="email"/></dd>

    <dt><g:annotatedLabel owner="${d}" property="territories">Territories</g:annotatedLabel></dt>
    <dd>
        <table class="table table-striped table-bordered">
          <thead>
            <tr>
              <th>Territory</th>
            </tr>
          </thead>
          <tbody>
            <g:each in="${d.getCombosByPropertyName('territories')}" var="p">
              <tr>
                <td><g:link controller="resource" action="show" id="${p.toComponent.class.name}:${p.toComponent.id}"> ${p.toComponent.name} </g:link></td>
              </tr>
            </g:each>
          </tbody>
        </table>
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
