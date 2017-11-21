<dl class="dl-horizontal">
  <dt>
    <g:annotatedLabel owner="${d}" property="name">Title</g:annotatedLabel>
  </dt>
  <dd>
    ${d.name}
    (Modify title through variants below)
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="status">Work</g:annotatedLabel>
  </dt>
  <dd>
    <g:if test="${d.work}">
      <g:link controller="resource" action="show" id="${d.work.class.name}:${d.work.id}"> ${d.work.name} </g:link>
    </g:if>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
  </dt>
  <dd>
    <g:xEditableRefData owner="${d}" field="status" config='KBComponent.Status' />
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


  <dt> <g:annotatedLabel owner="${d}" property="editionNumber">Edition Number</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="editionNumber" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="coverImage">Cover Image URL</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="coverImage" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="editionDifferentiator">Edition Differentiator</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="editionDifferentiator" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="editionStatement">Edition Statement</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="editionStatement" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="volumeNumber">Volume Number</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="volumeNumber" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="dateFirstInPrint">Date first in print</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" type="date" field="dateFirstInPrint" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="dateFirstOnline">Date first online</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" type="date" field="dateFirstOnline" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="summaryOfContent">Summary of content</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="summaryOfContent" /> </dd>

  <g:if test="${d.id != null}">
    <dt>
      <g:annotatedLabel owner="${d}" property="titleHistory">Title History</g:annotatedLabel>
    </dt>
    <dd>
      <table class="table table-striped table-bordered">
        <thead>
          <tr>
            <th>Event Date</th>
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
                        <g:if test="${ft.id == d.id}"><b></g:if>
                        <g:link controller="resource" action="show"
                          id="${ft?.class.name}:${ft.id}">
                          ${ft.name}
                        </g:link> 

                        <g:if test="${ft.publishedFrom ||ft.publishedTo }">
                         (
                          <g:formatDate 
                          format="${session.sessionPreferences?.globalDateFormat}" date="${ft.publishedFrom}" />
                          <em>To</em>
                          <g:formatDate
                            format="${session.sessionPreferences?.globalDateFormat}" date="${ft.publishedTo}" /> 
                          )
                        </g:if> 
                        
                        <g:if test="${ft.id == d.id}"></b></g:if>
                      </g:if> <g:else>From title not present</g:else>
                    </li>
                  </g:each>
                </ul>
              </td>
              <td>
                <ul>
                  <g:each in="${he.to}" var="ft">
                    <li>
                    	<g:if test="${ft != null}">
                        <g:if test="${ft.id == d.id}"><b></g:if>
                        <g:link controller="resource" action="show"
                          id="${ft.class.name}:${ft.id}">
                          ${ft.name}
                        </g:link> 
                        <g:if test="${ft.publishedFrom ||ft.publishedTo }">
                          (
                          <g:formatDate
                            format="${session.sessionPreferences?.globalDateFormat}" date="${ft.publishedFrom}" />
                          <em>To</em>
                          <g:formatDate
                            format="${session.sessionPreferences?.globalDateFormat}" date="${ft.publishedTo}" /> 
                          )
                        </g:if>
                        <g:if test="${ft.id == d.id}"></b></g:if>
	                    </g:if>
	                    <g:else>To title not present</g:else>
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
    <li class="active"><a href="#titledetails" data-toggle="tab">Title Details</a></li>
    <li><a href="#altnames" data-toggle="tab">Alternate Names <span class="badge badge-warning"> ${d.variantNames?.size()}</span> </a></li>
        
    <g:if test="${ d.isEditable() }">
      <li><a href="#history" data-toggle="tab">Add to Title History</a></li>
    </g:if>

    <li><a href="#identifiers" data-toggle="tab">Identifiers <span class="badge badge-warning"> ${d.ids?.size()} </span></a></li>

    <li><a href="#publishers" data-toggle="tab">Publishers <span
        class="badge badge-warning">
          ${d.publisher?.size()}
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

    <li><a href="#ds" data-toggle="tab">Decision Support</a></li>

    <li><a href="#people" data-toggle="tab">People <span class="badge badge-warning"> ${d.people?.size()} </span></a></li>

    <li><a href="#subjects" data-toggle="tab">Subjects <span class="badge badge-warning"> ${d.subjects?.size()} </span></a></li>

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
            <g:annotatedLabel owner="${d}" property="OAStatus">OA Status</g:annotatedLabel>
          </dt>
          <dd>
            <g:xEditableRefData owner="${d}" field="OAStatus"
              config='TitleInstance.OAStatus' />
          </dd>

          <dt>
            <g:annotatedLabel owner="${d}" property="continuingSeries">Continuing Series</g:annotatedLabel>
          </dt>
          <dd>
            <g:xEditableRefData owner="${d}" field="continuingSeries"
              config='TitleInstance.ContinuingSeries' />
          </dd>
        </dl>
      </g:if>
    </div>

    <g:render template="showVariantnames" contextPath="../tabTemplates"
      model="${[d:displayobj, showActions:true]}" />

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
                      onClick="AddTitle(document.AddHistoryForm.fromTitle, document.AddHistoryForm.beforeTitles)">Add</button>
                    <button type="button" onClick="removeTitle('beforeTitles')">Remove</button></td>
                  <td></td>
                  <td><g:simpleReferenceTypedown class="form-control" name="ToTitle"
                      baseClass="org.gokb.cred.TitleInstance" /> <br />
                    <button type="button"
                      onClick="AddTitle(document.AddHistoryForm.ToTitle, document.AddHistoryForm.afterTitles)">Add</button>
                    <button type="button" onClick="removeTitle('afterTitles')">Remove</button></td>
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
        <g:render template="tippdisplay" contextPath="../apptemplates" model="${[d:d.tipps]}" />
      </dd>

    </div>

    <div class="tab-pane" id="publishers">

      <dt>
        <g:annotatedLabel owner="${d}" property="publishers">Publishers</g:annotatedLabel>
      </dt>

     <dd>
		<g:render template="simpleCombos" contextPath="../apptemplates"
        model="${[d:d, property:'publisher', fragment:'identifiers', delete:'true', cols:[
                  [expr:'name', colhead:'name', action:'link'],
                  [expr:'status', colhead:'status'],
				  [expr:'startDate', colhead: 'from'],
				  [expr:'endDate', colhead: 'to']]]}" />
      </dd>

        <g:form controller="ajaxSupport" action="addToStdCollection" class="form-inline">
          <input type="hidden" name="__context" value="${d.class.name}:${d.id}" />
          <input type="hidden" name="__property" value="publisher" />
          <dt>Add Publisher:</td>
          <dd>
            <g:simpleReferenceTypedown class="form-control input-xxlarge" name="__relatedObject" baseClass="org.gokb.cred.Org" /><button type="submit" class="btn btn-default btn-primary btn-sm ">Add</button>
          </dd>
        </g:form>


    </div>

    <div class="tab-pane" id="identifiers">
    <div class="tab-pane" id="identifiers">
      <g:render template="simpleCombos" contextPath="../apptemplates"
        model="${[d:d, property:'ids', fragment:'identifiers', cols:[
                  [expr:'namespace.value', colhead:'Namespace'],
                  [expr:'value', colhead:'ID', action:'link']]]}" />
    </div>
    </div>

    <div class="tab-pane" id="addprops">
      <g:render template="addprops" contextPath="../apptemplates"
        model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="revreqtab" contextPath="../apptemplates"
        model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="ds">
      <g:render template="dstab" contextPath="../apptemplates" model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="people">

     <dl>
	    <dt>
		  <g:annotatedLabel owner="${d}" property="people">Add People</g:annotatedLabel>
		</dt>
		<dd>
		  <!-- this bit could be better  -->
		  <g:render template="componentPerson" contextPath="../apptemplates"
				    model="${[d:d, property:'people', cols:[[expr:'person.name',colhead:'Name', action:'link-person'],
						                                    [expr:'role.value', colhead: 'Role']], targetClass:'org.gokb.cred.Person',direction:'in']}" />
		</dd>
	  </dl>
    </div>

    <div class="tab-pane" id="subjects">
	  <dl>
	    <dt>
		  <g:annotatedLabel owner="${d}" property="subjects">Add Subjects</g:annotatedLabel>
		</dt>
		<dd>
		  <!-- this bit could be better  -->
		  <g:render template="componentSubject" contextPath="../apptemplates"
				    model="${[d:d, property:'subjects', cols:[[expr:'subject.name',colhead:'Subject Heading',action:'link-subject'],
						                                      [expr:'subject.clsmrk', colhead: 'Classification']],targetClass:'org.gokb.cred.Subject',direction:'in']}" />
		</dd>
	  </dl>
	</div>

  </div>
  <g:render template="componentStatus" contextPath="../apptemplates"
    model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
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


</asset:script>
