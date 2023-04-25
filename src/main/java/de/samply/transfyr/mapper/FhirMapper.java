package de.samply.transfyr.mapper;

import java.util.HashMap;

public class FhirMapper {

  HashMap<String, String> icd10Snomed;

  public FhirMapper(HashMap<String,String> icd10Snomed){
    this.icd10Snomed = icd10Snomed;
  }

}
