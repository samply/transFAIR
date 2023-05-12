package de.samply.transfyr.mapper;

import java.util.List;
import org.hl7.fhir.r4.model.Resource;

public interface FhirResourceMapper {
  
  public List<Resource> map(List<Resource> resources);

}
