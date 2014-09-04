<dl class="dl-horizontal">
  <dt>
    <g:annotatedLabel owner="${d}" property="name">Title</g:annotatedLabel>
  </dt>
  <dd>
    ${d.name}
    (Modify title through variants below)
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="status"
      config='KBComponent.Status' />
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
    <g:annotatedLabel owner="${d}" property="currentPubisher">Current Publisher</g:annotatedLabel>
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
    <g:xEditable class="ipe" owner="${d}" type="date"
      field="publishedFrom" />
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="publishedTo">Published To</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditable class="ipe" owner="${d}" type="date" field="publishedTo" />
  </dd>

  <g:if test="${d.id != null}">
    <dt>
      <g:annotatedLabel owner="${d}" property="titleHistory">Title History</g:annotatedLabel>
    </dt>
    <dd>
      <table class="table table-striped table-bordered">
        <thead>
          <tr>
            <th>Date</th>
            <th>Before</th>
            <th>After</th>
            <th>Actions</th>
          <tr>
        </thead>
        <tbody>
          <g:each in="${d.titleHistory}" var="he">
            <tr>
              <td><g:formatDate
                  format="${session.sessionPreferences?.globalDateFormat}"
                  date="${he.date}" /></td>
              <td>
                <ul>
                  <g:each in="${he.from}" var="ft">
                    <li><g:if test="${ft != null}">
                        <g:link controller="resource" action="show"
                          id="${ft?.class.name}:${ft.id}">
                          ${ft.name}
                        </g:link> (
                      <g:formatDate
                          format="${session.sessionPreferences?.globalDateFormat}"
                          date="${ft.publishedFrom}" />
                        <em>To</em>
                        <g:formatDate
                          format="${session.sessionPreferences?.globalDateFormat}"
                          date="${ft.publishedTo}" /> ) 
                    </g:if> <g:else>From title not present</g:else></li>
                  </g:each>
                </ul>
              </td>
              <td>
                <ul>
                  <g:each in="${he.to}" var="ft">
                    <li>
                    	<g:if test="${ft != null}">
                        <g:link controller="resource" action="show"
                          id="${ft.class.name}:${ft.id}">
                          ${ft.name}
                        </g:link> (
                        <g:formatDate
                          format="${session.sessionPreferences?.globalDateFormat}"
                          date="${ft.publishedFrom}" />
                        <em>To</em>
                        <g:formatDate
                          format="${session.sessionPreferences?.globalDateFormat}"
                          date="${ft.publishedTo}" /> )
	                    </g:if>
	                    <g:else>From title not present</g:else>
                    </li>
                  </g:each>
                </ul>
              </td>
              <td><g:link controller="workflow"
                  action="DeleteTitleHistoryEvent" class="confirm-click" data-confirm-message="Are you sure you wish to delete this Title History entry?" id="${he.id}">Delete</g:link></td>
            </tr>
          </g:each>
        </tbody>
      </table>
    </dd>
  </g:if>
</dl>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#titledetails" data-toggle="tab">Title
        Details</a></li>
    <g:if test="${ d.isEditable() }">
      <li><a href="#history" data-toggle="tab">Add to Title
          History</a></li>
    </g:if>
    <li><a href="#identifiers" data-toggle="tab">Identifiers <span
        class="badge badge-warning">
          ${d.ids?.size()}
      </span></a></li>
    <li><a href="#publishers" data-toggle="tab">Publishers <span
        class="badge badge-warning">
          ${d.getCombosByPropertyName('publisher')?.size()}
      </span></a></li>
    <li><a href="#availability" data-toggle="tab">Availability <span
        class="badge badge-warning">
          ${d.tipps?.size()}
      </span></a></li>
    <li><a href="#addprops" data-toggle="tab">Custom Fields <span
        class="badge badge-warning">
          ${d.additionalProperties?.size()}
      </span></a></li>
    <li><a href="#review" data-toggle="tab">Review Tasks <span
        class="badge badge-warning">
          ${d.reviewRequests?.size()}
      </span></a></li>
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
              config='TitleInstance.Medium' />
          </dd>

          <dt>
            <g:annotatedLabel owner="${d}" property="pureOA">pureOA</g:annotatedLabel>
          </dt>
          <dd>
            <g:xEditableRefData owner="${d}" field="pureOA"
              config='TitleInstance.PureOA' />
          </dd>

          <dt>
            <g:annotatedLabel owner="${d}" property="continuingSeries">Continuing Series</g:annotatedLabel>
          </dt>
          <dd>
            <g:xEditableRefData owner="${d}" field="continuingSeries"
              config='TitleInstance.ContinuingSeries' />
          </dd>

          <dt>
            <g:annotatedLabel owner="${d}" property="alternateTitles">Alternate Titles</g:annotatedLabel>
          </dt>
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
                    <td><g:xEditable owner="${v}" field="variantName" /></td>
                    <td><g:xEditableRefData owner="${v}" field="status"
                        config='KBComponentVariantName.Status' /></td>
                    <td><g:xEditableRefData owner="${v}" field="variantType"
                        config='KBComponentVariantName.VariantType' /></td>
                    <td><g:xEditableRefData owner="${v}" field="locale"
                        config='KBComponentVariantName.Locale' /></td>
                    <td>
                    	<g:if test="${ d.isEditable() }">
                        <g:link controller="workflow" action="AuthorizeVariant"
                          id="${v.id}">Make Authorized</g:link>,
                        <g:link controller="workflow"
                        	class="confirm-click" data-confirm-message="Are you sure you wish to delete this Variant?"
                          action="DeleteVariant" id="${v.id}" >Delete</g:link>
                    	</g:if>
                    </td>
                  </tr>
                </g:each>
              </tbody>
            </table>
            <button
              class="hidden-license-details btn btn-default btn-sm btn-primary "
              data-toggle="collapse" data-target="#collapseableAddTitle">
              Add new <i class="glyphicon glyphicon-plus"></i>
            </button>
            <dl id="collapseableAddTitle" class="dl-horizontal collapse">
              <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
                <input type="hidden" name="__context" value="${d.class.name}:${d.id}" />
                <input type="hidden" name="__newObjectClass" value="org.gokb.cred.KBComponentVariantName" />
                <input type="hidden" name="__recip" value="owner" />
                <dt>Add Title Variant</dt>
                <dd>
                  <input type="text" name="variantName" />
                </dd>
                <dt>Locale</dt>
                <dd>
                  <g:simpleReferenceTypedown class="form-control" name="locale"
                    baseClass="org.gokb.cred.RefdataValue"
                    filter1="KBComponentVariantName.Locale" />
                </dd>
                <dt>Variant Type</dt>
                <dd>
                  <g:simpleReferenceTypedown class="form-control" name="variantType"
                    baseClass="org.gokb.cred.RefdataValue"
                    filter1="KBComponentVariantName.VariantType" />
                </dd>
                <dt></dt>
                <dd>
                  <button type="submit"
                    class="btn btn-default btn-primary btn-sm ">Add</button>
                </dd>
              </g:form>
            </dl>
          </dd>
        </dl>
      </g:if>
    </div>
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
                  </select><br /></td>
                  <td>
                    <button type="button"
                      onClick="SelectMoveRows(document.AddHistoryForm.beforeTitles,document.AddHistoryForm.afterTitles)">&gt;</button>
                    <br />
                    <button type="button"
                      onClick="SelectMoveRows(document.AddHistoryForm.afterTitles,document.AddHistoryForm.beforeTitles)">&lt;</button>
                    <br />
                  </td>
                  <td><select name="afterTitles" size="5" multiple="multiple"
                    class="input-xxlarge" style="width: 500px;" ></select></td>
                </tr>
                <tr>
                  <td><g:simpleReferenceTypedown class="form-control" name="fromTitle"
                      baseClass="org.gokb.cred.TitleInstance" /> <br />
                    <button type="button"
                      onClick="AddTitle(document.AddHistoryForm.fromTitle, document.AddHistoryForm.beforeTitles)">Add</button></td>
                  <td></td>
                  <td><g:simpleReferenceTypedown class="form-control" name="ToTitle"
                      baseClass="org.gokb.cred.TitleInstance" /> <br />
                    <button type="button"
                      onClick="AddTitle(document.AddHistoryForm.ToTitle, document.AddHistoryForm.afterTitles)">Add</button></td>
                </tr>
              </table>
            </dd>
            <dt>Event Date</dt>
            <dd>
              <input type="date" name="EventDate" />
            </dd>
            <dt></dt>
            <dd>
              <button
                onClick="submitTitleHistoryEvent(document.AddHistoryForm.beforeTitles,document.AddHistoryForm.afterTitles)">Add
                Title History Event -&gt;</button>
            </dd>
          </g:form>
        </dl>
      </g:if>
    </div>

    <div class="tab-pane" id="availability">
      <dt>
        <g:annotatedLabel owner="${d}" property="availability">Availability</g:annotatedLabel>
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
                <td>Date: <g:formatDate
                    format="${session.sessionPreferences?.globalDateFormat}"
                    date="${tipp.startDate}" /><br /> Volume: ${tipp.startVolume}<br />
                  Issue: ${tipp.startIssue}
                </td>
                <td>Date: <g:formatDate
                    format="${session.sessionPreferences?.globalDateFormat}"
                    date="${tipp.endDate}" /><br /> Volume: ${tipp.endVolume}<br />
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
        <table class="table table-striped table-bordered">
          <thead>
            <tr>
              <th>Publisher Name</th>
              <th>Combo Status</th>
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
                <td><g:xEditableRefData owner="${p}" field="status"
                    config='Combo.Status' /></td>
                <td><g:xEditable class="ipe" owner="${p}" field="startDate"
                    type="date" /></td>
                <td><g:xEditable class="ipe" owner="${p}" field="endDate"
                    type="date" /></td>
              </tr>
            </g:each>
          </tbody>
        </table>
      </dd>

    </div>

    <div class="tab-pane" id="identifiers">
      <g:render template="combosByType" contextPath="../apptemplates"
        model="${[d:d, property:'ids', cols:[
                  [expr:'toComponent.namespace.value', colhead:'Namespace'],
                  [expr:'toComponent.value', colhead:'ID', action:'link']]]}" />
    </div>

    <div class="tab-pane" id="addprops">
      <g:render template="addprops" contextPath="../apptemplates"
        model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="revreqtab" contextPath="../apptemplates"
        model="${[d:d]}" />
    </div>

  </div>
  <g:render template="componentStatus" contextPath="../apptemplates"
    model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
</div>


<r:script type="text/javascript">
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
</r:script>
