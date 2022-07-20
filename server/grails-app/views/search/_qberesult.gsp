<%@ page import="grails.converters.JSON"%>

<g:set var="counter" value="${offset}" />

<g:if test="${ request.isAjax() }">

  <g:render template="pagination" model="${params}" />

  <table class="table table-striped table-condensed table-bordered">
    <thead>
      <tr class="inline-nav">
        <g:each in="${qbeConfig.qbeResults}" var="c">
          <g:if test="${!params.hide || !params.hide.contains(c.qpEquiv)}">
            <th style="white-space:nowrap;">
              <g:if test="${c.sort}">
                <g:if test="${params.sort==c.sort && params.order=='asc'}">
                  <g:link params="${params+['sort':c.sort,order:'desc']}">
                    ${c.heading}
                    <i class="fas fa-sort-up"></i>
                  </g:link>
                </g:if>
                <g:else>
                  <g:if test="${params.sort==c.sort && params.order=='desc'}">
                    <g:link params="${params+['sort':c.sort,order:'asc']}">
                      ${c.heading}
                      <i class="fas fa-sort-down"></i>
                    </g:link>
                  </g:if>
                  <g:else>
                    <g:link params="${params+['sort':c.sort,order:'desc']}">
                      ${c.heading}
                      <i class="fas fa-sort"></i>
                    </g:link>
                  </g:else>
                </g:else>
              </g:if>
              <g:else>
                ${c.heading}
              </g:else>
            </th>
          </g:if>
        </g:each>
      </tr>
    </thead>
    <tbody>
      <g:each in="${rows}" var="r">
        <g:set var="r" value="${r}" />
        <tr class="${++counter==det ? 'success':''}">
          <!-- Row ${counter} -->
          <g:each in="${r.cols}" var="c">
            <td>
              <g:if test="${c.link != null }">
                <g:link controller="resource"
                    action="show"
                    id="${c.link}"
                    params="${c.link_params!=null?groovy.util.Eval.x(pageScope,c.link_params):[]}">
                    ${c.value}
                </g:link>
              </g:if>
              <g:else>
                ${c.value}
              </g:else>
            </td>
          </g:each>
        </tr>
      </g:each>
    </tbody>
  </table>
</g:if>
<g:else>
  <div class="batch-all-info" style="display:none;"></div>

  <g:render template="pagination" model="${params}" />

  <g:form controller="workflow" action="action" method="post" params="${params}" class='action-form' >

    <table class="table table-striped table-condensed table-bordered">
      <thead>
        <tr>
          <th></th>
          <g:each in="${qbeConfig.qbeResults}" var="c">
            <g:set var="colcode" value="${baseClass + '.' + c.heading}" />
            <g:set var="colmsg" value="${message(code: colcode, default:c.heading)}" />
            <g:if test="${!params.hide || !params.hide.contains(c.qpEquiv)}">
              <th style="white-space:nowrap"><g:if test="${c.sort}">
                  <g:if test="${params.sort==c.sort && params.order=='asc'}">
                    <g:link params="${params+['sort':c.sort,order:'desc']}">
                      ${colmsg == colcode ? c.heading : colmsg}
                      <i class="fas fa-sort-up"></i>
                    </g:link>
                  </g:if>
                  <g:else>
                    <g:if test="${params.sort==c.sort && params.order=='desc'}">
                      <g:link params="${params+['sort':c.sort,order:'asc']}">
                        ${colmsg == colcode ? c.heading : colmsg}
                        <i class="fas fa-sort-down"></i>
                      </g:link>
                    </g:if>
                    <g:else>
                      <g:link params="${params+['sort':c.sort,order:'desc']}">
                        ${colmsg == colcode ? c.heading : colmsg}
                        <i class="fas fa-sort"></i>
                      </g:link>
                    </g:else>
                  </g:else>
                </g:if> <g:else>
                  ${colmsg == colcode ? c.heading : colmsg}
                </g:else></th>
            </g:if>
          </g:each>
          <g:if test="${request.user?.showQuickView?.value=='Yes'}">
            <th></th>
          </g:if>
        </tr>
      </thead>
      <tbody>
        <g:each in="${rows}" var="r">
          <g:if test="${r != null }">
            <g:set var="row_obj" value="${r.obj}" />
            <tr class="${++counter==det ? 'success':''}">
              <!-- Row ${counter} -->
              <td style="vertical-align:middle;"><g:if
                  test="${row_obj?.isEditable() && row_obj.respondsTo('availableActions')}">
                  <g:set var="al"
                    value="${new JSON(row_obj?.userAvailableActions()).toString().encodeAsHTML()}" />
                  <input type="checkbox" name="bulk:${r.oid}"
                    data-actns="${al}" class="obj-action-ck-box" />
                </g:if> <g:else>
                  <input type="checkbox"
                    title="${ !row_obj?.isEditable() ? 'Component is read only' : 'No actions available' }"
                    disabled="disabled" readonly="readonly" />
                </g:else></td>
              <g:each in="${r.cols}" var="c">
                <td style="vertical-align:middle;"><g:if test="${ c.link != null }">
                    <g:link controller="resource"
                      action="show"
                      id="${c.link}"
                      params="${c.link_params!=null?groovy.util.Eval.x(pageScope,c.link_params):[]}">
                      ${c.value}
                    </g:link>
                  </g:if>
                  <g:elseif test="${c.value instanceof Boolean}">
                    <g:if test="${c.value}">
                      <i class="fa fa-check-circle text-success fa-lg" title="${message(code:'default.boolean.true')}"></i>
                    </g:if>
                    <g:else>
                      <i class="fa fa-times-circle text-danger fa-lg" title="${message(code:'default.boolean.false')}"></i>
                    </g:else>
                  </g:elseif>
                  <g:else>
                    ${c.value}
                  </g:else></td>
              </g:each>
              <g:if test="${request.user?.showQuickView?.value=='Yes'}">
                <td>
                  <g:link class="btn btn-xs btn-default pull-right desktop-only" controller="search"
                    action="index" params="${params+['det':counter]}"><i class="fa fa-eye" ></i></g:link>
                </td>
              </g:if>
            </tr>
          </g:if>
          <g:else>
            <tr>
              <td>Error - Row not found</td>
            </tr>
          </g:else>
        </g:each>
      </tbody>
    </table>
    </g:form>
  <g:render template="pagination" model="${params + [dropup : true]}" />
</g:else>

<script language="JavaScript">
function jumpToPage() {
  alert("jump to page");
}
</script>
