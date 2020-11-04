<g:render template="/apptemplates/kbcomponent"
          model="${[d: displayobj, rd: refdata_properties, dtype: 'KBComponent']}"/>

<g:if test="${d.id != null}">
    <dl class="dl-horizontal">
        <dt>
            <g:annotatedLabel owner="${d}" property="url">URL</g:annotatedLabel>
        </dt>
        <dd>
            <g:xEditable class="ipe" owner="${d}" field="url"/>
            <g:if test="${d.url}">
                &nbsp;<a href="${d.url}" target="new">Follow Link</a>
            </g:if>

        </dd>
        <dt>
            <g:annotatedLabel owner="${d}" property="frequency">Frequency</g:annotatedLabel>
        </dt>
        <dd>
            <g:xEditable class="ipe" owner="${d}" field="frequency"/>
        </dd>
        <dt>
            <g:annotatedLabel owner="${d}" property="defaultSupplyMethod">Default Supply Method</g:annotatedLabel>
        </dt>
        <dd>
            <g:xEditableRefData owner="${d}" field="defaultSupplyMethod"
                                config="Source.DataSupplyMethod"/>
        </dd>
        <dt>
            <g:annotatedLabel owner="${d}" property="defaultDataFormat">Default Data Format</g:annotatedLabel>
        </dt>
        <dd>
            <g:xEditableRefData owner="${d}" field="defaultDataFormat"
                                config="Source.DataFormat"/>
        </dd>
        <dt>
            <g:annotatedLabel owner="${d}" property="targetNamespace">Target Namespace</g:annotatedLabel>
        </dt>
        <dd>
            <g:manyToOneReferenceTypedown owner="${d}" field="targetNamespace"
                                          baseClass="org.gokb.cred.IdentifierNamespace">
                ${d.targetNamespace?.name}
            </g:manyToOneReferenceTypedown>
        </dd>
        <dt>
            <g:annotatedLabel owner="${d}" property="automaticUpdates">automatic Updates</g:annotatedLabel>
        </dt>
        <dd>
            <g:checkBox owner="${d}" field="automaticUpdates" value="${automaticUpdates}"
                        checked="${automaticUpdates ? automaticUpdates : false}"
                        name="_automaticUpdates"/>
        </dd>
        <dt>
            <g:annotatedLabel owner="${d}" property="ezb&&zdb">match data with</g:annotatedLabel>
        </dt>
        <dd>
            <g:annotatedLabel owner="${d}" property="ezbMatch">ezb</g:annotatedLabel>
            <g:checkBox owner="${d}" field="ezbMatch" value="${ezbMatch}" checked="${ezbMatch ? ezbMatch : false}"
                        name="_ezbMatch"/>
            &nbsp;
            <g:annotatedLabel owner="${d}" property="zdbMatch">zdb</g:annotatedLabel>
            <g:checkBox owner="${d}" field="zdbMatch" value="${zdbMatch}" checked="${zdbMatch ? zdbMatch : false}"
                        name="_zdbMatch"/>
        </dd>
        <dt>
            <g:annotatedLabel owner="${d}" property="lastRun">last Run</g:annotatedLabel>
        </dt>
        <dd>
            ${d.lastRun ? d.lastRun : 'never'}
        </dd>
        <dt>
            <g:annotatedLabel owner="${d}" property="responsibleParty">Responsible Party</g:annotatedLabel>
        </dt>
        <dd>
            <g:manyToOneReferenceTypedown owner="${d}" field="responsibleParty"
                                          baseClass="org.gokb.cred.Org">
                ${d.responsibleParty?.name ?: ''}
            </g:manyToOneReferenceTypedown>
        </dd>
    </dl>
</g:if>
