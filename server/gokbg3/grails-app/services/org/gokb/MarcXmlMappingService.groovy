package org.gokb


import org.gokb.cred.*
import groovy.xml.MarkupBuilder

class MarcXmlMappingService {
  def dateFormatService

  def mapJournal(titleInstance) {
    def e_issn = titleInstance.ids.findAll { it.namespace == IdentifierNamespace.findByValue('eissn') }
    def p_issn = titleInstance.ids.findAll { it.namespace == IdentifierNamespace.findByValue('eissn') }
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)

    xml.'collection' {
      'record' {
        'leader'('     nas a       8c 4500')
        'controllfield'(tag: '005', dateFormatService.formatDenseTimestamp(titleInstance.dateCreated))
        'datafield'(tag: '022', ind1: '' ind2: '') {
          'subfield'(code: 'a', e_issn)
        }
        'datafield'(tag: '024', ind1: '8' ind2: '') {
          'subfield'(code: 'a', titleInstance.uuid)
        }
        'datafield'(tag: '245', ind1: '0' ind2: '0') {
          'subfield'(code: 'a', titleInstance.name)
        }
        'datafield'(tag: '776', ind1: '0' ind2: '8') {
          'subfield'(code: 'a', p_issn)
        }
        titleInstance.publisher.each { pub ->
          'datafield'(tag: '880', ind1: '3' ind2: '1', pub.name)
        }
      }
    }

    writer.close()
    return writer.toString()
  }
}