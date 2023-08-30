package de.samply.transfair.mapper.bbmri2beacon;

import de.samply.transfair.mapper.FhirResourceMapper;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.context.annotation.Profile;

import java.util.List;

// FHIR to Beacon conversion does not need a mapper, but without it, invisible Spring magic stops working.

@Profile("copy")
@Slf4j
public class FhirBbmriToBeaconResourceMapper implements FhirResourceMapper {

  public List<Resource> map(List<Resource> resources){

    return resources;
  }
}
