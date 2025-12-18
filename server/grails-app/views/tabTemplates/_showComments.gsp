<g:set var="editable" value="${ d.isEditable() && ((request.curator != null ? request.curator.size() > 0 : true) || (params.curationOverride == "true" && request.user.isAdmin())) }" />
<div class="tab-pane" id="comments">
  <g:if test="${d.id != null}">
    <dl>
      <dt>
        <g:annotatedLabel owner="${d}" property="comments">Comments</g:annotatedLabel>
      </dt>
      <dd>
        <table class="table table-striped table-bordered">
          <thead>
            <tr>
              <th>Comment</th>
              <th>Language</th>
              <g:if test="${ showActions }">
              <th>Actions</th>
              </g:if>
            </tr>
          </thead>
          <tbody>
            <g:each in="${d.comments}" var="v">
              <tr>
                <td>
                  <pre>${v.value}</pre>
                </td>
                <td>${v.language}</td>
                <td>
                  <g:if test="${ editable && showActions }">
                    <g:link controller="ajaxSupport"
                      class="confirm-click"
                      data-confirm-message="Are you sure you wish to delete this Comment?"
                      action="deleteComment"
                      id="${v.id}"
                    >
                      Delete
                    </g:link>
                  </g:if>
                </td>
              </tr>
            </g:each>
          </tbody>
        </table>

        <g:if test="${editable}">
          <h4>
            <g:annotatedLabel owner="${d}" property="addComment">Add Comment</g:annotatedLabel>
          </h4>
          <dl class="dl-horizontal">
            <g:form controller="ajaxSupport" action="addToCollection"
              class="form-inline">
              <input type="hidden" name="__context"
                value="${d.class.name}:${d.id}" />
              <input type="hidden" name="__newObjectClass"
                value="org.gokb.cred.KBComponentComment" />
              <input type="hidden" name="__recip" value="owner" />
              <input type="hidden" name="fragment" value="comments" />
              <dt class="dt-label">Comment</dt>
              <dd>
                <textarea class="form-control" name="value" ></textarea>
              </dd>
              <dt class="dt-label">Language</dt>
              <dd>
                <g:simpleReferenceTypedown class="form-control" name="language"
                  baseClass="org.gokb.cred.RefdataValue"
                  filter1="KBComponent.Language" />
              </dd>
              <dt></dt>
              <dd>
                <button type="submit"
                  class="btn btn-default btn-primary">Add</button>
              </dd>
            </g:form>
          </dl>
        </g:if>
      </dd>
    </dl>
  </g:if>
  <g:else>
    Comments can be added after the creation process is finished.
  </g:else>
</div>
