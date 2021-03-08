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
      <g:xEditable class="ipe" owner="${d}" field="name" />
    </g:else>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
  </dt>
  <dd>
    <sec:ifAnyGranted roles="ROLE_SUPERUSER">
      <g:xEditableRefData owner="${d}" field="status" config='KBComponent.Status' />
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
      config='TitleInstance.ReasonRetired' />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="editStatus"
      config='KBComponent.EditStatus' />
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
    <g:xEditable class="ipe" owner="${d}" type="date" field="publishedFrom" />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="publishedTo">Published To</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" type="date" field="publishedTo" />
  </dd>

  <g:if test="${d?.id != null && d.titleHistory}">
    <dt>
      <g:annotatedLabel owner="${d}" property="titleHistory">Title History</g:annotatedLabel>
    </dt>
    <dd>

      <g:render template="/apptemplates/fullth" model="${[d:d]}" />
    </dd>
  </g:if>
</dl>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#titledetails" data-toggle="tab">Title Details</a></li>
    <g:if test="${d.id != null}">
      <li><a href="#altnames" data-toggle="tab">Alternate Names <span class="badge badge-warning"> ${d.variantNames?.size() ?: '0'}</span> </a></li>

      <g:if test="${ d.isEditable() }">
        <li><a href="#history" data-toggle="tab">Add to Title History</a></li>
      </g:if>
      <li><a href="#identifiers" data-toggle="tab">Identifiers <span
          class="badge badge-warning">
            ${d.ids?.size() ?: '0'}
        </span></a></li>
      <li><a href="#publishers" data-toggle="tab">Publishers <span
          class="badge badge-warning">
            ${d.getCombosByPropertyNameAndStatus('publisher',params.publisher_status)?.size() ?: '0'}
        </span></a></li>
      <li><a href="#availability" data-toggle="tab">Availability <span
          class="badge badge-warning">
            ${d.tipps?.size() ?: '0'}
        </span></a></li>
      <li><a href="#addprops" data-toggle="tab">Custom Fields <span
          class="badge badge-warning">
            ${d.additionalProperties?.size() ?: '0'}
        </span></a></li>
      <li><a href="#review" data-toggle="tab">Review Tasks (Open/Total)
        <span class="badge badge-warning">
          ${d.reviewRequests?.findAll { it.status == org.gokb.cred.RefdataCategory.lookup('ReviewRequest.Status','Open') }?.size() ?: '0'}/${d.reviewRequests.size()}
        </span>
      </a></li>
    </g:if>
    <g:else>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Alternate Names </span></li>
      <g:if test="${ d.isEditable() }">
        <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Add to Title History </span></li>
      </g:if>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Identifiers </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Publishers </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Availability </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Custom Fields </span></li>
      <li class="disabled" title="${message(code:'component.create.idMissing.label')}"><span class="nav-tab-disabled">Review Tasks </span></li>
    </g:else>

  </ul>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="titledetails">

        <dl class="dl-horizontal">

          <dt>
            <g:annotatedLabel owner="${d}" property="medium">Medium</g:annotatedLabel>
          </dt>
          <dd>
            <g:if test="${d.id != null}">
              <g:xEditableRefData owner="${d}" field="medium"
                config='TitleInstance.Medium' />
            </g:if>
            <g:else>
              Database
            </g:else>
          </dd>

          <dt>
            <g:annotatedLabel owner="${d}" property="OAStatus">OA Status</g:annotatedLabel>
          </dt>
          <dd>
            <g:xEditableRefData owner="${d}" field="OAStatus"
              config='TitleInstance.OAStatus' />
          </dd>
        </dl>
    </div>

    <g:render template="/tabTemplates/showVariantnames" model="${[d:displayobj, showActions:true]}" />

    <div class="tab-pane" id="history">
      <g:if test="${d.id != null}">
        <dl class="dl-horizontal">
          <g:form name="AddHistoryForm" controller="workflow" action="createTitleHistoryEvent">
            <dt style="width:100px;">
              Titles
              </dt>
            <dd style="width:80%">
              <table style="width:100%">
                <tr>
                  <th>Before</th>
                  <th></th>
                  <th>After</th>
                </tr>
                <tr>
                  <td><select name="beforeTitles" size="5" multiple
                    class="input-xxlarge" style="width:100%">
                      <option value="org.gokb.cred.TitleInstance:${d.id}">
                        ${d.name}
                      </option>
                  </select><br /></td>
                  <td style="text-align:center;">
                    <button class="btn btn-sm" style="margin: 2px 5px;" type="button"
                      onClick="SelectMoveRows(document.AddHistoryForm.beforeTitles,document.AddHistoryForm.afterTitles)">&gt;</button>
                    <div style="height:2px;"></div>
                    <button class="btn btn-sm" style="margin: 2px 5px;" type="button"
                      onClick="SelectMoveRows(document.AddHistoryForm.afterTitles,document.AddHistoryForm.beforeTitles)">&lt;</button>
                  </td>
                  <td><select name="afterTitles" size="5" multiple="multiple"
                    class="input-xxlarge" style="width:100%"></select></td>
                </tr>
                <tr>
                  <td><g:simpleReferenceTypedown class="form-control" name="fromTitle"
                      baseClass="org.gokb.cred.TitleInstance" /> <br />
                    <button class="btn btn-sm" type="button"
                      onClick="AddTitle(document.AddHistoryForm.fromTitle, document.AddHistoryForm.beforeTitles)">Add</button>
                    <button class="btn btn-sm" type="button" onClick="removeTitle('beforeTitles')">Remove</button></td>
                  <td></td>
                  <td><g:simpleReferenceTypedown class="form-control" name="ToTitle"
                      baseClass="org.gokb.cred.TitleInstance" /> <br />
                    <button class="btn btn-sm" type="button"
                      onClick="AddTitle(document.AddHistoryForm.ToTitle, document.AddHistoryForm.afterTitles)">Add</button>
                    <button class="btn btn-sm" type="button" onClick="removeTitle('afterTitles')">Remove</button></td>
                </tr>
              </table>
            </dd>
            <dt class="dt-label" style="width:100px;">Event Date</dt>
            <dd>
              <input type="date" class="form-control" name="EventDate" required />
            </dd>
            <dt style="width:100px;"></dt>
            <dd>
              <button class="btn btn-default btn-primary"
                onClick="submitTitleHistoryEvent(document.AddHistoryForm.beforeTitles,document.AddHistoryForm.afterTitles)">Add
                Title History Event</button>
            </dd>
          </g:form>
        </dl>
      </g:if>
    </div>

    <div class="tab-pane" id="availability">
      <g:if test="${d.id}">
        <dt>
          <g:annotatedLabel owner="${d}" property="availability">Package Availability</g:annotatedLabel>
        </dt>
        <dd>
          <g:link class="display-inline" controller="search" action="index"
            params="[qbe:'g:3tipps', inline:true, refOid: d.getLogEntityId(), qp_title_id:d.id, hide:['qp_title_id', 'qp_title']]"
            id="">Availability of this Title</g:link>
        </dd>
      </g:if>
    </div>

    <div class="tab-pane" id="publishers">
      <g:render template="/tabTemplates/showPublishers"
      model="${[d:displayobj]}" />
    </div>

    <div class="tab-pane" id="identifiers">
      <dl>
        <dt>
          <g:annotatedLabel owner="${d}" property="ids">Identifiers</g:annotatedLabel>
        </dt>
        <dd>
          <g:render template="/apptemplates/combosByType"
            model="${[d:d, property:'ids', fragment:'identifiers', cols:[
                      [expr:'toComponent.namespace.value', colhead:'Namespace'],
                      [expr:'toComponent.value', colhead:'ID', action:'link']]]}" />
          <g:if test="${d.isEditable()}">
            <h4>
              <g:annotatedLabel owner="${d}" property="addIdentifier">Add new Identifier</g:annotatedLabel>
            </h4>
            <g:render template="/apptemplates/addIdentifier" model="${[d:d, hash:'#identifiers']}"/>
          </g:if>
        </dd>
      </dl>
    </div>

    <div class="tab-pane" id="addprops">
      <g:render template="/apptemplates/addprops"
        model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="/apptemplates/revreqtab"
        model="${[d:d]}" />
    </div>
  </div>
  <g:if test="${d.id}">
    <g:render template="/apptemplates/componentStatus" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
  </g:if>
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


  var hash = window.location.hash;
  hash && $('ul.nav a[href="' + hash + '"]').tab('show');

<%--   $('.nav-tabs a').click(function (e) {
    $(this).tab('show');
    var scrollmem = $('body').scrollTop();
    window.location.hash = this.hash;
    $('html,body').scrollTop(scrollmem);
  }); --%>


</asset:script>
