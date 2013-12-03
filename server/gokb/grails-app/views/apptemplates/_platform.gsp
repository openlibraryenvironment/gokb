<r:require modules="gokbstyle" />
<r:require modules="editable" />

<h3>
  ${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}
</h3>

<div id="content">

  <dl class="dl-horizontal">
    <dt>
      <g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel>
    </dt>
    <dd>
      <g:xEditable class="ipe" owner="${d}" field="name" />
    </dd>
    <dt>
      <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
    </dt>
    <dd>
      <g:xEditableRefData owner="${d}" field="status"
        config="KBComponent.Status" />
    </dd>
    <dt>
      <g:annotatedLabel owner="${d}" property="shortCode">Short Code</g:annotatedLabel>
    </dt>
    <dd>
      <g:xEditable class="ipe" owner="${d}" field="shortcode" />
    </dd>
    <dt>
      <g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel>
    </dt>
    <dd>
      <g:manyToOneReferenceTypedown owner="${d}" field="source"
        baseClass="org.gokb.cred.Source" />
    </dd>

  <dt><g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel></dt>
  <dd><g:xEditableRefData owner="${d}" field="editStatus" config='KBComponent.EditStatus' /></dd>

  </dl>

  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#platformdetails" data-toggle="tab">Platform Details</a></li>
  </ul>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="platformdetails">

      <dl class="dl-horizontal">
        <dt>
          <g:annotatedLabel owner="${d}" property="primaryURL">Primary URL</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditable class="ipe" owner="${d}" field="primaryUrl">
            ${d.primaryUrl}
          </g:xEditable>
        </dd>

        <dt>
          <g:annotatedLabel owner="${d}" property="software">Software</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditableRefData owner="${d}" field="software"
            config='Platform.Software' />
        </dd>

        <dt>
          <g:annotatedLabel owner="${d}" property="service">Service</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditableRefData owner="${d}" field="service"
            config='Platform.Service' />
        </dd>

        <dt>
          <g:annotatedLabel owner="${d}" property="authentication">Authentication</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditableRefData owner="${d}" field="authentication"
            config='Platform.AuthMethod' />
        </dd>
      </dl>
    </div>

  </div>
  <g:render template="componentStatus" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />

</div>

<script type="text/javascript">
  $(document).ready(function() {
    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
