package de.samply.transfair.reader.amr;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Location;

import java.util.Map;

/**
 * This is a utility class for constructing FHIR Location resources and extensions
 * from data extracted from an AMR CSV file.
 */
public class LocationBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR Location resource using attributes extracted from the record relating
     * to the reporting country.
     *
     * @param record A map containing location data, where keys represent data attributes.
     * @return A constructed Location resource with populated properties.
     */
    public static Location buildReportingCountry(Map<String, String> record) {
        Location reportingCountry = new Location();

        // Extract country data from the map
        String id = record.get("ReportingCountry");

        // Set the country's ID
        IdType locationId = new IdType(id);
        reportingCountry.setId(locationId);

        return reportingCountry;
    }
}
