<dl class="dl-horizontal">

  <dt><g:annotatedLabel owner="${d}" property="name">Territory Name</g:annotatedLabel></dt>
  <dd><g:xEditable class="ipe" owner="${d}" field="name" /></dd>

  <g:if test="${d.id != null}">
  <dt><g:annotatedLabel owner="${d}" property="reasonRetired">Status Reason</g:annotatedLabel></dt>
  <dd><g:xEditableRefData owner="${d}" field="reasonRetired" config='TitleInstance.ReasonRetired' /></dd>

  <dt><g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel></dt>
  <dd><g:xEditableRefData owner="${d}" field="status" config='KBComponent.Status' /></dd>

  <dt><g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel></dt>
  <dd><g:xEditableRefData owner="${d}" field="editStatus" config='KBComponent.EditStatus' /></dd>
  </g:if>
</dl>