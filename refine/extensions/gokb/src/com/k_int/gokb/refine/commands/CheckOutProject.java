package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.ProjectManager;
import com.google.refine.ProjectMetadata;
import com.google.refine.commands.Command;
import com.google.refine.model.Project;
import com.google.refine.util.ParsingUtilities;


public class CheckOutProject extends Command {
    final static Logger logger = LoggerFactory.getLogger("GOKb-checkout-project_command");

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ProjectManager pm = ProjectManager.singleton;
        pm.setBusy(true);
        try {
            Properties options = ParsingUtilities.parseUrlParameters(request);

            long projectID = Project.generateID();
            logger.info("Checking out GOKb project into Refine project {}", projectID);

            downloadGOKbProject(request, options, projectID);

            pm.loadProjectMetadata(projectID);
            
            ProjectMetadata meta = pm.getProjectMetadata(projectID);
            
            if (meta != null) {
                 // Move to project page.
                redirect(response, "/project?project=" + projectID);
            } else {
                logger.error("Failed to import project. Reason unknown.");
            }
        } catch (Exception e) {
            respondException(response, e);
        } finally {
            ProjectManager.singleton.setBusy(false);
        }
    }
    
    /**
     * Import the Project from the GOKb repository
     */
    protected void downloadGOKbProject (
            HttpServletRequest    request,
            Properties            options,
            long projectID
    ) throws Exception {
        
        String urlString = "";
        for (Object key : options.keySet()) {
            urlString += 
              (!"".equals(urlString) ? "&" : "?") + key + "=" + ParsingUtilities.encode(options.getProperty((String)key, ""));
        }
        
        urlString = "http://localhost:8080/gokb/api/projectCheckout" + urlString;
        
        URL url = new URL(urlString);
        URLConnection connection = null;

        try {
            connection = url.openConnection();
            connection.setConnectTimeout(180000);
            connection.connect();
        } catch (Exception e) {
            throw new Exception("Cannot connect to " + urlString, e);
        }

        InputStream inputStream = null;
        try {
            inputStream = connection.getInputStream();
        } catch (Exception e) {
            throw new Exception("Cannot retrieve content from " + url, e);
        }

        try {
            ProjectManager.singleton.importProject(projectID, inputStream, !urlString.endsWith(".tar"));
        } finally {
            inputStream.close();
        }
    }
}