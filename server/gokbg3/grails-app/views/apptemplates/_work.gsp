<div id="content">

  <dl class="dl-horizontal">
    <dt> <g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="name" /> </dd>
    <dt> <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel> </dt>
    <dd> <g:xEditableRefData owner="${d}" field="status" config="KBComponent.Status" /> </dd>
    <dt> <g:annotatedLabel owner="${d}" property="status">Instances</g:annotatedLabel> </dt>
    <g:if test="${d.id != null && d.instances}">
      <dd> 
        <table class="table table-striped">
          <thead>
            <tr>
              <th>Title</th>
            </tr>
          </thead>
          <tbody>
            <g:each in="${d.instances}" var="i">
              <tr>
                <td><g:link controller="resource" action="show" id="${i.class.name}:${i.id}"> ${i.name} </g:link></td>
              </tr>
            </g:each>
          </tbody>
      </dd> 
    </g:if>
  </dl>
</div>
