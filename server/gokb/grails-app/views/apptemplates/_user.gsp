<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<g:if test="${d.id != null}">
  <dl class="dl-horizontal">

    <div class="control-group">
      <dt>User Name</dt>
      <dd>${d.username}</dd>
    </div>

    <div class="control-group">
      <dt>Display Name</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="displayName"/></dd>
    </div>

    <div class="control-group">
      <dt>Email</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="email"/></dd>
    </div>

  </dl>
</g:if>

<script language="JavaScript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
