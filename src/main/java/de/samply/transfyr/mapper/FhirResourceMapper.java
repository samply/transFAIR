package de.samply.transfyr.mapper;

import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirResourceMapper {

  private final FhirSpecimenMapper specimenMapper;
  private FhirPatientMapper patientMapper;
  private FhirConditionMapper conditionMapper;
  private FhirObservationMapper observationMapper;
  private FhirOrganizationMapper organizationMapper;

  public FhirResourceMapper(FhirPatientMapper patientMapper, FhirConditionMapper conditionMapper, FhirSpecimenMapper specimenMapper, 
      FhirObservationMapper observationMapper, FhirOrganizationMapper organizationMapper){
    this.patientMapper = patientMapper;
    this.conditionMapper = conditionMapper;
    this.specimenMapper = specimenMapper;
    this.observationMapper = observationMapper;
    this.organizationMapper = organizationMapper;
  }

  public List<Resource> map(List<Resource> resources){

    return resources.stream().map( 
        resource -> {
          ResourceType resType = resource.getResourceType();
          Resource res = null;
          switch (resType) {
            case Patient: res = patientMapper.map(resource);
            break;
            case Condition: res = conditionMapper.map(resource);
            break;
            case Specimen: res = specimenMapper.map(resource);
            break;
            case Observation: res = observationMapper.map(resource);
            break;
            case Organization: res= organizationMapper.map(resource);
            break;
            default:
              break;
          }

          return  res;
        }
        ).filter(Objects::nonNull).toList();

  }

}
