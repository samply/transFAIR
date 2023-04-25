package de.samply.transfyr.mapper;

import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Resource;

@Slf4j
public class FhirConditionMapper extends FhirMapper {


  public FhirConditionMapper(
      HashMap<String, String> icd10Snomed) {
    super(icd10Snomed);
  }

  private Condition convertCodingBySystem(Condition in, Condition out, String inSystem, String outSystem){


    Coding inCoding = in.getCode().getCoding()
        .stream()
        .filter(coding -> coding.getSystem()
            .equals(inSystem))
        .findFirst().get();


    if (inCoding != null){
      String newCode = this.icd10Snomed.get(inCoding.getCode());

      if (newCode != null) {
        Coding outCoding = new Coding().setCode(newCode).setSystem(outSystem);
        out.getCode().addCoding(outCoding);

      }
    }

    return out;
  }

  public Resource map(Resource resource){

    Condition outCond = new Condition();
    Condition inCond = (Condition) resource;

    this.convertCodingBySystem(inCond, outCond,
        "http://hl7.org/fhir/sid/icd-10",
        "http://snomed.org");

    outCond.setSubject(inCond.getSubject());
    outCond.setId(inCond.getId());


    //TODO - Return null if resource could not be mapped

    return outCond;
  }
}
