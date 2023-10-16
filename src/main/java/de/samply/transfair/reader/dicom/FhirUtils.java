package de.samply.transfair.reader.dicom;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Resource;

/**
 * A utility class for working with FHIR resources.
 */
public class FhirUtils {
    /**
     * Serializes a FHIR resource to a JSON string.
     *
     * @param resource the FHIR resource to serialize
     * @return the JSON string representation of the resource
     */
    public static String serializeResourceToJson(Resource resource) {
        // Create a new FhirContext
        FhirContext fhirContext = FhirContext.forR4();

        // Create a new instance of the JSON parser
        IParser jsonParser = fhirContext.newJsonParser();
        jsonParser.setPrettyPrint(true);

        // Serialize the resource to a JSON string
        String jsonString = jsonParser.encodeResourceToString(resource);

        return jsonString;
    }

    /**
     * Creates a CodeableConcept object with a single Coding containing the specified code, system, and display.
     *
     * @param code    the code for the coding
     * @param system  the coding system
     * @param display the display value for the coding
     * @return the created CodeableConcept object
     */
    public static CodeableConcept createCodeableConcept(String code, String system, String display) {
        Coding coding = FhirUtils.createCoding(code, system, display);
        CodeableConcept codeableConcept = new CodeableConcept();
        codeableConcept.addCoding(coding);

        return codeableConcept;
    }

    /**
     * Creates a Coding object with the specified code, system and display. Set user-selected
     * properties to "false".
     *
     * @param code    the coding code
     * @param system  the coding system
     * @param display the coding display
     * @return the created Coding object
     */
    public static Coding createCoding(String code, String system, String display) {
        Coding coding = new Coding();
        coding.setCode(code);
        coding.setSystem(system);
        coding.setDisplay(display);
        coding.setUserSelected(false);

        return coding;
    }
}
