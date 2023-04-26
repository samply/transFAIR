package de.samply.transfyr.mapper;

import java.util.HashMap;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

public class FhirPatientMapper extends FhirMapper {


  public FhirPatientMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> sampleTypeSnomed) {
    super(icd10Snomed, sampleTypeSnomed);
  }

  public Resource map(Resource resource){

    Patient out = new Patient();
    Patient in = (Patient) resource;

    out.setGender(in.getGender());
    out.setBirthDate(in.getBirthDate());
    out.setId(in.getId());

    return out;
  }
}
