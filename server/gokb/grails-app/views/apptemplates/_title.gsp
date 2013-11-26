<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<dl class="dl-horizontal">

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="name">Title</g:annotatedLabel></dt>
    <dd>${d.name} (Modify title through variants below)</dd>
  </div>

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="reasonRetired">Status Reason</g:annotatedLabel></dt>
    <dd><g:xEditableRefData owner="${d}" field="reasonRetired" config='TitleInstance.ReasonRetired' /></dd>
  </div>

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel></dt>
    <dd><g:xEditableRefData owner="${d}" field="status" config='KBComponent.Status' /></dd>
  </div>

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel></dt>
    <dd><g:xEditableRefData owner="${d}" field="editStatus" config='KBComponent.EditStatus' /></dd>
  </div>

</dl>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#titledetails" data-toggle="tab">Title Details</a></li>
    <li><a href="#identifiers" data-toggle="tab">Identifiers</a></li>
    <li><a href="#titlerels" data-toggle="tab">Title Relationships</a></li>
    <li><a href="#addprops" data-toggle="tab">Custom Fields <span class="badge badge-warning">${d.additionalProperties?.size()}</span></a></li>
    <li><a href="#review" data-toggle="tab">Review Tasks <span class="badge badge-warning">${d.reviewRequests?.size()}</span></a></li>
  </ul>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="titledetails">
      
      <g:if test="${d.id != null}">
      
        <dl class="dl-horizontal">
      
          <div class="control-group">
            <dt><g:annotatedLabel owner="${d}" property="medium">Medium</g:annotatedLabel></dt>
            <dd><g:xEditableRefData owner="${d}" field="medium" config='TitleInstance.Medium' /></dd>
          </div>
      
          <div class="control-group">
            <dt><g:annotatedLabel owner="${d}" property="pureOA">pureOA</g:annotatedLabel></dt>
            <dd><g:xEditableRefData owner="${d}" field="pureOA" config='TitleInstance.PureOA' /></dd>
          </div>
      
          <div class="control-group">
            <dt><g:annotatedLabel owner="${d}" property="continuingSeries">Continuing Series</g:annotatedLabel></dt>
            <dd><g:xEditableRefData owner="${d}" field="continuingSeries" config='TitleInstance.ContinuingSeries' /></dd>
          </div>
      
          <div class="control-group">
            <dt><g:annotatedLabel owner="${d}" property="imprint">Imprint</g:annotatedLabel></dt>
            <dd>
              <g:xEditable owner="${d}" field="imprint"/>
            </dd>
          </div>
      
          <div class="control-group">
            <dt><g:annotatedLabel owner="${d}" property="alternateTitles">Alternate Titles</g:annotatedLabel>
            <button class="hidden-license-details btn" data-toggle="collapse" data-target="#collapseableAddTitle"><i class="icon-plus"></i></button></dt>
            <dd>
              <table class="table table-striped table-bordered">
                <thead>
                  <tr>
                    <th>Variant Title</th>
                    <th>Status</th>
                    <th>Variant Type</th>
                    <th>Locale</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <g:each in="${d.variantNames}" var="v">
                    <tr>
                      <td>
                        <g:xEditable owner="${v}" field="variantName"/>
                      </td>
                      <td><g:xEditableRefData owner="${v}" field="status" config='KBComponentVariantName.Status' /></td>
                      <td><g:xEditableRefData owner="${v}" field="variantType" config='KBComponentVariantName.VariantType' /></td>
                      <td><g:xEditableRefData owner="${v}" field="locale" config='KBComponentVariantName.Locale' /></td>
                      <td><g:link controller="workflow" action="AuthorizeVariant" id="${v.id}">Make Authorized</g:link>,
                          <g:link controller="workflow" action="DeleteVariant" id="${v.id}">Delete</g:link></td>
                    </tr>
                  </g:each>
                </tbody>
              </table>
              <dl id="collapseableAddTitle" class="dl-horizontal collapse">
                <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
                  <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
                  <input type="hidden" name="__newObjectClass" value="org.gokb.cred.KBComponentVariantName"/>
                  <input type="hidden" name="__recip" value="owner"/>
                  <dt>Add Title Variant</dt><dd><input type="text" name="variantName"/></dd>
                  <dt>Locale</dt><dd><g:simpleReferenceTypedown name="locale" baseClass="org.gokb.cred.RefdataValue" filter1="KBComponentVariantName.Locale" /></dd>
                  <dt>Variant Type</dt><dd><g:simpleReferenceTypedown name="variantType" baseClass="org.gokb.cred.RefdataValue" filter1="KBComponentVariantName.VariantType" /></dd>
                  <dt></dt><dd><button type="submit" class="btn btn-primary btn-small">Add</button></dd>
                </g:form>
              </dl>
            </dd>
          </div>


            <div class="control-group">
      
              <dt><g:annotatedLabel owner="${d}" property="publishers">Publishers</g:annotatedLabel></dt>
              <dd>
                <table class="table table-striped table-bordered">
                  <thead>
                    <tr>
                      <th>Publisher Name</th>
                      <th>Relationship Status</th>
                      <th>Publisher From</th>
                      <th>Publisher To</th>
                    </tr>
                  </thead>
                  <tbody>
                    <g:each in="${d.getCombosByPropertyName('publisher')}" var="p">
                      <tr>
                        <td><g:link controller="resource" action="show"
                            id="${p.toComponent.class.name}:${p.toComponent.id}">
                            ${p.toComponent.name}
                          </g:link></td>
                        <td>
                          <g:xEditableRefData owner="${p}" field="status" config='Combo.Status' />
                        </td>
                        <td><g:xEditable class="ipe" owner="${p}"
                            field="startDate" type="date" /></td>
                        <td><g:xEditable class="ipe" owner="${p}" field="endDate"
                            type="date" /></td>
                      </tr>
                    </g:each>
                  </tbody>
                </table>
              </dd>
            </div>
      
            <div class="control-group">
              <dt><g:annotatedLabel owner="${d}" property="availability">Availability</g:annotatedLabel></dt>
              <dd>
                <table class="table table-striped table-bordered">
                  <thead>
                    <tr>
                      <th>TIPP</th>
                      <th>Status</th>
                      <th>Package</th>
                      <th>Platform</th>
                      <th>Start</th>
                      <th>End</th>
                      <th>Embargo</th>
                    </tr>
                  </thead>
                  <tbody>
                    <g:each in="${d.tipps}" var="tipp">
                      <tr>
                        <td><g:link controller="resource" action="show"
                            id="${tipp.getClassName()+':'+tipp.id}">
                            ${tipp.id}
                          </g:link></td>
                        <td>
                          ${tipp.status?.value}
                        </td>
                        <td><g:link controller="resource" action="show"
                            id="${tipp.pkg.getClassName()+':'+tipp.pkg.id}">
                            ${tipp.pkg.name}
                          </g:link></td>
                        <td><g:link controller="resource" action="show"
                            id="${tipp.hostPlatform.getClassName()+':'+tipp.hostPlatform.id}">
                            ${tipp.hostPlatform.name}
                          </g:link></td>
                        <td> Date: <g:formatDate
                            format="${session.sessionPreferences?.globalDateFormat}"
                            date="${tipp.startDate}" /></br>
                          Volume: ${tipp.startVolume}</br>
                          Issue: ${tipp.startIssue}
                        </td>
                        <td>Date: <g:formatDate
                            format="${session.sessionPreferences?.globalDateFormat}"
                            date="${tipp.endDate}" /><br/>
                          Volume: ${tipp.endVolume}<br/>
                          Issue: ${tipp.endIssue}
                        </td>
                        <td>
                          ${tipp.embargo}
                        </td>
                      </tr>
                    </g:each>
                  </tbody>
                </table>
              </dd>
            </div>


        </dl>
      </g:if>
    </div>

    <div class="tab-pane" id="identifiers">
      <g:render template="combosByType" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'ids', cols:[[expr:'toComponent.namespace.value',
                                                                   colhead:'Namespace'],
                                                             [expr:'toComponent.value',
                                                                   colhead:'ID',
                                                                   action:'link']], direction:'out']}" />
    </div>

    <div class="tab-pane" id="titlerels">
    </div>

    <div class="tab-pane" id="addprops">
      <g:render template="addprops" contextPath="../apptemplates" model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="revreqtab" contextPath="../apptemplates" model="${[d:d]}" />
    </div>

  </div>
  <g:render template="componentStatus" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
</div>


<script language="JavaScript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
