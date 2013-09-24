package com.k_int.gokb.refine.functions;

import java.util.List;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONWriter;

import com.google.refine.grel.Function;
import com.google.refine.model.Project;

public class CaseInsensitiveCellLookup implements Function {

    @Override
    public String call(Properties bindings, Object[] args) {

        String ret = null;

        if (args.length >= 1) {
            Object columnNameObj = args[0];
            if (columnNameObj != null && columnNameObj instanceof String) {
                
                // Cast to String
                String columnName = (String)columnNameObj;
                
                Project project = (Project) bindings.get("project");
                List<String> cols = project.columnModel.getColumnNames();
                String actualName = null;
                for (int i=0; i<cols.size() && (actualName == null); i++) {
                    
                  // Get the current name
                  String col = cols.get(i);
                  
                  // Get each column name and check for case-insensitive match.
                  if (columnName.equalsIgnoreCase(col)) {

                    // Set the actual name
                    actualName = col;
                  }
                }
                
                return actualName;
            }
        }

        return ret;
    }


    @Override
    public void write(JSONWriter writer, Properties options)
            throws JSONException {

        writer.object();
        writer.key("description"); writer.value("Converts the column name passed in into the correct case if present in the data.");
        writer.key("params"); writer.value("Column name.");
        writer.key("returns"); writer.value("Either the actual column name or null if not found.");
        writer.endObject();
    }
}
