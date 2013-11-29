<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h3>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h3>

<div id="content">

  <dl class="dl-horizontal">
      <dt><g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="name"/></dd>
      <dt><g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel></dt>
      <dd><g:xEditableRefData owner="${d}" field="status" config="KBComponent.Status" /></dd>
      <dt><g:annotatedLabel owner="${d}" property="reference">Reference</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="reference"/></dd>
      <dt><g:annotatedLabel owner="${d}" property="shortCode">Short Code</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="shortcode"/></dd>
      <dt><g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel></dt>
      <dd><g:manyToOneReferenceTypedown owner="${d}" field="source" baseClass="org.gokb.cred.Source"/></dd>
  </dl>

  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#orgdetails" data-toggle="tab">Organisation</a></li>
    <li><a href="#altnames" data-toggle="tab">Alternate Names <span class="badge badge-warning">${d.variantNames?.size()}</span></a></li>
    <li><a href="#ids" data-toggle="tab">IDs <span class="badge badge-warning">${d.ids?.size()}</span></a></li>
    <li><a href="#lists" data-toggle="tab">Lists</a></li>
    <li><a href="#addprops" data-toggle="tab">Custom Fields <span class="badge badge-warning">${d.additionalProperties?.size()}</span></a></li>
    <li><a href="#review" data-toggle="tab">Review Tasks <span class="badge badge-warning">${d.reviewRequests?.size()}</span></a></li>
    <li><a href="#offices" data-toggle="tab">Offices <span class="badge badge-warning">${d.offices?.size()}</span></a></li>
  </ul>

  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="orgdetails">
      <g:if test="${d.id != null}">
        <dl class="dl-horizontal">
            <dt><g:annotatedLabel owner="${d}" property="mission">Mission</g:annotatedLabel></dt>
            <dd><g:xEditableRefData owner="${d}" field="mission" config='Org.Mission' /></dd>
            <dt><g:annotatedLabel owner="${d}" property="roles">Roles</g:annotatedLabel></dt>
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
            <dt><g:annotatedLabel owner="${d}" property="tags">Tags</g:annotatedLabel></dt>
            <dd>
              <table class="table table-striped table-bordered">
                <thead>
                  <tr>
                    <th>Tag Category</th>
                    <th>Tag Value</th>
                  </tr>
                </thead>
                <tbody>
                  <g:each in="${d.tags}" var="t">
                    <tr>
                      <td>${t.owner.desc}</td>
                      <td>${t.value}</td>
                    </tr>
                  </g:each>
                </tbody>
              </table>
            </dd>
        </dl>
      </g:if>
    </div>
    <div class="tab-pane" id="altnames">
      <g:if test="${d.id != null}">
        <dl class="dl-horizontal">
            <dt><g:annotatedLabel owner="${d}" property="alternateNames">Alternate Names</g:annotatedLabel></dt>
            <dd>
              <table class="table table-striped table-bordered">
                <thead>
                  <tr>
                    <th>Alternate Name</th>
                    <th>Status</th>
                    <th>Variant Type</th>
                    <th>Locale</th>
                  </tr>
                </thead>
                <tbody>
                  <g:each in="${d.variantNames}" var="v">
                    <tr>
                      <td>
                        ${v.variantName}
                      </td>
                      <td><g:xEditableRefData owner="${v}" field="status" config='KBComponentVariantName.Status' /></td>
                      <td><g:xEditableRefData owner="${v}" field="variantType" config='KBComponentVariantName.VariantType' /></td>
                      <td><g:xEditableRefData owner="${v}" field="locale" config='KBComponentVariantName.Locale' /></td>
                    </tr>
                  </g:each>
                </tbody>
              </table>

              <h4><g:annotatedLabel owner="${d}" property="addVariantName">Add Variant Name</g:annotatedLabel></h4>
              <dl class="dl-horizontal">
                <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
                  <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
                  <input type="hidden" name="__newObjectClass" value="org.gokb.cred.KBComponentVariantName"/>
                  <input type="hidden" name="__recip" value="owner"/>
                  <dt>Variant Name</dt><dd><input type="text" name="variantName"/></dd>
                  <dt>Locale</dt><dd><g:simpleReferenceTypedown name="locale" baseClass="org.gokb.cred.RefdataValue" filter1="KBComponentVariantName.Locale" /></dd>
                  <dt>Variant Type</dt><dd><g:simpleReferenceTypedown name="variantType" baseClass="org.gokb.cred.RefdataValue" filter1="KBComponentVariantName.VariantType" /></dd>
                  <dt></dt><dd><button type="submit" class="btn btn-primary btn-small">Add</button></dd>
                </g:form>
              </dl>

            </dd>
        </dl>
      </g:if>
    </div>
    <div class="tab-pane" id="ids">
      <g:if test="${d.id != null}">
        <dl>
            <dt><g:annotatedLabel owner="${d}" property="ids">IDs</g:annotatedLabel></dt>
            <dd>
              <g:render template="comboList" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'ids', cols:[[expr:'namespace.value',colhead:'Namespace'],[expr:'value',colhead:'Identifier']]]}" />
            </dd>

          <g:if test="${d.parent != null}">
              <dt><g:annotatedLabel owner="${d}" property="parent">Parent</g:annotatedLabel></dt>
              <dd>
                <g:link controller="resource" action="show"
                  id="${d.parent.getClassName()+':'+d.parent.id}">
                  ${d.parent.name}
                </g:link>
              </dd>
          </g:if>
      
          <g:if test="${d.children?.size() > 0}">
            <dt><g:annotatedLabel owner="${d}" property="children">Children</g:annotatedLabel></dt>
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

    <div class="tab-pane" id="addprops">
      <g:render template="addprops" contextPath="../apptemplates" model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="revreqtab" contextPath="../apptemplates" model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="offices">
        <dl class="dl-horizontal">
            <dt><g:annotatedLabel owner="${d}" property="offices">Offices</g:annotatedLabel></dt>
            <dd class="well">
              <g:render template="comboList" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'offices', cols:[[expr:'name',colhead:'Office Name']],targetClass:'org.gokb.cred.Office',direction:'in']}" />
            </dd>
        </dl>
    </div>

    <div class="tab-pane" id="lists">
        <dl class="dl-horizontal">
            <dt><g:annotatedLabel owner="${d}" property="licenses">Licenses</g:annotatedLabel></dt>
            <dd class="well">
              <g:render template="comboList" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'heldLicenses', cols:[[expr:'name',colhead:'License Name']],targetClass:'org.gokb.cred.License']}" />
            </dd>
            <dt><g:annotatedLabel owner="${d}" property="platforms">Platforms</g:annotatedLabel></dt>
            <dd>
              <g:render template="comboList" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'providedPlatforms', cols:[[expr:'name',colhead:'Platform Name',targetClass:'org.gokb.cred.Platform']]]}" />
            </dd>
            <dt><g:annotatedLabel owner="${d}" property="titles">Titles</g:annotatedLabel></dt>
            <dd>
              <g:render template="combosByType" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'publishedTitles', cols:[[expr:'fromComponent.name',
                                                                          colhead:'Title Name',
                                                                          action:'link']], direction:'in']}" />
            </dd>
            <dt><g:annotatedLabel owner="${d}" property="packages">Packages</g:annotatedLabel></dt>
            <dd>
              <g:render template="comboList" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'providedPackages', cols:[[expr:'name',colhead:'Package Name']],targetClass:'org.gokb.cred.Package']}" />
            </dd>

        </dl>
    </div>


  </div>
  <g:render template="componentStatus" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
</div>
<script type="text/javascript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
