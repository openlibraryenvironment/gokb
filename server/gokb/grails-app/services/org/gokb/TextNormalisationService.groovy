package org.gokb

class TextNormalisationService {

  static transactional = false

  def normalise(value) {
    return value?.toLowerCase()?.trim()
  }
}
