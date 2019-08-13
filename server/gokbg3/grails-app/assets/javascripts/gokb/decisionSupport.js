$(document).ready(function(){
  $('#tab-decision-suppport .vote a').click(function(e){

    // The ID.
    var id = $(this).attr('id');
    var id_comp = id.split("_");

    setAppliedCriterion($(this), id_comp[0], id_comp[1], id_comp[2], id_comp[3]);

    // Prevent the default action.
    e.stopImmediatePropagation();
    return false;
  });

    $(document).on('click','.noteDelete',function (e) {
        var agreed = confirm("Are you sure you wish to delete?");
        if (agreed == true) {
            var elememt = $(this);
            var note    = elememt.data('note');
            deleteNote(elememt,note);
        }

        e.stopImmediatePropagation();
    });

});

function setAppliedCriterion(target, component_id, criterion_id, v ,c) {
  $.ajax({
    url: gokb.config.baseUrl+'/ajaxSupport/appliedCriterion?comp='+component_id+'&crit='+criterion_id+'&val='+v,
    dataType:"json"
  }).done(function(data) {
    if (data && "status" in data && data.status == 'OK') {
      // Remove the existing border colours
      var removeClasses = "text-neutral text-negative text-contentious text-positive";
      target.siblings().removeClass(removeClasses);
      target.addClass("text-" + c + " selected");
      target.parents("td").next().find("span.DSAuthor").each(function (index, element) {
          if($(element).text() === data.username)
          {
              var authorComment = $(element).parent().next().children("p.triangle-border");
              authorComment.removeClass(removeClasses + " border-neutral border-negative border-contentious border-positive");
              authorComment.addClass("border-" + c +" text-" + c);
          }
      });

      var newNotes = $(".newNoteContainer");
      if(newNotes) {
          newNotes.each(function () {
              var element = $(this);
              element.removeClass(removeClasses + " border-neutral border-negative border-contentious border-positive");
              element.addClass("border-" + c + " text-" + c);
          });
      }

      if ( !data.changedFrom || data.changedFrom != v ) {
        var selectedCount = target.parents(".DSVote").siblings(".otherVoters").find(".count-" + v);
        var addedNew = 1 + + selectedCount.text();
        
        selectedCount.text(addedNew.toString());

        if ( data.changedFrom ) {
          var oldCount = target.parent().next().find('.count-' + data.changedFrom);
          var removedNew = -1 + + oldCount.text();
          
          oldCount.text(removedNew.toString());
        }
      }
    }
  });
}

function voteColour(currentVote) {
    var retval;
    if(currentVote != 'undefined' || currentVote != null)
    {
        var css = currentVote.removeClass('selected').attr('class');
        switch (css)
        {
            case "text-negative":
                retval = " text-negative border-negative";
                break;
            case "text-positive":
                retval = " text-positive border-positive";
                break
            default:
                retval = " text-contentious border-contentious";
                break;
        }
    }
    currentVote.addClass('selected'); //add back incase further comments added
    return retval;
}

function addNote(id, username, institution, displayname) {
  console.log('Adding note: ',id,username,institution, displayname);
  var v   = $('#'+id+'_newnote').val();
  var i   = (institution == null || institution.length == 0) ? 'N/A' : institution;
  var currentVote = $('#currentVote'+id+' a.selected'); //current users vote, if it exists that is!
  var cssf = voteColour(currentVote);

  $.ajax({
    // libs and culture: 0894-8631
    url: gokb.config.baseUrl+'/ajaxSupport/criterionComment?comp='+id+'&comment='+v,
    dataType:"json"
  }).done(function(data) {
      $('#'+id+'_notestable').prepend("<li class=\"list-group-item\"><span id='org.gokb.cred.DSNote:"+ data.newNote +":note' class='newNote xEditableValue  editable editable-pre-wrapped editable-click' data-pk='org.gokb.cred.DSNote:"+data.newNote+"' data-name='note' data-tpl='<textarea wrap=\"off\"></textarea>' data-type='textarea' data-url='/gokbLabs/ajaxSupport/editableSetValue'>"+ v +"</span></li>")
      $('.newNote').editable(); //Xeditable on newly created notes
      $('#'+id+'_newnote').val('');
  });

  return false;
}


function deleteNote(target,note) {
  $.ajax({
    url: gokb.config.baseUrl+'/ajaxSupport/criterionCommentDelete?note='+note,
    dataType:"json"
  }).done(function(data) {
    if(data.status == 'OK')
    {
        //Remove previous RED, AMBER, GREEN CSS classes and replace with GREY deleted
        var removeClasses  = 'text-neutral text-negative text-contentious text-positive border-neutral border-negative border-contentious border-positive xEditableValue editable editable-pre-wrapped editable-click';
        var deletedClasses = 'text-deleted border-deleted'
        var editClasses    = 'xEditableValue editable editable-pre-wrapped editable-click'
        var message        = 'Number of deleted notes: ';

        //remove/add CSS, remove delete functionality
        var paraTag        = target.parent("p");
        var cid            = target.data('comp');
        paraTag.removeClass(removeClasses);
        paraTag.addClass(deletedClasses);
        target.prev().removeClass(editClasses).unbind('click'); //unbind delete behaviour

        //Message append
        var deletedList = $('#'+cid+'_deleted');
        var rowCount = deletedList.children('dt').length + 1;
        deletedList.prev().text(message + rowCount);

        //move from active list to deleted
        var dd = target.parent().parent();
        var dt = dd.prev("dt").detach();
        dd = dd.detach();
        deletedList.prepend(dt,dd); //add to top of DL
        target.remove(); //delete button
    }
  });

  return false;
}

function meToo(oid, result) {
  console.log("Me too %o",oid);
  $.ajax({
    url: gokb.config.baseUrl+'/ajaxSupport/plusOne?object='+oid,
    dataType:"json"
  }).done(function(data) {
    if(data.status == 'OK') {
      console.log("OK %o",data);
      if ( result ) {
        $(result).html(""+data.newcount);
      }
    }
  });
}
