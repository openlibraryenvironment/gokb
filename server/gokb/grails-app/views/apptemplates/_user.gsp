<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<g:if test="${d.id != null}">
  <dl class="dl-horizontal">

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="username">User Name</g:annotatedLabel></dt>
      <dd>${d.username}</dd>
    </div>

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="displayName">Display Name</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="displayName"/></dd>
    </div>

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="email">Email</g:annotatedLabel></dt>
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
