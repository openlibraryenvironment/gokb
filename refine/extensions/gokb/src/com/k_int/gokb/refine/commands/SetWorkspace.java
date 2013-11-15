package com.k_int.gokb.refine.commands;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.ProjectManager;
import com.google.refine.commands.Command;
import com.k_int.gokb.refine.GOKbModuleImpl;


public class SetWorkspace extends Command {
    final static Logger logger = LoggerFactory.getLogger("GOKb-set-workspace_command");

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
      
        // Get the project manager and flag that it is busy.
        final ProjectManager pm = ProjectManager.singleton;
        pm.setBusy(true);
        try {
            
            // Get the workspace ID.
            int workspace_id = Integer.parseInt(request.getParameter("ws"));
            
            // Set the active workspace.
            GOKbModuleImpl.singleton.setActiveWorkspace(workspace_id);
            
            // Redirect to the front page.
            response.sendRedirect("/");

        } finally {
            // Make sure we clear the busy flag.
            pm.setBusy(false);
        }
    }
}