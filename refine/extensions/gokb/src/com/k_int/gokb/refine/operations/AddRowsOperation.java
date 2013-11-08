package com.k_int.gokb.refine.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.google.refine.history.HistoryEntry;
import com.google.refine.model.AbstractOperation;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.google.refine.model.changes.MassRowChange;
import com.google.refine.operations.OperationRegistry;

public class AddRowsOperation extends AbstractOperation {

    private int _number;

    static public AbstractOperation reconstruct(Project project, JSONObject obj) throws Exception {
        return new AddRowsOperation(
            obj.getInt("number")
        );
    }

    public AddRowsOperation( int number ) {
        _number = number;
    }

    @Override
    public void write(JSONWriter writer, Properties options)
            throws JSONException {
        writer.object();
        writer.key("op"); writer.value(OperationRegistry.s_opClassToName.get(this.getClass()));
        writer.key("description"); writer.value("Prepended " + _number + " blank row" + (_number > 1 ? "s" : ""));
        writer.key("number"); writer.value(_number);
        writer.endObject();
    }

    @Override
    protected String getBriefDescription(Project project) {
        return "Prepended " + _number + " blank row" + (_number > 1 ? "s" : "");
    }

    @Override
    protected HistoryEntry createHistoryEntry(Project project, long historyEntryID) throws Exception {

        // Create a new list of rows.
        List<Row> newRows = new ArrayList<Row>();

        // Add n new blank rows.
        for (int i=0; i<_number; i++) {
            newRows.add(new Row(0));
        }

        // Add the other rows now.
        newRows.addAll(project.rows);

        return new HistoryEntry(
            historyEntryID,
            project, 
            getBriefDescription(null), 
            this, 
            new MassRowChange(newRows)
        );
    }
}
