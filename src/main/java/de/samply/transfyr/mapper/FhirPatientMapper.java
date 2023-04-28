package de.samply.transfyr.mapper;

import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

public class FhirPatientMapper extends FhirMapper {


  public FhirPatientMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> sampleTypeSnomed) {
    super(icd10Snomed, sampleTypeSnomed);
  }

  public List<Resource> map(Resource resource){


    Patient in = (Patient) resource;
    Patient out = in.copy();

    out.setGender(in.getGender());
    out.setBirthDate(in.getBirthDate());
    out.setId(in.getId());

    return List.of(out);
  }
}
