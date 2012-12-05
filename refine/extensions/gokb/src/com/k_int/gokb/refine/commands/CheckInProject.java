package com.k_int.gokb.refine.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tools.tar.TarOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.refine.A_RefineAPIBridge;
import com.k_int.gokb.refine.RefineAPICallback;

import com.google.refine.ProjectManager;
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
            // Get the project.
            final Project project = getProject(request);
            
            // Get files sent to this method.
            Map<String, Object> files = files(request);
            
            // Should the changes made to this project be sent back up to the server?
            if ("true".equalsIgnoreCase(request.getParameter("update"))) { 
                
                // Ensure the project has been saved.
                pm.ensureProjectSaved(project.id);
                
                // Create byte array output stream.
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                
                // Create a GZipped Tar Stream to create our tar.gz file.
                TarOutputStream tgzout = new TarOutputStream(
                   new GZIPOutputStream(
                      out
                   )
                );
                
                try {
                    
                    // Export the project to the output stream.
                    pm.exportProject(project.id, tgzout);
                    
                    // Add the stream to the map.
                    files.put("projectFile", out);
                } finally {
                    
                    // Ensure we close our output stream.
                    tgzout.close();
                }
            }
            
            // Now we need to pass the data to the API.
            postToAPI("projectCheckin", params(request), files, new RefineAPICallback(){

                @Override
                protected void onSuccess(InputStream result)
                        throws Exception {
                    
                    // Remove the project from refine.
                    pm.deleteProject(project);
                    
                    // Redirect to the refine index.
                    redirect(response, "/");
                }
                
            });
        	
        } catch (Exception e) {
            
            // Respond with the error page.
            respondWithErrorPage(request, response, e.getLocalizedMessage(), e);
            
        } finally {
            // Make sure we clear the busy flag.
            pm.setBusy(false);
        }
    }
}