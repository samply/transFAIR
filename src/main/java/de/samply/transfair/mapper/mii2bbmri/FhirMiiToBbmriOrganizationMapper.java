package de.samply.transfair.mapper.mii2bbmri;

import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirMiiToBbmriOrganizationMapper extends FhirMiiToBbmriMapper {

  private static final String MII_BIOBANK_DESCRIPTION =
      "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/BeschreibungSammlung";
  private static final String BBMRI_BIOBANK_DESCRIPTION =
      "https://fhir.bbmri.de/StructureDefinition/OrganizationDescription";
  private static final String BBMRI_CANONICAL_TYPE = "https://fhir.bbmri.de/StructureDefinition/Biobank";


  public FhirMiiToBbmriOrganizationMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> snomedSampleType) {
    super(icd10Snomed, snomedSampleType);
  }


  public List<Resource> map(Resource resource){

    Organization in = (Organization) resource;
    Organization out = in.copy();

    out.setMeta(new Meta().setProfile(List.of(new CanonicalType(BBMRI_CANONICAL_TYPE))));

    for (Extension extension : out.getExtension()) {
      if (extension.getUrl().equals(MII_BIOBANK_DESCRIPTION)) {
        extension.setUrl(BBMRI_BIOBANK_DESCRIPTION);
      }
    }

    return List.of(resource);
  }
}
