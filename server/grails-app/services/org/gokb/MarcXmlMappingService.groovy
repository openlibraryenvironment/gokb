package org.gokb


import org.gokb.cred.*
import groovy.xml.MarkupBuilder

class MarcXmlMappingService {
  def dateFormatService

  def mapZdbJournal(titleInstance) {
    def e_issn = titleInstance.ids.findAll { it.namespace == IdentifierNamespace.findByValue('eissn') }[0]?.value
    def p_issn = titleInstance.ids.findAll { it.namespace == IdentifierNamespace.findByValue('issn') }[0]?.value
    def pub = titleInstance.currentPublisher
    def startYear = titleInstance.publishedFrom ? dateFormatService.formatDate(titleInstance.publishedFrom).substring(0,4) : null
    def endYear = titleInstance.publishedTo ? dateFormatService.formatDate(titleInstance.publishedTo).substring(0,4) : null
    def ctrl_flde = dateFormatService.formatDate(titleInstance.dateCreated).substring(0,4) + 'c'

    if (titleInstance.publishedFrom) {
      ctrl_flde += startYear
    }
    else {
      ctrl_flde += '0000'
    }

    if (titleInstance.publishedTo) {
      ctrl_flde += endYear
    }
    else {
      ctrl_flde += '9999'
    }

    ctrl_flde += 'xx ||p|o |||||||||1    c'

    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)

    xml.'collection' {
      'record' {
        'leader'('     nas a       8c 4500')
        'controlfield'(tag: '005', dateFormatService.formatDenseTimestamp(titleInstance.dateCreated))
        'controlfield'(tag: '007', 'cr||||||||||||')
        'controlfield'(tag: '008', ctrl_flde)
        'datafield'(tag: '022', ind1: '' ind2: '') {
          'subfield'(code: 'a', e_issn)
        }
        'datafield'(tag: '024', ind1: '8' ind2: '') {
          'subfield'(code: 'a', titleInstance.uuid)
        }
        'datafield'(tag: '245', ind1: '0' ind2: '0') {
          'subfield'(code: 'a', titleInstance.name)
          'subfield'(code: 'c', pub.name)
        }
        'datafield'(tag: '300', ind1: '' ind2: '') {
          'subfield'(code: 'a', 'Online-Ressource')
        }
        'datafield'(tag: '336', ind1: '' ind2: '') {
          'subfield'(code: 'a', 'Text')
          'subfield'(code: 'b', 'txt')
          'subfield'(code: '2', 'rdacontent')
        }
        'datafield'(tag: '337', ind1: '' ind2: '') {
          'subfield'(code: 'a', 'Computermedien')
          'subfield'(code: 'b', 'c')
          'subfield'(code: '2', 'rdamedia')
        }
        'datafield'(tag: '338', ind1: '' ind2: '') {
          'subfield'(code: 'a', 'Online-Ressource')
          'subfield'(code: 'b', 'cr')
          'subfield'(code: '2', 'rdcarrier')
        }
        if (startYear) {
          def date_range = startYear + (endYear ? ' - ' + endYear : '')
          'datafield'(tag: '362', ind1: '0' ind2: '') {
            'subfield'(code: 'a', date_range)
          }

          if (endYear) {
            'datafield'(tag: '363', ind1: '0' ind2: '0') {
              'subfield'(code: '8', '1.1\\x')
              'subfield'(code: 'i', startYear)
            }
            'datafield'(tag: '363', ind1: '1' ind2: '0') {
              'subfield'(code: '8', '1.2\\x')
              'subfield'(code: 'i', endYear)
            }
          }
        }
        'datafield'(tag: '655', ind1: '' ind2: '7') {
          'subfield'(code: '0', '(DE-588)4067488-5')
          'subfield'(code: '0', 'https://d-nb.info/gnd/4067488-5')
          'subfield'(code: '0', '(DE-101)040674886')
          'subfield'(code: 'a', 'Zeitschrift')
          'subfield'(code: '2', 'gnd-content')
        }
        if (p_issn) {
          'datafield'(tag: '776', ind1: '0' ind2: '8') {
            'subfield'(code: 'a', 'Erscheint auch als')
            'subfield'(code: 'n', 'Druck-Ausgabe')
            'subfield'(code: 't', titleInstance.name)
            'subfield'(code: 'x', p_issn)
          }
        }
      }
    }

    writer.close()
    return writer.toString()
  }
}