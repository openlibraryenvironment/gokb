<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h1>

<dl class="dl-horizontal">
  <div class="control-group">
    <dt>Name</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="name"/></dd>
  </div>

  <div class="control-group">
    <dt>Website</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="website"/></dd>
  </div>

  <div class="control-group">
    <dt>Email</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="email"/></dd>
  </div>

  <div class="control-group">
    <dt>Phone Number</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="phoneNumber"/></dd>
  </div>

  <div class="control-group">
    <dt>Other Details</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="otherDetails"/></dd>
  </div>

  <div class="control-group">
    <dt>Address Line 1</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="addressLine1"/></dd>
  </div>

  <div class="control-group">
    <dt>Address Line 2</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="addressLine2"/></dd>
  </div>

  <div class="control-group">
    <dt>City</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="city"/></dd>
  </div>

  <div class="control-group">
    <dt>Zip/Postcode</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="zipPostcode"/></dd>
  </div>

  <div class="control-group">
    <dt>Region</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="region"/></dd>
  </div>

  <div class="control-group">
    <dt>Owner Org</dt>
    <dd><g:manyToOneReferenceTypedown owner="${d}" field="org" name="${comboprop}" baseClass="org.gokb.cred.Org">${d.org?.name?:''}</g:manyToOneReferenceTypedown></dd>
  </div>

  <g:if test="${d.id != null}">


    <div class="control-group">
      <dt>Country</dt>
      <dd><g:xEditableRefData owner="${d}" field="country" config='Country' /></dd>
    </div>

  </g:if>
</dl>
<script language="JavaScript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
