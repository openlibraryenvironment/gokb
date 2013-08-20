<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h1>

<div id="content">

  <dl class="dl-horizontal">

    <div class="control-group">
      <dt>Name</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="name"/></dd>
    </div>

    <div class="control-group">
      <dt>Status</dt>
      <dd><g:xEditableRefData owner="${d}" field="status" config="KBComponent.Status" /></dd>
    </div>

    <div class="control-group">
      <dt>Internal ID</dt>
      <dd>${d.id}</dd>
    </div>

    <div class="control-group">
      <dt>Reference</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="reference"/></dd>
    </div>

    <div class="control-group">
      <dt>Short Code</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="shortcode"/></dd>
    </div>

  </dl>

  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#orgdetails" data-toggle="tab">Organisation</a></li>
    <li><a href="#lists" data-toggle="tab">Lists</a></li>
    <li><a href="#status" data-toggle="tab">Status</a></li>
  </ul>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="orgdetails">
      <g:if test="${d.id != null}">
        <dl class="dl-horizontal">
      
          <div class="control-group">
            <dt>Mission</dt>
            <dd><g:xEditableRefData owner="${d}" field="mission" config='Org.Mission' /></dd>
          </div>
      
      
          <div class="control-group">
            <dt>Roles</dt>
            <dd>
              <g:if test="${d.id != null}">
                <ul>
                  <g:each in="${d.roles?.sort({"${it.value}"})}" var="t">
                    <li>${t.value}</li>
                  </g:each>
                </ul>
                <br/>
      
                <g:form controller="ajaxSupport" action="addToStdCollection" class="form-inline">
                  <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
                  <input type="hidden" name="__property" value="roles"/>
                  <g:simpleReferenceTypedown name="__relatedObject" baseClass="org.gokb.cred.RefdataValue" filter1="Org.Role" />
                  <input type="submit" value="Add..." class="btn btn-primary btn-small"/>
                </g:form>
              </g:if>
              <g:else>
                Record must be saved before roles can be edited.
              </g:else>
            </dd>
          </div>
      
          <div class="control-group">
            <dt>IDs</dt>
            <dd>
              <g:render template="comboList" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'ids', cols:[[expr:'namespace.value',colhead:'Namespace'],[expr:'value',colhead:'Identifier']]]}" />
            </dd>
          </div>

          <g:if test="${d.parent != null}">
            <div class="control-group">
              <dt>Parent</dt>
              <dd>
                <g:link controller="resource" action="show"
                  id="${d.parent.getClassName()+':'+d.parent.id}">
                  ${d.parent.name}
                </g:link>
              </dd>
            </div>
          </g:if>
      
          <g:if test="${d.children?.size() > 0}">
            <dt>Children</dt>
            <dd>
              <ul>
                <g:each in="${d.children}" var="c">
                  <li><g:link controller="resource" action="show"
                      id="${c.getClassName()+':'+c.id}">
                      ${c.name}
                    </g:link></li>
                </g:each>
              </ul>
            </dd>
          </g:if>
        </dl>
      </g:if>
    </div>

    <div class="tab-pane" id="lists">
        <dl class="dl-horizontal">

          <div class="control-group">
            <dt>Offices</dt>
            <dd class="well">
              <g:render template="comboList" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'offices', cols:[[expr:'name',colhead:'Office Name']],targetClass:'org.gokb.cred.Office']}" />
            </dd>
          </div>

         <div class="control-group">
            <dt>Licenses</dt>
            <dd>
              <g:render template="comboList" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'heldLicenses', cols:[[expr:'name',colhead:'License Name']],targetClass:'org.gokb.cred.License']}" />
            </dd>
          </div>

         <div class="control-group">
            <dt>Platforms</dt>
            <dd>
              <g:render template="comboList" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'providedPlatforms', cols:[[expr:'name',colhead:'Platform Name',targetClass:'org.gokb.cred.Platform']]]}" />
            </dd>
          </div>

         <div class="control-group">
            <dt>Titles</dt>
            <dd>
              <g:render template="combosByType" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'publishedTitles', cols:[[expr:'fromComponent.name',
                                                                          colhead:'Title Name',
                                                                          action:'link']], direction:'in']}" />
            </dd>
          </div>

         <div class="control-group">
            <dt>Packages</dt>
            <dd>
              <g:render template="comboList" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'providedPackages', cols:[[expr:'name',colhead:'Package Name']],targetClass:'org.gokb.cred.Package']}" />
            </dd>
          </div>

         <div class="control-group">
            <dt>Identifiers</dt>
            <dd>
              <g:render template="combosByType" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'ids', cols:[[expr:'toComponent.namespace.value',
                                                                   colhead:'Namespace'],
                                                             [expr:'toComponent.value',
                                                                   colhead:'ID',
                                                                   action:'link']], direction:'in']}" />
            </dd>
          </div>

        </dl>
    </div>

    <div class="tab-pane" id="status">
      <g:render template="componentStatus" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
    </div>

  </div>
</div>



<script language="JavaScript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
