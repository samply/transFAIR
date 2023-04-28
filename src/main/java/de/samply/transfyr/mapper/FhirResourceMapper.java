package de.samply.transfyr.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirResourceMapper {

  private final FhirSpecimenMapper specimenMapper;
  private final FhirPatientMapper patientMapper;
  private final FhirConditionMapper conditionMapper;
  private final FhirObservationMapper observationMapper;
  private final FhirOrganizationMapper organizationMapper;

  public FhirResourceMapper(FhirPatientMapper patientMapper, FhirConditionMapper conditionMapper, FhirSpecimenMapper specimenMapper, 
      FhirObservationMapper observationMapper, FhirOrganizationMapper organizationMapper){
    this.patientMapper = patientMapper;
    this.conditionMapper = conditionMapper;
    this.specimenMapper = specimenMapper;
    this.observationMapper = observationMapper;
    this.organizationMapper = organizationMapper;
  }

  public List<Resource> map(List<Resource> resources){

    return resources.stream().flatMap( 
        resource -> {
          ResourceType resType = resource.getResourceType();
          List<Resource> results = new ArrayList<>();
          switch (resType) {
            case Patient: 
              results.addAll(patientMapper.map(resource));
              break;
            case Condition: 
              results = conditionMapper.map(resource);
              break;
            case Specimen: 
              results = specimenMapper.map(resource);
              break;
            case Observation: 
              results = observationMapper.map(resource);
              break;
            case Organization: 
              results = organizationMapper.map(resource);
              break;
            default:
              log.warn("Unmapable type: {}", resType.name());
              break;
          }
          return  results.stream();
        }
        ).filter(Objects::nonNull).toList();
  }
}
