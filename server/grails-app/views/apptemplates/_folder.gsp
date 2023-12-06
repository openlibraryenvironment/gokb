<div id="content">
  <dl class="dl-horizontal">

    <dt><g:annotatedLabel owner="${d}" property="name">Folder Name</g:annotatedLabel></dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="name" /></dd>

    <g:if test="${d.id != null}">

      <ul id="tabs" class="nav nav-tabs">
        <li class="active"><a href="#folderContents" data-toggle="tab">Folder Contents</a></li>
        <li class=""><a href="#availability" data-toggle="tab">Availability</a></li>
        <li class=""><a href="#loadTitleList" data-toggle="tab">Load Title List</a></li>
      </ul>

      <div id="my-tab-content" class="tab-content">


        <div class="tab-pane active" id="folderContents">

          <g:link class="display-inline" 
                  controller="search" 
                  action="index"
                  params="[qbe:'g:folderContents', qp_folder_id:d.id, hide:['qp_folder_id', 'SEARCH_FORM']]"
                  id="folderContentsSearch">Contents of this folder</g:link>
        </div>

        <div class="tab-pane " id="availability">
          <pre>
            ${d.getAvailability()}
          </pre>
        </div>


        <div class="tab-pane " id="loadTitleList">
          <g:form controller="folderUpload" action="processSubmission" method="post" enctype="multipart/form-data">
            <input type="hidden" name="defaultFolder" value="${d.id}"/>
            <input type="hidden" name="ownerOrg" value="${d.owner?.id}"/>
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
        </div>


      </div>

    </g:if>

  </dl>
</div>
