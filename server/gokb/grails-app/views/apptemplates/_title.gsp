<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h1>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#titledetails" data-toggle="tab">Title Details</a></li>
    <li><a href="#lists" data-toggle="tab">Lists</a></li>
    <li><a href="#titlerels" data-toggle="tab">Title Relationships</a></li>
    <li><a href="#addprops" data-toggle="tab">Additional Properties</a></li>
    <li><a href="#header" data-toggle="tab">Header</a></li>
    <li><a href="#status" data-toggle="tab">Status</a></li>
  </ul>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="titledetails">
      
      <g:if test="${d.id != null}">
      
        <dl class="dl-horizontal">
      
          <div class="control-group">
            <dt>Title</dt>
            <dd><g:xEditable owner="${d}" field="name" /></dd>
          </div>

          <div class="control-group">
            <dt>Medium</dt>
            <dd><g:xEditableRefData owner="${d}" field="medium" config='TitleInstance.Medium' /></dd>
          </div>
      
          <div class="control-group">
            <dt>pureOA</dt>
            <dd><g:xEditableRefData owner="${d}" field="pureOA" config='TitleInstance.PureOA' /></dd>
          </div>
      
          <div class="control-group">
            <dt>Reason Retired</dt>
            <dd><g:xEditableRefData owner="${d}" field="reasonRetired" config='TitleInstance.ReasonRetired' /></dd>
          </div>
      
          <div class="control-group">
            <dt>Imprint</dt>
            <dd>
              <g:xEditable owner="${d}" field="imprint"/>
            </dd>
          </div>
      
          <g:if test="${d.variantNames}">
            <div class="control-group">
              <dt>Alternate Titles</dt>
              <dd>
                <table class="table table-striped table-bordered">
                  <thead>
                    <tr>
                      <th>Variant Title</th>
                      <th>Status</th>
                      <th>Variant Type</th>
                      <th>Locale</th>
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
                      </tr>
                    </g:each>
                  </tbody>
                </table>
              </dd>
            </div>
          </g:if>
          <g:if test="${d.publisher}">
            <div class="control-group">
      
              <dt>Publishers</dt>
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
      
          </g:if>
          <g:if test="${d.tipps}">
            <div class="control-group">
              <dt>Availability</dt>
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
          </g:if>
        </dl>
      </g:if>
    </div>

    <div class="tab-pane" id="lists">
        <div class="control-group">
            <dt>Identifiers</dt>
            <dd>
              <g:render template="combosByType" 
                        contextPath="../apptemplates" 
                        model="${[d:d, property:'ids', cols:[[expr:'toComponent.namespace.value',
                                                                   colhead:'Namespace'],
                                                             [expr:'toComponent.value',
                                                                   colhead:'ID',
                                                                   action:'link']], direction:'out']}" />
            </dd>
          </div>
    </div>

    <div class="tab-pane" id="titlerels">
    </div>

    <div class="tab-pane" id="addprops">
      <g:render template="addprops" contextPath="../apptemplates" model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="header">
      <g:render template="kbcomponent" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
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
