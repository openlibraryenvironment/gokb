
<h3> <input type="radio" name="pr.prob_res_${prob?.problemSequence}.ResolutionOption" value="newTitleInTHG"/> Option 1 : Create a new title</h3>
<p>Use this option to create a new title - optionally using the correct identifier. Link the new title to the
title history of the title identified so that we can match easily in the future. If you know dates for the new title,
suggest them in the form below, otherwise accept the defaults. 
<select name="pr.prob_res_${prob?.problemSequence}.HistoryChoiceOption">
  <option value="AddPre">Add as preceeding</option>
  <option value="AddPro">Add as proceeding</option>
</select>
Using <input type="date" name="pr.prob_res_${prob?.problemSequence}.HistoryChoiceDate"></input>
</p>

<h3> <input type="radio" name="pr.prob_res_${prob?.problemSequence}.ResolutionOption" value="variantName" checked /> Option 2 : Add the new title string as a variant</h3>
<p>The supplied title really is just a radically different variant of the canonical one - add it as a variant to
resolve this issue.</p>
</select>
