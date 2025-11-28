package org.gokb

import grails.gorm.transactions.Transactional

@Transactional
class WebEndpointService {

    def extractFtpUrlParts (String webEndpointUrl, String sourceUrl) {
        def result = [:]

        //we dont need the protocol
        String hostname = webEndpointUrl.replace("ftp://", "")
        String filename = ""
        String directory = "/"

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

        result.hostname = hostname
        result.filename = filename
        result.directory = directory


        result

    }






}
