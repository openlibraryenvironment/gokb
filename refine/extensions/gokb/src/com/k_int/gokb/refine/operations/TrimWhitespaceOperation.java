package com.k_int.gokb.refine.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.google.refine.history.HistoryEntry;
import com.google.refine.model.AbstractOperation;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.google.refine.model.changes.MassRowChange;
import com.google.refine.operations.OperationRegistry;

public class TrimWhitespaceOperation extends AbstractOperation {

  static public AbstractOperation reconstruct(Project project, JSONObject obj) throws Exception {
    return new TrimWhitespaceOperation();
  }

  // Do nothing.
  public TrimWhitespaceOperation() {}

  @Override
  public void write(JSONWriter writer, Properties options)
      throws JSONException {
    writer.object();
    writer.key("op"); writer.value(OperationRegistry.s_opClassToName.get(this.getClass()));
    writer.key("description"); writer.value("Trim white-space from all rows");
    writer.endObject();
  }

  @Override
  protected String getBriefDescription(Project project) {
    return "Trim white-space from all rows";
  }

  @Override
  protected HistoryEntry createHistoryEntry(Project project, long historyEntryID) throws Exception {
    
    // The current rows and columns.
    List<Row> rows = project.rows;

    // Create a new list of rows.
    List<Row> newRows = new ArrayList<Row>(rows.size());
    
    // Go through each row.
    for (Row row : rows) {
      
      // New row.
      Row newRow = new Row(row.cells.size());
      
      // Add to the list of new rows.
      newRows.add(newRow);
      
      // For each column per row.
      for (int i=0; i<row.cells.size(); i++) {
        
        // Current Cell.
        Cell cell = row.getCell(i);
        
        // For each cell, trim any whitespace if empty.
        Object oldValue = cell != null ? cell.value : null;
        
        // Check if null.
        if (oldValue == null) {
          
          // Just add a null cell.
          newRow.setCell(i, null);
          
        } else {
          
          // Not null, check to see if it's a String.
          if (oldValue instanceof String) {
            
            // Trim the value.
            String newValue = ((String)oldValue).trim();
            if ("".equals(newValue)) {
              
              // Add a null cell.
              newRow.setCell(i, null);
              
            } else {
              
              // Add the new value.
              newRow.setCell(
                i,
                new Cell(newValue, cell.recon)
              );
            }
            
          } else {
            
            // Add existing value.
            newRow.setCell(i, cell);
            
          }
        }
      }
    }

    // Add the history entry.
    return new HistoryEntry(
      historyEntryID,
      project, 
      getBriefDescription(null), 
      this, 
      new MassRowChange(newRows)
    );
  }
}
