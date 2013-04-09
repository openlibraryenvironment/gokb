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
             <g:set var="al" value="${new JSON(r.availableActions()).toString().encodeAsHTML()}"/>  <!--"-->
              <input type="checkbox" name="bulk:${r.class.name}:${r.id}" data-actns="${al}" class="obj-action-ck-box" onChange="javascript:updateAvailableActions();"/>
            </g:if>
          </td>
        </tr>
      </g:each>
    </tbody>
  </table>
  <div class="pull-right well">
    <h4>Available actions for selected rows</h4>
    <select id="selectedBulkAction" name="selectedBulkAction">
      <option value="delete">Delete</option>
    </select>
    <button type="submit" class="btn">Action</button>
  </div>
</g:form>

<script language="javascript">
  function updateAvailableActions() {
    // alert("update actions");

    var allActionsAvailable = []; 
			
		// Step through each checked box.
		$('input.obj-action-ck-box:checked').each(function(i) {
			var elem = $(this);
			if (i == 0) {
				var json = elem.attr('data-actns');
				
				// Set all actions available to this objects actions.
				allActionsAvailable = $.parseJSON(json);
			} else {
				var json = elem.attr('data-actns');
				
				var elementActions = $.parseJSON(json);

				// Filter the array using a callback that checks that this element actions contains
				// the object.
				$.grep(allActionsAvailable, function(action, index) {
					return $.inArray(action, elementActions) > -1
				});
			}
		});

		var opts = $('select#selectedBulkAction').prop("options");
		opts.length = 0;
		
		// Add the options to the dropdown.
		$.each(allActionsAvailable, function (index, action) {
			opts[index] = new Option(action.label, action.code);
		});
    
    // Steve ;) this function needs to iterate over all checked checkboxes with class obj-action-ck-box and build up a list of
    // actions. Only actions which are applicable to all checked items should be in the final combo (So the combo only shows actions
    // that can be bulk applied to all checked rows, some rows may allow an action, some may not depending on values in the object.
    // For example, a title with status "Deleted" can't be deleted, but one with status "Current" can be. Once we have the actions
    // at the intersection of all checked rows, we need to fill the selectedBulkAction combo with the selected rows.
    //
    // Format of the data-actns property in each checkbox element is [{'code':'object::delete','label':'Delete'},{'code':'title::transfer','label':'Transfer'}]
    // So the option element needs to have a value of the code, and text of the label.
    
  }
</script>
