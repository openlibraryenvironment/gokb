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
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        ProjectManager.singleton.setBusy(true);
        try {
            Properties options = ParsingUtilities.parseUrlParameters(request);

            long projectID = Project.generateID();
            logger.info("Checking out GOKb project into Refine project {}", projectID);

            downloadGOKbProject(request, options);

            ProjectManager.singleton.loadProjectMetadata(projectID);

            ProjectMetadata pm = ProjectManager.singleton.getProjectMetadata(projectID);
            if (pm != null) {
                if (options.containsKey("project-name")) {
                    String projectName = options.getProperty("project-name");
                    if (projectName != null && projectName.length() > 0) {
                        pm.setName(projectName);
                    }
                }

                redirect(response, "/project?project=" + projectID);
            } else {
                respondWithErrorPage(request, response, "Failed to import project. Reason unknown.", null);
            }
        } catch (Exception e) {
            respondWithErrorPage(request, response, "Failed to import project", e);
        } finally {
            ProjectManager.singleton.setBusy(false);
        }
    }
    
    protected void downloadGOKbProject (
            HttpServletRequest    request,
            Properties            options
    ) throws Exception {
        
        url
        
        URL url = new URL(urlString);
        URLConnection connection = null;

        try {
            connection = url.openConnection();
            connection.setConnectTimeout(5000);
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