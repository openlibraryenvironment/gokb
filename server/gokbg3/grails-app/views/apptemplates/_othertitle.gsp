<dl class="dl-horizontal">
  <dt>
    <g:annotatedLabel owner="${d}" property="name">Title</g:annotatedLabel>
  </dt>
  <dd style="max-width:60%">
    <div>
      ${d.name}<br/>
      <span style="white-space:nowrap;">(Modify title through <i>Alternate Names</i> below)</span>
    </div>
  </dd>

  <dt>
    <g:annotatedLabel owner="${d}" property="status">Work</g:annotatedLabel>
  </dt>
  <dd style="width:50%">
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

</dl>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#titledetails" data-toggle="tab">Title Details</a></li>
    <li><a href="#altnames" data-toggle="tab">Alternate Names <span class="badge badge-warning"> ${d.variantNames?.size() ?: '0'}</span> </a></li>
        
    <li><a href="#identifiers" data-toggle="tab">Identifiers <span class="badge badge-warning"> ${d.ids?.size() ?: '0'} </span></a></li>

    <li><a href="#publishers" data-toggle="tab">Publishers <span
        class="badge badge-warning">
          ${d.publisher?.size() ?: '0'}
      </span></a></li>

    <li><a href="#availability" data-toggle="tab">Availability <span
        class="badge badge-warning">
          ${d.tipps?.size() ?: '0'}
      </span></a></li>

    <li><a href="#addprops" data-toggle="tab">Custom Fields <span
        class="badge badge-warning">
          ${d.additionalProperties?.size() ?: '0'}
      </span></a></li>

    <li><a href="#review" data-toggle="tab">Review Tasks <span
        class="badge badge-warning">
          ${d.reviewRequests?.size() ?: '0'}
      </span></a></li>

    <li><a href="#ds" data-toggle="tab">Decision Support</a></li>

    <li><a href="#people" data-toggle="tab">People <span class="badge badge-warning"> ${d.people?.size() ?: '0'} </span></a></li>

    <li><a href="#subjects" data-toggle="tab">Subjects <span class="badge badge-warning"> ${d.subjects?.size() ?: '0'} </span></a></li>

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

    <g:render template="/tabTemplates/showVariantnames" model="${[d:displayobj, showActions:true]}" />

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
        <g:render template="/apptemplates/tippdisplay" model="${[d:d.tipps]}" />
      </dd>

    </div>

    <div class="tab-pane" id="publishers">

      <dt>
        <g:annotatedLabel owner="${d}" property="publishers">Publishers</g:annotatedLabel>
      </dt>

     <dd>
		<g:render template="/apptemplates/simpleCombos"
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
      <g:render template="/apptemplates/simpleCombos"
        model="${[d:d, property:'ids', fragment:'identifiers', cols:[
                  [expr:'namespace.value', colhead:'Namespace'],
                  [expr:'value', colhead:'ID', action:'link']]]}" />
    </div>
    </div>

    <div class="tab-pane" id="addprops">
      <g:render template="/apptemplates/addprops"
        model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="review">
      <g:render template="/apptemplates/revreqtab"
        model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="ds">
      <g:render template="/apptemplates/dstab" model="${[d:d]}" />
    </div>

    <div class="tab-pane" id="people">

     <dl>
	    <dt>
		  <g:annotatedLabel owner="${d}" property="people">Add People</g:annotatedLabel>
		</dt>
		<dd>
		  <!-- this bit could be better  -->
		  <g:render template="/apptemplates/componentPerson"
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
		  <g:render template="/apptemplates/componentSubject"
				    model="${[d:d, property:'subjects', cols:[[expr:'subject.name',colhead:'Subject Heading',action:'link-subject'],
						                                      [expr:'subject.clsmrk', colhead: 'Classification']],targetClass:'org.gokb.cred.Subject',direction:'in']}" />
		</dd>
	  </dl>
	</div>

  </div>
  <g:render template="/apptemplates/componentStatus"
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
