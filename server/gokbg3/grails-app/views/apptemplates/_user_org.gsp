<dl class="dl-horizontal">

  <dt><g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel></dt>
  <dd><g:xEditable class="ipe" owner="${d}" field="displayName" /></dd>

  <g:if test="${d.id != null}">
  <g:set var="userIsOrgAdmin" value="${d.members.find { it.party == request.user && it.role?.value == 'Administrator' && it.status?.value == 'Approved'}}" />
  <dt><g:annotatedLabel owner="${d}" property="owner">Owner</g:annotatedLabel></dt>
  <dd>${d.owner}</dd>
  <dt><g:annotatedLabel owner="${d}" property="name">Members</g:annotatedLabel></dt>
  <dd>
    <table id="uomembers"class="table table-bordered table-striped">
      <thead>
        <tr>
          <td>User</td>
          <td>Membership Status</td>
          <td>Role</td>
          <td>Actions</td>
        </tr>
      </thead>
      <tbody>
        <g:each in="${d.members}" var="m">
          <tr>
            <td>${m.party}</td>
            <td>
              <g:if test="${ user.isAdmin() || userIsOrgAdmin }">
                <g:xEditableRefData owner="${m}" field="status" config='MembershipStatus' />
              </g:if>
              <g:else>${m.status?.value}</g:else>
            </td>
            <td>
              <g:if test="${ user.isAdmin() || userIsOrgAdmin }">
                <g:xEditableRefData owner="${m}" field="role" config='MembershipRole' />
              </g:if>
              <g:else>${m.role?.value}</g:else>
            </td>
            <td>
              <g:if test="${user.isAdmin() || userIsOrgAdmin }">
                <g:link controller="ajaxSupport"
                        action="delete"
                        params="${['__context':m.class.name+':'+m.id]}"
                        class="confirm-click"
                        data-confirm-message="Are you sure you wish to delete this user membership?" >
                        Delete
                </g:link>
              </g:if>
            </td>
          </tr>
        </g:each>
      </tbody>
      <tfoot>
        <g:if test="${user.isAdmin() || d.owner == request.user || userIsOrgAdmin }">
          <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
            <input type="hidden" name="__context" value="org.gokb.cred.UserOrganisation:${d.id}"/>
            <input type="hidden" name="__recip" value="memberOf"/>
            <input type="hidden" name="__newObjectClass" value="org.gokb.cred.UserOrganisationMembership"/>
            <tr>
              <td>
                <g:simpleReferenceTypedown class="form-control"
                                          name="party"
                                          baseClass="org.gokb.cred.User"/>
              </td>
              <td>
                <g:simpleReferenceTypedown class="form-control"
                                          name="status"
                                          baseClass="org.gokb.cred.RefdataValue"
                                          filter1="MembershipStatus"/>
              </td>
              <td>
                <g:simpleReferenceTypedown class="form-control"
                                          name="role"
                                          baseClass="org.gokb.cred.RefdataValue"
                                          filter1="MembershipRole"/>
              </td>
              <td><button class="btn btn-success">Add</button></td>
            </tr>
          </g:form>
        </g:if>
      </tfoot>
    </table>
    <g:if test="${!d.members?.party?.contains(request.user)}">
      <button id ="applyBtn" class="btn btn-default" onclick="applyForMembership(${d.id})">Apply for Membership</button>
    </g:if>
  </dd>


  <g:if test="${org.gokb.cred.Folder.isTypeReadable()}">
    <dt><g:annotatedLabel owner="${d}" property="name">Folders / Collections</g:annotatedLabel></dt>
    <dd>
      <table class="table table-bordered table-striped">
        <thead>
          <tr>
            <td>Name</td>
            <td>Actions</td>
          </tr>
        </thead>
        <tbody>
          <g:each in="${d.folders}" var="f">
            <tr>
              <td><g:link controller="resource" action="show" id="org.gokb.cred.Folder:${f.id}">${f.name}</g:link></td>
              <td></td>
            </tr>
          </g:each>
        </tbody>
        <tfoot>
          <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
            <input type="hidden" name="__context" value="org.gokb.cred.UserOrganisation:${d.id}"/>
            <input type="hidden" name="__recip" value="owner"/>
            <input type="hidden" name="__newObjectClass" value="org.gokb.cred.Folder"/>
            <tr>
              <td>
                <input type="text" class="form-control" name="name" placeholder="New Folder Name"/>
              </td>
              <td><button class="btn btn-success">Add</button></td>
            </tr>
          </g:form>
        </tfoot>
      </table>
    </dd>
  </g:if>

  <g:if test="${grailsApplication.config.feature.directUpload}">
    <dt><g:annotatedLabel owner="${d}" property="name">Load Title List</g:annotatedLabel> (<a href="https://github.com/k-int/gokb-phase1/wiki/Title-List-Upload-Format">Format</a>)</dt>
    <dd class="container">
      <g:form controller="folderUpload" action="processSubmission" method="post" enctype="multipart/form-data">
        <input type="hidden" name="ownerOrg" value="${d.id}"/>
        <div class="input-group" >
          <span class="input-group-btn">
            <span class="btn btn-default btn-file">
              Browse <input type="file" id="submissionFile" name="submissionFile" onchange='$("#upload-file-info").html($(this).val());' />
            </span>
          </span>
          <span class='form-control' id="upload-file-info"><label for="submissionFile" >Select a file...</label></span>
          <span class="input-group-btn">
            <button type="submit" class="btn btn-primary">Upload</button>
          </span>
        </div>
      </g:form>
    </dd>
    </g:if>
  </g:if>

  <asset:script type="text/javascript" >
    function applyForMembership(oid) {
      $.ajax({
            url: '/gokb/ajaxSupport/applyForUserorg/' + oid,
            dataType:"json"
          }).done(function(data) {
            if ( data.result == 'OK' ) {
              
              var uoTable = document.getElementById("uomembers");
              var newRow = uoTable.insertRow(-1);
              var newUser = newRow.insertCell(0);
              var newStatus = newRow.insertCell(1);
              var newRole = newRow.insertCell(2);
              var newActions = newRow.insertCell(3);

              newUser.innerHTML = data.item.user;
              newStatus.innerHTML = data.item.status;
              newRole.innerHTML = data.item.role;

              $('#applyBtn').hide();
            }
            else {
              console.log(data.message);
            }
          });
    }
  </asset:script>

</dl>
