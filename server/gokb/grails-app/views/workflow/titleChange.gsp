<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb: Title Change</title>
</head>
<body>

  <h1 class="page-header">Title Change</h1>
  <div id="mainarea" class="panel panel-default">
    <div class="panel-heading">
      <h3 class="panel-title">Step 1 of 2</h3>
    </div>
    <div class="panel-body">
      <g:form name="AddHistoryForm" controller="workflow" action="startTitleChange" method="get">
        <div class="row">
          <div class="col-md-12">
          <dl>
            <dt>Change Type:</dt>
            <dd>
              <select name="changeType">
                <option value="nameChange">Simple Name Change</option>
              </select>
            </dd>

            <dt> Titles </dt>
            <dd>
              <table>
                <tr>
                  <th>Before</th>
                  <th></th>
                  <th>After</th>
                </tr>
                <tr>
                  <td><select name="beforeTitles" size="5" multiple class="input-xxlarge" style="width: 500px;">
                    <g:each in="${objects_to_action}" var="o">
                      <option value="org.gokb.cred.TitleInstance:${o.id}"> ${o.name} </option>
                    </g:each>
                  </select><br /></td>
                  <td>
                    <button type="button" onClick="SelectMoveRows(document.AddHistoryForm.beforeTitles,document.AddHistoryForm.afterTitles)">&gt;</button>
                    <br />
                    <button type="button" onClick="SelectMoveRows(document.AddHistoryForm.afterTitles,document.AddHistoryForm.beforeTitles)">&lt;</button>
                    <br />
                  </td>
                  <td><select name="afterTitles" size="5" multiple="multiple"
                    class="input-xxlarge" style="width: 500px;" ></select></td>
                </tr>
                <tr>
                  <td><g:simpleReferenceTypedown class="form-control" name="fromTitle" baseClass="org.gokb.cred.TitleInstance" /> <br />
                    <button type="button" onClick="AddTitle(document.AddHistoryForm.fromTitle, document.AddHistoryForm.beforeTitles)">Add</button></td>
                  <td></td>
                  <td><g:simpleReferenceTypedown class="form-control" name="ToTitle" baseClass="org.gokb.cred.TitleInstance" /> <br />
                    <button type="button" onClick="AddTitle(document.AddHistoryForm.ToTitle, document.AddHistoryForm.afterTitles)">Add</button></td>
                </tr>
              </table>
            </dd>
            <dt>Event Date</dt>
            <dd> <input type="date" name="eventDate" /> </dd>
            <dt></dt>
            <dd>
            </dd>
          </dl>
        </div>
        <button class="btn btn-default btn-sm pull-right"
                onClick="submitTitleChangeRequest(document.AddHistoryForm.beforeTitles,document.AddHistoryForm.afterTitles)">Next</button>
      </g:form>
    </div>
  </div>

<asset:script type="text/javascript">
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
    var newRow=new Option(titleIdHidden.parentNode.getElementsByTagName('div')[0].getElementsByTagName('span')[0].innerHTML,
                          titleIdHidden.value);
    ss.options[ss.length] = newRow;
    SelectSort[ss];
  }

  function submitTitleChangeRequest(ss1,ss2) {
    selectAll(ss1);
    selectAll(ss2);
  }

  function selectAll(ss) {
    for (i=ss.options.length - 1; i>=0; i--) {
      ss.options[i].selected = true;
    }
  }
</asset:script>

</body>
</html>
