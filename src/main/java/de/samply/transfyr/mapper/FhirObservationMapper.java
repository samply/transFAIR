package de.samply.transfyr.mapper;

import java.util.HashMap;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirObservationMapper extends FhirMapper {


  public FhirObservationMapper(
      HashMap<String, String> icd10Snomed) {
    super(icd10Snomed);
  }


  public Resource map(Resource resource){

    Observation outObservation = new Observation();
    Observation inObservation = (Observation) resource;

    outObservation = inObservation.copy();


    //TODO - Return null if resource could not be mapped

    return outObservation;
  }
}
