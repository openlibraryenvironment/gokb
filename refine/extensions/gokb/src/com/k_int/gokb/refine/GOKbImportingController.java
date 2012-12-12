package com.k_int.gokb.refine;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.RefineServlet;
import com.google.refine.importing.DefaultImportingController;
import com.google.refine.importing.ImportingJob;
import com.google.refine.importing.ImportingManager;
import com.google.refine.model.Project;
import com.google.refine.util.ParsingUtilities;


public class GOKbImportingController extends DefaultImportingController{
    
    final static Logger logger = LoggerFactory.getLogger("GOKb-importing-controller");

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        
        // Do the default action first.
        super.doPost(request, response);

        /*
         * The uploaded file is in the POST body as a "file part". If
         * we call request.getParameter() then the POST body will get
         * read and we won't have a chance to parse the body ourselves.
         * This is why we have to parse the URL for parameters ourselves.
         */
        Properties parameters = ParsingUtilities.parseUrlParameters(request);
        String subCommand = parameters.getProperty("subCommand");
        if ("load-raw-data".equals(subCommand)) {
            // We are creating a project. Need to get at the job and parameters,
            // to get the file for MD5 hashing.
            
            long jobID = Long.parseLong(parameters.getProperty("jobID"));
            final ImportingJob job = ImportingManager.getJob(jobID);
            final RefineServlet servlet = this.servlet;
            if (job != null) {
                String state;
                try {
                    
                    // Examine the job and wait for the project to be updated.
                    state = (String) job.config.get("state");
                    while (!"error".equals(state) && !"ready".equals(state)) {
                        // Wait for project to be created or for error to occur.
                    }
                    
                    if ("ready".equals(state) && Boolean.TRUE.equals(job.config.get("hasData"))) {
                        // Project has been created... Let's create our hash.
                        Project p = job.project;
                        
                        // Hash the current data, before the project is created.
                        job.metadata.setCustomMetadata(
                          "md5-hash",
                          RefineUtil.byteArrayToHex(
                            RefineUtil.hashProjectData(p, servlet.getModule("gokb").getTemporaryDir())
                          )
                        );
                    }
                } catch (Exception e) {
                    logger.error("Error while trying to cretae file MD5 hash.", e);
                }
            }
        }
    }
}
