package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONWriter;

import com.k_int.gokb.refine.RefineUtil;

import com.google.refine.commands.Command;
import com.google.refine.model.Project;


public class FingerprintProjectCommand extends Command {
    
    private void serveInternally(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            Project project = getProject(request);
            byte[] fp = RefineUtil.fingerprintProjectData(project);
            
            // Convert the byte array to a Hex String
            StringBuilder hexString = new StringBuilder();
            for (int i=0; i<fp.length; i++) {
                String hex = Integer.toHexString(0xFF & fp[i]);
                if (hex.length() == 1) {
                    // could use a for loop, but we're only dealing with a single byte
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            // Write the JSON out.
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "application/json");

            Writer w = response.getWriter();
            JSONWriter writer = new JSONWriter(w);
            
            writer.object();
            writer.key("fingerprint");
            writer.value(hexString.toString());
            writer.endObject();
            
            w.flush();
            w.close();
        } catch (Exception e) {
            respondException(response, e);
        }
    }
    
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        serveInternally(request, response);
    }
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        serveInternally(request, response);
    }
}
