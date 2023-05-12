package de.samply.transfyr.mapper.mii2bbmri;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirMiiToBbmriObservationMapper extends FhirMiiToBbmriMapper {

  public FhirMiiToBbmriObservationMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> snomedSampleType) {
    super(icd10Snomed, snomedSampleType);
  }


  public List<Resource> map(Resource resource){
    
    
    //Right now no observations are mapped from MII to BBMRI
    return Collections.emptyList();
  }
}
