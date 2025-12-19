package org.gokb

import grails.gorm.transactions.Transactional

import java.util.regex.Pattern

@Transactional
class WebEndpointService {

    static Pattern FIXED_DATE_ENDING_PLACEHOLDER_PATTERN = ~/\{YYYY-MM-DD\}\.(tsv|txt)$/
    static Pattern VARIABLE_DATE_ENDING_PLACEHOLDER_PATTERN = ~/([12][0-9]{3}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]))\.(tsv|txt)$/

    def extractFtpUrlParts (String webEndpointUrl, String sourceUrl, boolean dynamic_date) {
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
