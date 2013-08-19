<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<g:if test="${d.id != null}">
  <dl class="dl-horizontal">

    <div class="control-group">
      <dt>License URL</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="url"/></dd>
    </div>

    <div class="control-group">
      <dt>License Doc</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="file"/></dd>
    </div>

   <div class="control-group">
      <dt>License Type</dt>
      <dd><g:xEditableRefData owner="${d}" field="type" config='License.Type' /></dd>
    </div>


  </dl>
</g:if>
<script language="JavaScript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
