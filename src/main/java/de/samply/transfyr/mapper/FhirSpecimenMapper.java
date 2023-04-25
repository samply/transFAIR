package de.samply.transfyr.mapper;

import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;

@Slf4j
public class FhirSpecimenMapper extends FhirMapper {


  public FhirSpecimenMapper(
      HashMap<String, String> icd10Snomed) {
    super(icd10Snomed);
  }

  public Resource map(Resource resource){

    Specimen in = (Specimen) resource;
    Specimen out = new Specimen();
    
    out = in.copy();

    return out;
  }
}
