package de.samply.transfyr.mapper.copy;

import java.util.List;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.context.annotation.Profile;
import de.samply.transfyr.mapper.FhirResourceMapper;
import de.samply.transfyr.mapper.bbmri2mii.FhirBbmriToMiiConditionMapper;
import de.samply.transfyr.mapper.bbmri2mii.FhirBbmriToMiiObservationMapper;
import de.samply.transfyr.mapper.bbmri2mii.FhirBbmriToMiiOrganizationMapper;
import de.samply.transfyr.mapper.bbmri2mii.FhirBbmriToMiiPatientMapper;
import de.samply.transfyr.mapper.bbmri2mii.FhirBbmriToMiiSpecimenMapper;
import lombok.extern.slf4j.Slf4j;

@Profile("copy")
@Slf4j
public class FhirCopyResourceMapper implements FhirResourceMapper {

  public List<Resource> map(List<Resource> resources){

    return resources;
  }
}
