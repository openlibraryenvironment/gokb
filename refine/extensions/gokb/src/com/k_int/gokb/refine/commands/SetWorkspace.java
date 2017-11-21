package com.k_int.gokb.refine.commands;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.module.GOKbModuleImpl;

import com.google.refine.commands.Command;


public class SetWorkspace extends Command {
    final static Logger logger = LoggerFactory.getLogger("GOKb-set-workspace_command");

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            
            // Get the workspace ID.
            int workspace_id = Integer.parseInt(request.getParameter("ws"));
            
            // Set the active workspace.
            GOKbModuleImpl.singleton.setActiveWorkspace(workspace_id);
            
            // Redirect to the front page.
            JSONWriter writer = new JSONWriter(response.getWriter());
            writer.object();
            writer.key("status"); writer.value(true);
            writer.endObject();
            
        }  catch (JSONException e) {
          respondException(response, e);
        }
    }
}