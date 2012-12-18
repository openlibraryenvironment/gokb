package com.k_int.gokb.refine.commands;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.refine.A_RefineAPIBridge;


public class AddProject extends A_RefineAPIBridge {
    final static Logger logger = LoggerFactory.getLogger("GOKb-add-project_command");

    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        
    }
}