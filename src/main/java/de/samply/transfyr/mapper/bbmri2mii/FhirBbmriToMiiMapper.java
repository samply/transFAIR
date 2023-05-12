package de.samply.transfyr.mapper.bbmri2mii;

import java.util.HashMap;

public class FhirBbmriToMiiMapper {

  HashMap<String, String> icd10Snomed;
  HashMap<String, String> sampleType2Snomed;

  public FhirBbmriToMiiMapper(HashMap<String,String> icd10Snomed, HashMap<String,String> sampleType2Snomed){
    this.icd10Snomed = icd10Snomed;
    this.sampleType2Snomed = sampleType2Snomed;
  }

}
