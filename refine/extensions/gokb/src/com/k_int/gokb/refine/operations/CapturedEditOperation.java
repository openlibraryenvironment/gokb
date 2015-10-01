/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
 * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

package com.k_int.gokb.refine.operations;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.refine.model.AbstractOperation;
import com.google.refine.model.Column;
import com.google.refine.model.Project;
import com.google.refine.model.changes.CellChange;
import com.google.refine.operations.OnError;
import com.google.refine.operations.cell.TextTransformOperation;

public class CapturedEditOperation extends TextTransformOperation {

  static public AbstractOperation reconstruct(Project project, JSONObject obj) throws Exception {
    JSONObject engineConfig = obj.getJSONObject("engineConfig");
    
    JSONArray basedOn = obj.getJSONArray("basedOn");
    String[] basedOnCols = new String[basedOn.length()];
    for (int i=0; i<basedOnCols.length; i++) {
      basedOnCols[i] = basedOn.getString(i);
    }

    return new CapturedEditOperation(
      engineConfig,
      obj.getString("columnName"),
      obj.getString("expression"),
      stringToOnError(obj.getString("onError")),
      obj.getBoolean("repeat"),
      obj.getInt("repeatCount"),
      basedOnCols,
      obj.getString("value")
    );
  }

  private final String[] cols;
  private final String val;
  
  public CapturedEditOperation(
      JSONObject engineConfig, 
      String columnName, 
      String expression, 
      OnError onError,
      boolean repeat,
      int repeatCount,
      String[] basedOn,
      String value
      ) {
    super(engineConfig, columnName, expression, onError, repeat, repeatCount);
    cols = basedOn;
    val = value;
  }

  @Override
  protected String getBriefDescription(Project project) {
    return "Captured edit on cells in column " + _columnName + " based on " + cols.length + " column values.";
  }

  @Override
  protected String createDescription(Column column,
      List<CellChange> cellChanges) {

    String desc = "Captured edit setting " + cellChanges.size() + 
        " cells in column " + column.getName() + " to " + val + " based on values in columns " + "'" + cols[0] + "'";
    
    for (int i=1; i<cols.length; i++) {
      desc += ((i == (cols.length - 1)) ? " and " : ", ") + "'" + cols[i] + "'"; 
    }
    return desc;
  }
}
