package de.samply.transfyr.mapper;

import java.util.HashMap;

public class FhirMapper {

  HashMap<String, String> icd10Snomed;
  HashMap<String, String> sampleType2Snomed;

  public FhirMapper(HashMap<String,String> icd10Snomed, HashMap<String,String> sampleType2Snomed){
    this.icd10Snomed = icd10Snomed;
    this.sampleType2Snomed = sampleType2Snomed;
  }

}
