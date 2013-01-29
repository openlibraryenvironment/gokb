var testData = [
  {
	label	: "A previously ingested name",
	value	: "guid:1234-5678-91011",
  },
  {
	label	: "Previous document name",
	value	: "docrow:10",
  },
];

// add the autocomplete
$(document).ready(function(){
	
	$(".data-table-cell-editor-editor").each(function(){
		$(this).autocomplete({
			  source: testData
		});
	});
});