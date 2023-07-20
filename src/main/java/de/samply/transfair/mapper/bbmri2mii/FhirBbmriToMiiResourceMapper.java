package de.samply.transfair.mapper.bbmri2mii;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import de.samply.transfair.mapper.FhirResourceMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirBbmriToMiiResourceMapper implements FhirResourceMapper {

  private final FhirBbmriToMiiSpecimenMapper specimenMapper;
  private final FhirBbmriToMiiPatientMapper patientMapper;
  private final FhirBbmriToMiiConditionMapper conditionMapper;
  private final FhirBbmriToMiiObservationMapper observationMapper;
  private final FhirBbmriToMiiOrganizationMapper organizationMapper;

  public FhirBbmriToMiiResourceMapper(FhirBbmriToMiiPatientMapper patientMapper, FhirBbmriToMiiConditionMapper conditionMapper, FhirBbmriToMiiSpecimenMapper specimenMapper, 
      FhirBbmriToMiiObservationMapper observationMapper, FhirBbmriToMiiOrganizationMapper organizationMapper){
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
