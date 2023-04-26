package de.samply.transfyr.mapper;

import java.util.HashMap;
import org.hl7.fhir.r4.model.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirOrganizationMapper extends FhirMapper {


  public FhirOrganizationMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> sampleTypeSnomed) {
    super(icd10Snomed, sampleTypeSnomed);
  }


  public Resource map(Resource resource){

    return resource;
  }
}
