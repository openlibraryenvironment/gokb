package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.k_int.gokb.refine.operations.AddRowsOperation;

import com.google.refine.commands.Command;
import com.google.refine.model.AbstractOperation;
import com.google.refine.model.Project;
import com.google.refine.process.Process;

public class AddRowsCommand extends Command {
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            Project project = getProject(request);

            // Read in the number of rows to append. 
            Integer number;
            try {
                number = Integer.parseInt(request.getParameter("number"));
            } catch (Exception e) {

                // Default to 1.
                number = 1;
            }

            // Create the operation.
            AbstractOperation op = new AddRowsOperation(number);

            // Process the operation.
            Process process = op.createProcess(project, new Properties());

            // Respond to the client.
            performProcessAndRespond(request, response, project, process);
        } catch (Exception e) {
            respondException(response, e);
        }
    }
}