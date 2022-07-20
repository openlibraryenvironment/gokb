<dl class="dl-horizontal">
  <dt> <g:annotatedLabel owner="${d}" property="explanation">Title</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="title" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="description">Description</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="description" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="explanation">Explanation</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="explanation" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="owner">Category</g:annotatedLabel> </dt>
  <dd>
    <g:manyToOneReferenceTypedown owner="${d}" field="owner" baseClass="org.gokb.cred.DSCategory">${d.owner?.description}</g:manyToOneReferenceTypedown>
  </dd>
  <g:if test="${d.id}">

    <dt> Applied Criteria </dt>
    <dd>
    <table class="table table-bordered">
      <thead>
        <tr>
          <th>Item</th>
          <th>Applied Criteria</th>
          <th>Notes</th>
        </tr>
      </thead>
      <tbody>
        <g:each in="${d.getDecisionSupportLines()}" var="ac">
          <tr>
            <td>
              <g:if test="${ac.appliedTo}">
                <g:link controller="resource" action="show" id="${ac.appliedTo.class.name}:${ac.appliedTo.id}">${ac.appliedTo?.getNiceName()} ${ac.appliedTo?.name}</g:link>
              </g:if>
            </td>
            <td style="vertical-align:top; white-space: nowrap;">
              <i class="fa fa-question-circle fa-2x ${(ac.value?.value=='Unknown'||ac.value?.value==null)?'text-neutral':''}" ></i>&nbsp;
              <i class="fa fa-times-circle fa-2x ${ac.value?.value=='Red'?'text-negative':''}"></i> &nbsp;
              <i class="fa fa-info-circle fa-2x" ${ac.value?.value=='Amber'?'text-contentious':''}"></i>&nbsp;
              <i class="fa fa-check-circle fa-2x ${ac.value?.value=='Green'?'text-positive':''}"></i>
            </td>
            <td>
              <ul>
                <g:each in="${ac.notes}" var="note">
                  <li>${note.note}</li>
                </g:each>
              </ul>
            </td>
          </tr>
        </g:each>
      </tbody>
    </table>
    </dd>
  </g:if>

</dl>
