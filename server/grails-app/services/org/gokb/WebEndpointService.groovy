package org.gokb

import grails.gorm.transactions.Transactional

import java.util.regex.Pattern

@Transactional
class WebEndpointService {

    static Pattern FIXED_DATE_ENDING_PLACEHOLDER_PATTERN = ~/\{YYYY-MM-DD\}\.(tsv|txt)$/

    def extractFtpUrlParts (String webEndpointUrl, String sourceUrl) {
        def result = [:]

        boolean isDateMasked = false
        if (sourceUrl =~ FIXED_DATE_ENDING_PLACEHOLDER_PATTERN) {
            log.debug("### IS MIT DATUMSMASKIERUNG ###")
            isDateMasked = true
        }

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
