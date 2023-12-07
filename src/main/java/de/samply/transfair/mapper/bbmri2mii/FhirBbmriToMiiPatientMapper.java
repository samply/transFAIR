package de.samply.transfair.mapper.bbmri2mii;

import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

public class FhirBbmriToMiiPatientMapper extends FhirBbmriToMiiMapper {


  private static final String MII_PROFILE_PATIENT = "https://simplifier.net/medizininformatikinitiative-modulperson/mii_pr_person_patient";

  public FhirBbmriToMiiPatientMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> sampleTypeSnomed) {
    super(icd10Snomed, sampleTypeSnomed);
  }

  public List<Resource> map(Resource resource){


    Patient in = (Patient) resource;
    Patient out = in.copy();
    

    out.setMeta(new Meta().addProfile(MII_PROFILE_PATIENT));


    return List.of(out);
  }
}
