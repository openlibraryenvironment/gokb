<g:set var="editable" value="${ d.isEditable() && ((request.curator != null ? request.curator.size() > 0 : true) || (params.curationOverride == "true")) }" />
<div class="tab-pane" id="altnames">
  <g:if test="${d.id != null}">
    <dl>
      <dt>
        <g:annotatedLabel owner="${d}" property="alternateNames">Alternate Names</g:annotatedLabel>
      </dt>
      <dd>
        <table class="table table-striped table-bordered">
          <thead>
            <tr>
              <th>Alternate Name</th>
              <th>Status</th>
              <th>Variant Type</th>
              <th>Locale</th>
                        <g:if test="${ showActions }">
                        <th>Actions</th>
                        </g:if>
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
                <g:if test="${ editable && showActions }">
                <td>
                              <g:link controller="workflow" action="AuthorizeVariant" id="${v.id}">Make Authorized</g:link>,
                              <g:link controller="workflow" class="confirm-click" data-confirm-message="Are you sure you wish to delete this Variant?"
                                action="DeleteVariant" id="${v.id}" >Delete</g:link>
                          </td>
                          </g:if>
              </tr>
            </g:each>
          </tbody>
        </table>

        <g:if test="${editable}">
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
                <g:simpleReferenceTypedown class="form-control" name="locale"
                  baseClass="org.gokb.cred.RefdataValue"
                  filter1="KBComponentVariantName.Locale" />
              </dd>
              <dt>Variant Type</dt>
              <dd>
                <g:simpleReferenceTypedown class="form-control" name="variantType"
                  baseClass="org.gokb.cred.RefdataValue"
                  filter1="KBComponentVariantName.VariantType" />
              </dd>
              <dt></dt>
              <dd>
                <button type="submit"
                  class="btn btn-default btn-primary btn-sm ">Add</button>
              </dd>
            </g:form>
          </dl>
        </g:if>
      </dd>
    </dl>
  </g:if>
</div>
