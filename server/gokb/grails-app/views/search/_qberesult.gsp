<%@ page import="grails.converters.JSON" %>

<g:set var="counter" value="${offset}" />

<g:form controller="workflow" action="action">
  <table class="table table-striped">
   <caption>Search results</caption>
    <thead>
      <tr>
        <th>row#</th>
        <g:each in="${qbeConfig.qbeResults}" var="c">
          <th>${c.heading}</th>
        </g:each>
        <th>Actions [bulk]</th>
      </tr>
    </thead>
    <tbody>
      <g:each in="${rows}" var="r">
        <tr class="${++counter==det ? 'success':''}">
          <td>${counter}</td>
          <g:each in="${qbeConfig.qbeResults}" var="c">
            <td>${groovy.util.Eval.x(r, 'x.' + c.property)}</td>
          </g:each>
          <td>
            <g:link class="btn" controller="resource" action="show" id="${r.class.name+':'+r.id}">Show</g:link>
            <g:link class="btn" controller="search" action="index" params="${params+['det':counter]}">Preview -></g:link>
            <g:if test="${r.respondsTo('availableActions')}">
             <g:set var="al" value="${new JSON(r.availableActions()).toString().replaceAll("\"","'")}"/>  <!--"-->
              <input type="checkbox" name="bulk:${r.class.name}:${r.id}" data-actns="${al}"/>
            </g:if>
          </td>
        </tr>
      </g:each>
    </tbody>
  </table>
  <div class="pull-right well">
    <h4>Available actions for selected rows</h4>
    <select name="selectedBulkAction">
      <option value="delete">Delete</option>
    </select>
    <button type="submit" class="btn">Action</button>
  </div>
</g:form>
