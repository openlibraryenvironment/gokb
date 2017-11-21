<dl class="dl-horizontal">

  <dt><g:annotatedLabel owner="${d}" property="name">Curatory Group Name</g:annotatedLabel></dt>
  <dd><g:xEditable class="ipe" owner="${d}" field="name" /></dd>

  <g:if test="${d.id != null}">
	
	  <dt><g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel></dt>
	  <dd><g:xEditableRefData owner="${d}" field="status" config='KBComponent.Status' /></dd>
	
	  <dt><g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel></dt>
	  <dd><g:xEditableRefData owner="${d}" field="editStatus" config='KBComponent.EditStatus' /></dd>
	  <dt><g:annotatedLabel owner="${d}" property="users">Members</g:annotatedLabel></dt>
	  <dd>
	    <g:if test="${ d.users }" >
		    <ul>
		      <g:each var="u" in="${ d.users }" >
		        <li><a href="mailto:${ u.email }" ><i class="fa fa-envelope"></i> ${u.displayName }</a></li>
		      </g:each>
		    </ul>
	    </g:if>
			<g:else>
	      <p>There are no members of this curatory group.</p>  		
			</g:else>
		</dd>
  </g:if>
</dl>