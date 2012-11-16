package com.k_int.gokb.refine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.tools.tar.TarOutputStream;

import com.google.refine.ProjectManager;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Row;

public class ProjectUtil {
    
    private static final String DIGEST_TYPE = "MD5";

    public static byte[] fingerprintProject(Project project) throws IOException, NoSuchAlgorithmException {
        ProjectManager pm = ProjectManager.singleton;
        
        // Ensure that the project has been saved.
        pm.ensureProjectSaved(project.id);
        
        // Raw byte output stream.
        ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
        
        // Tar output stream for refine.
        TarOutputStream projOut = new TarOutputStream(
           rawOut
        );
        
        // Create the digest.
        MessageDigest md = MessageDigest.getInstance(DIGEST_TYPE);
        
        // Set our project to export to the output stream.
        pm.exportProject(project.id, projOut);
        
        // Digest the project file.
        return md.digest(rawOut.toByteArray());
    }
    
    public static byte[] fingerprintProjectData(Project project) throws IOException, NoSuchAlgorithmException {
                
        // Create the digest.
        MessageDigest md = MessageDigest.getInstance(DIGEST_TYPE);
        
        // Add each Cell to the Digest.
        for ( Row row : project.rows ) {
            for (Cell cell : row.cells) {
                Serializable val = null;
                if (cell != null && cell.value != null) val = cell.value.toString();
                val = (val == null ? "|" : val + "|");
                md.update(val.toString().getBytes());
            }
            md.update("\n".getBytes());
        }
        
        // Digest the data.
        return md.digest();
    }
}