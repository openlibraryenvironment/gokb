package com.k_int.gokb.refine;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.ProjectManager;
import com.google.refine.ProjectMetadata;
import com.google.refine.importing.DefaultImportingController;
import com.google.refine.importing.ImportingJob;
import com.google.refine.importing.ImportingManager;
import com.google.refine.importing.ImportingUtilities;
import com.google.refine.util.JSONUtilities;
import com.google.refine.util.ParsingUtilities;


public class GOKbImportingController extends DefaultImportingController {
    
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
        
        long jobID = Long.parseLong(parameters.getProperty("jobID"));
        final ImportingJob job = ImportingManager.getJob(jobID);
        
        if ("load-raw-data".equals(subCommand)) {
            // We are creating a project. Need to get at the job and parameters,
            // to get the file for MD5 hashing.
            
            if (job != null) {
            
                // MD5 has in another thread.
                new Thread() {
                    
                    @Override
                    public void run(){
                        try {
                            JSONObject conf = job.config;
                            
                            // Examine the job and wait for the project to be updated.
                            String state = (String) conf.get("state");
                            while (job.updating) { 
                              sleep(100);
                            }
                            
                            // If the only one file has been uploaded then let's MD5 it.
                            JSONObject rec = conf.getJSONObject("retrievalRecord");
                            if ("ready".equals(state) && rec.getInt("uploadCount") == 1) {
                                
                                // We have only 1 file uploaded.
                                JSONObject file = (JSONObject)rec.getJSONArray("files").get(0);
                            
                                if (file.get("location") != null) {
                                    
                                    String MD5 = RefineUtil.byteArrayToHex(
                                       RefineUtil.hashFile(
                                           ImportingUtilities.getFile(job, file.getString("location"))
                                       )
                                    );
                                    
                                    // Add to the Job Config so it does not get deleted.
                                    JSONUtilities.safePut(conf, "hash", MD5);
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Error while trying to create file MD5 hash.", e);
                        }
                    }
                }.start();
                
            }
        } else if ("create-project".equals(subCommand)) {
            
            // Wait for the project to be created and then set our MD5 metadata attribute.
            while (job.updating) {
                /* Do nothing */
            }
            
            // Add the hash to the project metadata.            
            try {
                
                Long pid = job.config.getLong("projectID");
                
                if (pid != null) {
                    ProjectMetadata md = ProjectManager.singleton.getProjectMetadata(pid);
                    
                    md.setCustomMetadata("hash", job.config.getString("hash"));
                }
            } catch (JSONException e) {
                logger.error("Error while trying to set the MD5 hash.", e);
            }
        }
    }
}