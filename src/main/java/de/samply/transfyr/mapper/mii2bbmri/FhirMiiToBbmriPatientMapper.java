package de.samply.transfyr.mapper.mii2bbmri;

import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

public class FhirMiiToBbmriPatientMapper extends FhirMiiToBbmriMapper {

  private static final String MII_PROFILE_PATIENT = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient";

  public FhirMiiToBbmriPatientMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> snomedSampleType) {
    super(icd10Snomed, snomedSampleType);
  }

  public List<Resource> map(Resource resource){


    Patient in = (Patient) resource;
    Patient out = in.copy();


    out.setMeta(new Meta().addProfile(MII_PROFILE_PATIENT));


    return List.of(out);
  }
}
