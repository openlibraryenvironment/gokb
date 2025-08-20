<dl class="dl-horizontal">
    <dt> <g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="name" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="url">Base URL</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="url" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="supplyMethod">Methode</g:annotatedLabel> </dt>
    <dd> <g:xEditableRefData owner="${d}" field="supplyMethod" config='Source.DataSupplyMethod' /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="ba_username">Benutzername</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="ba_username" /> </dd>

    <dt> <g:annotatedLabel owner="${d}" property="ba_password">Passwort</g:annotatedLabel> </dt>
    <dd> <g:xEditable class="ipe" owner="${d}" field="ba_password" /> </dd>
</dl>