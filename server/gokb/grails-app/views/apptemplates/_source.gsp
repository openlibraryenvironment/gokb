<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<g:if test="${d.id != null}">
  <dl class="dl-horizontal">

    <div class="control-group">
      <dt>URL</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="url"/></dd>
    </div>

  </dl>
</g:if>
<script language="JavaScript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
