<div id="content">
  <dl class="dl-horizontal">

    <dt><g:annotatedLabel owner="${d}" property="name">Folder Name</g:annotatedLabel></dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="name" /></dd>

    <g:if test="${d.id != null}">

      <ul id="tabs" class="nav nav-tabs">
        <li class="active"><a href="#folderContents" data-toggle="tab">Folder Contents</a></li>
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

        <div class="tab-pane " id="loadTitleList">
          Load title list
        </div>


      </div>

    </g:if>

  </dl>
</div>
