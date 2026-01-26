package org.gokb

import grails.gorm.transactions.Transactional

import java.util.regex.Pattern

@Transactional
class WebEndpointService {

    def extractFtpUrlParts (String webEndpointUrl, String sourceUrl) {
        def result = [:]

        String protocol = ""
        if(webEndpointUrl.startsWith("ftp://")){
            protocol = "ftp://"
        }
        else if(webEndpointUrl.startsWith("ftps://")){
            protocol = "ftps://"
        }

        //we dont need the protocol
        String hostname = webEndpointUrl.replace(protocol, "")
        String filename = ""
        String directory = "/"
        String completeUrl = ""

        if (hostname?.contains("/")) {
            String[] parts = hostname.split("/")
            hostname = parts[0]
            for(int i = 1; i < parts.length; i++){
                directory = directory.concat(parts[i] + "/")
            }
        }

        if (sourceUrl?.startsWith("/")) {
            sourceUrl = sourceUrl.substring(1)
        }

        if(sourceUrl?.contains("/")){
            String[] parts = sourceUrl.split("/")
            filename = parts[parts.length - 1]
            directory = directory + sourceUrl.substring(0, sourceUrl.lastIndexOf("/") + 1)
        }
        else {
            filename = sourceUrl
        }

        completeUrl = protocol + hostname + directory + filename

        result.hostname = hostname
        result.filename = filename
        result.directory = directory
        result.complete = completeUrl


        result

    }






}
