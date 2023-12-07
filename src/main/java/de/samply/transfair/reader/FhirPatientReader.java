package de.samply.transfair.reader;

import ca.uhn.fhir.rest.client.api.IGenericClient;

public class FhirPatientReader extends FhirResourceReader{
    public FhirPatientReader(IGenericClient client) {
        super(client);
        resourceName = "Patient";
        includeString = "Patient:subject";
        maxResourceCount = 10;
    }
}
