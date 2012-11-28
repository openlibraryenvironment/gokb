package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.refine.A_RefineAPIBridge;
import com.k_int.gokb.refine.RefineAPICallback;

import com.google.refine.ProjectManager;
import com.google.refine.ProjectMetadata;
import com.google.refine.model.Project;


public class CheckInProject extends A_RefineAPIBridge {
    final static Logger logger = LoggerFactory.getLogger("GOKb-checkin-project_command");

    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {       

    	// Get the project manager and flag that it is busy.
        final ProjectManager pm = ProjectManager.singleton;
        pm.setBusy(true);
        try {
            Project project = getProject(request);
//            pm.ensureProjectSaved(project.id);
//
//            response.setHeader("Content-Type", "application/x-gzip");
//
//            OutputStream os = response.getOutputStream();
//            try {
//                gzipTarToOutputStream(project, os);
//            } finally {
//                os.close();
//            }
        	
        } catch (Exception e) {
            
            // Respond with the error page.
            respondWithErrorPage(request, response, e.getLocalizedMessage(), e);
        } finally {
        	// Make sure we clear the busy flag.
            pm.setBusy(false);
        }
    }
}