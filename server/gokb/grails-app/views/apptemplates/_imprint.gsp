<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h1>

<dl class="dl-horizontal">

  <dt><g:annotatedLabel owner="${d}" property="name">Imprint Name</g:annotatedLabel></dt>
  <dd><g:xEditable class="ipe" owner="${d}" field="name"/></dd>

</dl>

<script type="text/javascript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
