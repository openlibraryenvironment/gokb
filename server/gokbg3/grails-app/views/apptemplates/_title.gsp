<dl class="dl-horizontal">
  <dt>
    <g:annotatedLabel owner="${d}" property="name">Title</g:annotatedLabel>
  </dt>
  <dd style="max-width:60%">
    <g:if test="${displayobj?.id != null}">
      <div>
        ${d.name}<br/>
        <span style="white-space:nowrap;">(Modify title through <i>Alternate Names</i> below)</span>
      </div>
    </g:if>
    <g:else>
      <g:xEditable class="ipe" owner="${d}" field="name"/>
    </g:else>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel>
  </dt>
  <dd>
    <g:manyToOneReferenceTypedown owner="${d}" field="source"
                                  baseClass="org.gokb.cred.Source">${d.source?.name}</g:manyToOneReferenceTypedown>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
  </dt>
  <dd>
    <sec:ifAnyGranted roles="ROLE_SUPERUSER">
      <g:xEditableRefData owner="${d}" field="status" config='KBComponent.Status'/>
    </sec:ifAnyGranted>
    <sec:ifNotGranted roles="ROLE_SUPERUSER">
      ${d.status?.value ?: 'Not Set'}
    </sec:ifNotGranted>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="reasonRetired">Status Reason</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="reasonRetired"
                        config='TitleInstance.ReasonRetired'/>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="editStatus"
                        config='KBComponent.EditStatus'/>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="language">Language</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="language" config="${org.gokb.cred.KBComponent.RD_LANGUAGE}"/>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="currentPubisher">Latest Publisher</g:annotatedLabel>
  </dt>
  <dd>
    ${d.currentPublisher}&nbsp;
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="imprint">Imprint</g:annotatedLabel>
  </dt>
  <dd>
    <g:manyToOneReferenceTypedown owner="${d}" field="imprint"
                                  baseClass="org.gokb.cred.Imprint">
      ${d.imprint?.name}
    </g:manyToOneReferenceTypedown>
    &nbsp;
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="publishedFrom">Published From</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" type="date" field="publishedFrom"/>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="publishedTo">Published To</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" type="date" field="publishedTo"/>
  </dd>

  <g:if test="${d?.id != null && d.titleHistory}">
    <dt>
      <g:annotatedLabel owner="${d}" property="titleHistory">Title History</g:annotatedLabel>
    </dt>
    <dd>
      <g:render template="/apptemplates/fullth" model="${[d: d]}"/>
    </dd>
  </g:if>
</dl>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#titledetails" data-toggle="tab">Title Details</a></li>
    <li><a href="#altnames" data-toggle="tab">Alternate Names <span
        class="badge badge-warning">${d.variantNames?.size() ?: '0'}</span></a></li>

    <g:if test="${d.isEditable()}">
      <li><a href="#history" data-toggle="tab">Add to Title History</a></li>
    </g:if>
    <li><a href="#identifiers" data-toggle="tab">Identifiers <span
        class="badge badge-warning">${d?.getCombosByPropertyNameAndStatus('ids', 'Active')?.size() ?: '0'}</span></a>
    </li>
    <li><a href="#publishers" data-toggle="tab">Publishers <span
        class="badge badge-warning">
      ${d.getCombosByPropertyNameAndStatus('publisher', params.publisher_status)?.size() ?: '0'}
    </span></a></li>
    <li><a href="#availability" data-toggle="tab">Package Availability <span
        class="badge badge-warning">
      ${d?.tipps?.findAll { it.status?.value == 'Current' }?.size() ?: '0'} (${d.tipps?.size() ?: '0'})
    </span></a></li>
    <li><a href="#addprops" data-toggle="tab">Custom Fields <span
        class="badge badge-warning">
      ${d.additionalProperties?.size() ?: '0'}
    </span></a></li>
    <li><a href="#review" data-toggle="tab">Review Tasks <span
        class="badge badge-warning">${d.reviewRequests?.size() ?: '0'}</span></a></li>
  </ul>

  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="titledetails">

      <g:if test="${d.id != null}">

        <dl class="dl-horizontal">

          <dt>
            <g:annotatedLabel owner="${d}" property="medium">Medium</g:annotatedLabel>
          </dt>
          <dd>
            <g:xEditableRefData owner="${d}" field="medium"
                                config='TitleInstance.Medium'/>
          </dd>

          <dt>
            <g:annotatedLabel owner="${d}" property="OAStatus">OA Status</g:annotatedLabel>
          </dt>
          <dd>
            <g:xEditableRefData owner="${d}" field="OAStatus"
                                config='TitleInstance.OAStatus'/>
          </dd>

          <dt>
            <g:annotatedLabel owner="${d}" property="continuingSeries">Continuing Series</g:annotatedLabel>
          </dt>
          <dd>
            <g:xEditableRefData owner="${d}" field="continuingSeries"
                                config='TitleInstance.ContinuingSeries'/>
          </dd>
        </dl>
      </g:if>
    </div>

    <g:render template="/tabTemplates/showVariantnames" model="${[d: displayobj, showActions: true]}"/>

    <div class="tab-pane" id="history">
      <g:if test="${d.id != null}">
        <dl class="dl-horizontal">
          <g:form name="AddHistoryForm" controller="workflow"
                  action="createTitleHistoryEvent">
            <dt>
              Titles
            </dt>
            <dd>
              <table>
                <tr>
                  <th>Before</th>
                  <th></th>
                  <th>After</th>
                </tr>
                <tr>
                  <td><select name="beforeTitles" size="5" multiple
                              class="input-xxlarge" style="width: 500px;">
                    <option value="org.gokb.cred.TitleInstance:${d.id}">
                      ${d.name}
                    </option>
                  </select><br/></td>
                  <td style="text-align:center;">
                    <button class="btn btn-sm" style="margin: 2px 5px;" type="button"
                            onClick="SelectMoveRows(document.AddHistoryForm.beforeTitles, document.AddHistoryForm.afterTitles)">&gt;</button>

                    <div style="height:2px;"></div>
                    <button class="btn btn-sm" style="margin: 2px 5px;" type="button"
                            onClick="SelectMoveRows(document.AddHistoryForm.afterTitles, document.AddHistoryForm.beforeTitles)">&lt;</button>
                  </td>
                  <td><select name="afterTitles" size="5" multiple="multiple"
                              class="input-xxlarge" style="width: 500px;"></select></td>
                </tr>
                <tr>
                  <td><g:simpleReferenceTypedown class="form-control" name="fromTitle"
                                                 baseClass="org.gokb.cred.TitleInstance"/> <br/>
                    <button class="btn btn-sm" type="button"
                            onClick="AddTitle(document.AddHistoryForm.fromTitle, document.AddHistoryForm.beforeTitles)">Add</button>
                    <button class="btn btn-sm" type="button" onClick="removeTitle('beforeTitles')">Remove</button></td>
                  <td></td>
                  <td><g:simpleReferenceTypedown class="form-control" name="ToTitle"
                                                 baseClass="org.gokb.cred.TitleInstance"/> <br/>
                    <button class="btn btn-sm" type="button"
                            onClick="AddTitle(document.AddHistoryForm.ToTitle, document.AddHistoryForm.afterTitles)">Add</button>
                    <button class="btn btn-sm" type="button" onClick="removeTitle('afterTitles')">Remove</button></td>
                </tr>
              </table>
            </dd>
            <dt class="dt-label">Event Date</dt>
            <dd>
              <input type="date" class="form-control" name="EventDate"/>
            </dd>
            <dt></dt>
            <dd>
              <button class="btn btn-default btn-primary"
                      onClick="submitTitleHistoryEvent(document.AddHistoryForm.beforeTitles, document.AddHistoryForm.afterTitles)">Add
              Title History Event</button>
            </dd>
          </g:form>
        </dl>
      </g:if>
    </div>

    <div class="tab-pane" id="availability">
      <dt>
        <g:annotatedLabel owner="${d}" property="availability">Package Availability</g:annotatedLabel>
      </dt>
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
              <td><g:if test="${tipp != null}"><g:link controller="resource" action="show"
                                                       id="${tipp?.getClassName() + ':' + tipp.id}">
                ${tipp.id}
              </g:link></g:if><g:else>ERROR</g:else></td>
              <td>
                ${tipp.status?.value}
              </td>
              <td><g:if test="${tipp.pkg != null}"><g:link controller="resource" action="show"
                                                           id="${tipp.pkg?.getClassName() + ':' + tipp.pkg.id}">
                ${tipp.pkg.name}
              </g:link></g:if><g:else>ERROR</g:else></td>
              <td><g:if test="${tipp.hostPlatform != null}"><g:link controller="resource" action="show"
                                                                    id="${tipp.hostPlatform?.getClassName() + ':' + tipp.hostPlatform.id}">
                ${tipp.hostPlatform.name}
              </g:link></g:if><g:else>ERROR: hostPlatform is null</g:else></td>
              <td>Date: <g:formatDate
                  format="${session.sessionPreferences?.globalDateFormat}"
                  date="${tipp.startDate}"/><br/> Volume: ${tipp.startVolume}<br/>
                Issue: ${tipp.startIssue}
              </td>
              <td>Date: <g:formatDate
                  format="${session.sessionPreferences?.globalDateFormat}"
                  date="${tipp.endDate}"/><br/> Volume: ${tipp.endVolume}<br/>
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

    <div class="tab-pane" id="publishers">

      <dt>
        <g:annotatedLabel owner="${d}" property="publishers">Publishers</g:annotatedLabel>
      </dt>

      <dd>
        <div>
          <g:form method="POST" controller="${controllerName}" action="${actionName}" fragment="publishers"
                  params="${params.findAll { k, v -> k != 'publisher_status' }}">

            Hide Deleted : <g:select name="publisher_status" optionKey="key" optionValue="value"
                                     from="${[null: 'Off', 'Active': 'On']}" value="${params.publisher_status}"/>
          </g:form>
        </div>
        <table class="table table-striped table-bordered">
          <thead>
          <tr>
            <th>Publisher Name</th>
            <th>Combo Status</th>
            <th>Publisher From</th>
            <th>Publisher To</th>
            <th>Actions</th>
          </tr>
          </thead>
          <tbody>
          <g:each in="${d.getCombosByPropertyNameAndStatus('publisher', params.publisher_status)}" var="p">
            <tr>
              <td><g:link controller="resource" action="show"
                          id="${p.toComponent.class.name}:${p.toComponent.id}">${p.toComponent.name}</g:link></td>
              <td><g:xEditableRefData owner="${p}" field="status" config='Combo.Status'/></td>
              <td><g:xEditable class="ipe" owner="${p}" field="startDate" type="date"/></td>
              <td><g:xEditable class="ipe" owner="${p}" field="endDate" type="date"/></td>
              <td><g:link controller="ajaxSupport" action="deleteCombo" id="${p.id}">Delete</g:link></td>
            </tr>
          </g:each>
          </tbody>
        </table>
      </dd>

      <g:form controller="ajaxSupport" action="addToStdCollection" class="form-inline">
        <input type="hidden" name="__context" value="${d.class.name}:${d.id}"/>
        <input type="hidden" name="__property" value="publisher"/>
        <td>Add Publisher:</td>
        <dd>
          <g:simpleReferenceTypedown class="form-control input-xxlarge" name="__relatedObject"
                                     baseClass="org.gokb.cred.Org"/><button type="submit"
                                                                            class="btn btn-default btn-primary btn-sm ">Add</button>
        </dd>
      </g:form>

    </div>

    <div class="tab-pane" id="identifiers">
      <g:render template="/apptemplates/combosByType"
                model="${[d: d, property: 'ids', fragment: 'identifiers', cols: [
                    [expr: 'toComponent.namespace.value', colhead: 'Namespace'],
                    [expr: 'toComponent.value', colhead: 'ID', action: 'link']]]}"/>

      <g:render template="/apptemplates/addIdentifier" model="${[d: d, hash: '#identifiers']}"/>

    </div>

    <div class="tab-pane" id="addprops">
      <g:render template="/apptemplates/addprops"
                model="${[d: d]}"/>
    </div>

    <div class="tab-pane" id="review">
      <g:render template="/apptemplates/revreqtab"
                model="${[d: d]}"/>
    </div>
  </div>
  <g:render template="/apptemplates/componentStatus"
            model="${[d: displayobj, rd: refdata_properties, dtype: 'KBComponent']}"/>
</div>


<asset:script type="text/javascript">

  $("select[name='publisher_status']").change(function(event) {
  console.log("In here")
    var form =$(event.target).closest("form")
    form.submit();
  });

  function SelectMoveRows(SS1,SS2) {
    var SelID='';
    var SelText='';
    // Move rows from SS1 to SS2 from bottom to top
    for (i=SS1.options.length - 1; i>=0; i--) {
        if (SS1.options[i].selected == true) {
            SelID=SS1.options[i].value;
            SelText=SS1.options[i].text;
            var newRow = new Option(SelText,SelID);
            SS2.options[SS2.length]=newRow;
            SS1.options[i]=null;
        }
    }
    SelectSort(SS2);
  }

  function SelectSort(SelList) {
    var ID='';
    var Text='';
    for (x=0; x < SelList.length - 1; x++) {
        for (y=x + 1; y < SelList.length; y++) {
            if (SelList[x].text > SelList[y].text) {
                ID=SelList[x].value;
                Text=SelList[x].text;
                SelList[x].value=SelList[y].value;
                SelList[x].text=SelList[y].text;
                SelList[y].value=ID;
                SelList[y].text=Text;
            }
        }
    }
  }
  function removeTitle(selectName) {
    $("select[name='"+selectName+"']").find(":selected").remove()
  }

  function AddTitle(titleIdHidden,ss) {
    // alert(titleIdHidden.value);
    // alert(titleIdHidden.parentNode.getElementsByTagName('div')[0].getElementsByTagName('span')[0].innerHTML);
    var newRow=new Option(titleIdHidden.parentNode.getElementsByTagName('div')[0].getElementsByTagName('span')[0].innerHTML,
                          titleIdHidden.value);
    ss.options[ss.length] = newRow;
    SelectSort[ss];
  }

  function submitTitleHistoryEvent(ss1,ss2) {
    selectAll(ss1);
    selectAll(ss2);
  }

  function selectAll(ss) {
    for (i=ss.options.length - 1; i>=0; i--) {
      ss.options[i].selected = true;
    }
  }

  var hash = location.hash;
  hash && $('ul.nav a[href="' + hash + '"]');

  $('.nav-tabs a').click(function (e) {
    var scrollmem = $('body').scrollTop();
    window.location.hash = this.hash;
    $('html,body').scrollTop(scrollmem);
  });

</asset:script>
