<h1>
  ${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}
</h1>

<dl class="dl-horizontal">
  <g:if test="${d.id != null}">
    <div class="control-group">
      <dt>
        <g:annotatedLabel owner="${d}" property="provider">Provider</g:annotatedLabel>
      </dt>
      <dd>
        ${d.provider?.name?:'Provider Not Set'}
      </dd>
    </div>

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel></dt>
      <dd><g:manyToOneReferenceTypedown owner="${d}" field="source" baseClass="org.gokb.cred.Source"/></dd>
    </div>

    <g:if test="${d.lastProject}">
      <div class="control-group">
        <dt><g:annotatedLabel owner="${d}" property="lastProject" >Last Project</g:annotatedLabel></dt>
        <dd>
          <g:link controller="resource" action="show"
            id="${d.lastProject?.getClassName()+':'+d.lastProject?.id}">
            ${d.lastProject?.name}
          </g:link>
        </dd>
      </div>
    </g:if>


    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="listVerifier">List Verifier</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="listVerifier" /></dd>
    </div>

    <div class="control-group">
      <dt><g:annotatedLabel owner="${d}" property="listVerifierDate">List Verifier Date</g:annotatedLabel></dt>
      <dd><g:xEditable class="ipe" owner="${d}" type="date" field="listVerifiedDate" /></dd>
    </div>

  </g:if>
</dl>

<div id="content">

  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#packagedetails" data-toggle="tab">Package Details</a></li>
    <li><a href="#titledetails" data-toggle="tab">Titles</a></li>
    <li><a href="#altnames" data-toggle="tab">Alt Names</a></li>
  </ul>

  <div id="my-tab-content" class="tab-content">

    <div class="tab-pane active" id="packagedetails">
      <dl class="dl-horizontal">
        <g:render template="refdataprops" contextPath="../apptemplates" model="${[d:(d), rd:(rd), dtype:(dtype)]}" />
      </dl>
    </div>

    <div class="tab-pane" id="titledetails">
      <g:link class="display-inline" controller="search" action="index" params="[qbe:'g:tipps', qp_pkg_id:d.id]" id="">Titles in this package</g:link>
    </div>

    <div class="tab-pane" id="altnames">
      <div class="control-group"><dl>
        <dt><g:annotatedLabel owner="${d}" property="alternateNames">Alternate Names</g:annotatedLabel></dt>
        <dd>
          <table class="table table-striped table-bordered">
            <thead>
              <tr>
                <th>Alternate Name</th>
                <th>Status</th>
                <th>Variant Type</th>
                <th>Locale</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${d.variantNames}" var="v">
                <tr>
                  <td>
                    ${v.variantName}
                  </td>
                  <td><g:xEditableRefData owner="${v}" field="status" config='KBComponentVariantName.Status' /></td>
                  <td><g:xEditableRefData owner="${v}" field="variantType" config='KBComponentVariantName.VariantType' /></td>
                  <td><g:xEditableRefData owner="${v}" field="locale" config='KBComponentVariantName.Locale' /></td>
                </tr>
              </g:each>
            </tbody>
          </table>

          <h4>
            <g:annotatedLabel owner="${d}" property="addVariantName">Add Variant Name</g:annotatedLabel>
          </h4>
          <dl class="dl-horizontal">
            <g:form controller="ajaxSupport" action="addToCollection"
              class="form-inline">
              <input type="hidden" name="__context"
                value="${d.class.name}:${d.id}" />
              <input type="hidden" name="__newObjectClass"
                value="org.gokb.cred.KBComponentVariantName" />
              <input type="hidden" name="__recip" value="owner" />
              <dt>Variant Name</dt>
              <dd>
                <input type="text" name="variantName" />
              </dd>
              <dt>Locale</dt>
              <dd>
                <g:simpleReferenceTypedown name="locale"
                  baseClass="org.gokb.cred.RefdataValue"
                  filter1="KBComponentVariantName.Locale" />
              </dd>
              <dt>Variant Type</dt>
              <dd>
                <g:simpleReferenceTypedown name="variantType"
                  baseClass="org.gokb.cred.RefdataValue"
                  filter1="KBComponentVariantName.VariantType" />
              </dd>
              <dt></dt>
              <dd>
                <button type="submit" class="btn btn-primary btn-small">Add</button>
              </dd>
            </g:form>
          </dl>
        </dd>
      </dl></div>

    </div>
  </div>
<g:render template="componentStatus" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
</div>

<script language="JavaScript">
  $(document).ready(function() {
    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
