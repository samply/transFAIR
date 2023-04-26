package de.samply.transfyr.mapper;

import java.util.HashMap;

public class FhirMapper {

  HashMap<String, String> icd10Snomed;
  HashMap<String, String> sampleTypeSnomed;

  public FhirMapper(HashMap<String,String> icd10Snomed, HashMap<String,String> sampleTypeSnomed){
    this.icd10Snomed = icd10Snomed;
    this.sampleTypeSnomed = sampleTypeSnomed;
  }

}
