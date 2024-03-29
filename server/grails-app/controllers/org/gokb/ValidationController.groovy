package org.gokb

import grails.converters.*

import org.gokb.cred.IdentifierNamespace

class ValidationController {

  def validationService
  def TSVIngestionService

  def index() {
    def result = [
      message: "Please select a specific endpoint!",
      endpoints: [
        kbart: [method: 'POST', description: 'Validates full KBART files and returns a report.', contentType: "multipart/form-data", content: 'submissionFile', pars: ['namespace']],
        componentName: [method: 'GET', description: 'Validates new component names for form and uniqueness', pars: ['value', 'componentType']],
        identifier: [method: 'GET', description: 'Validates an identifier value for a specified namespace', pars: ['value', 'namespace']],
        url: [method: 'POST', description: 'Validates a URL', contentType: "application/json", content: [value: '<url>']]
      ]
    ]

    render result as JSON
  }

  def kbart() {
    def result = [result: 'OK', errors: [:]]
    def multipart_file = request.getFile("submissionFile")
    def strict = params.boolean('strict') ?: false

    if (multipart_file && !multipart_file.isEmpty()) {
      def title_id_namespace = null
      def file_info = TSVIngestionService.analyseFile(multipart_file.getInputStream())

      if (!['UTF-8', 'US-ASCII'].contains(file_info.encoding)) {
        result.errors.encoding = [message: "The encoding of this file was identified as ${file_info.encoding}, but must be UTF-8!", messageCode: "kbart.errors.encoding", args: []]
      }

      if (params.namespace) {
        title_id_namespace = params.int('namespace') ? IdentifierNamespace.get(params.int('namespace')) : IdentifierNamespace.findByValue(params.namespace)

        if (!title_id_namespace) {
          result.errors.namespace = [message: "Unable to reference provided namespace for column title_id!", messageCode: "kbart.errors.namespaceNotFound", args: []]
        }
      }

      result.report = validationService.generateKbartReport(multipart_file.getInputStream(), title_id_namespace, strict)

      if (result.report.valid == false) {
        result.result = 'ERROR'

        if (result.report.errors.missingColumns) {
          result.errors.missingColumns = result.report.errors.missingColumns
        }
      }
    }
    else {
      result.result = 'ERROR'
      result.errors.file = [message: "No/empty file was supplied as 'submissionFile'!", messageCode: "validation.noFile", args: []]
    }

    render result as JSON
  }

  def componentName() {
    def result = validationService.checkNewComponentName(params.value, params.componentType)

    render result as JSON
  }

  def identifier() {
    def result = [result: 'OK']
    def namespace = params.int('namespace') ? IdentifierNamespace.get(params.int('namespace')) : IdentifierNamespace.findByValue(params.namespace)

    if (!namespace) {
      result.result = 'ERROR'
      result.errors = [namespace: [message: "Unable to reference namespace ${params.namespace}", messageCode: "validation.unknownNamespace", pars: [params.namespace]]]
    }
    else {
      def validation_result = validationService.checkIdForNamespace(params.value, namespace)

      if (!validation_result) {
        result.result = 'ERROR'
        result.errors = [value: [message: "Value ${params.value} is not valid", messageCode: "validation.invalid", pars: [params.value]]]
      }
    }

    render result as JSON
  }

  def url() {
    def result = [result: 'OK']
    def reqBody = request.JSON

    if (reqBody && reqBody.value) {
      def validation_result = validationService.checkUrl(reqBody.value, params.boolean('replaceDate') ?: true)

      if (!validation_result) {
        result.result = 'ERROR'
        result.errors = [value: [message: "Provided value ${reqBody.value} is not a valid URL", messageCode: "validation.urlForm", pars: [reqBody.value]]]
      }
    }
    else {
      result.result = 'ERROR'
      result.result = [value: [message: "No value provided via JSON object!"]]
    }

    render result as JSON
  }
}
