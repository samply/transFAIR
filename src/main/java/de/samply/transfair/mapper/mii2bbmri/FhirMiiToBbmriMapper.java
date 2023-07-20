package de.samply.transfair.mapper.mii2bbmri;

import java.util.HashMap;

public class FhirMiiToBbmriMapper {

  HashMap<String, String> icd10Snomed;
  HashMap<String, String> snomedToSampleType;

  public FhirMiiToBbmriMapper(HashMap<String,String> icd10Snomed, HashMap<String,String> snomedToSampleType){
    this.icd10Snomed = icd10Snomed;
    this.snomedToSampleType = snomedToSampleType;
  }

}
