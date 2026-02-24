<dl class="dl-horizontal">
    <dt> <g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="name" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="url">Base URL</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="url" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="epUsername">Benutzername</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="epUsername" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="epPassword">Passwort</g:annotatedLabel> </dt>
    <!-- <dd> <g:xEditable type="password" class="ipe" owner="${d}" field="epPassword" /> </dd> -->
    <dd> <g:xEditable type="password" class="ipe" owner="${d}" field="epPassword" /> </dd>

</dl>

<script>
    const x = document.querySelector('[data-name="epPassword"]')
    if (x.getHTML().length > 0) {
        x.innerHTML = "*****"
    }
</script>