package com.k_int.gokb.refine.functions;

import java.util.Properties;

import org.json.JSONException;
import org.json.JSONWriter;

import com.google.refine.grel.Function;
import com.google.refine.model.Project;
import com.k_int.gokb.module.util.ProjectUtils;

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
                
                ret = ProjectUtils.caseInsensitiveColumnName(project, columnName);
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
