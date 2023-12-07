package de.samply.transfair.reader.dicom;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.json.JSONReader;

import javax.json.Json;
import javax.json.stream.JsonParser;
import java.io.File;
import java.io.StringReader;
import lombok.extern.slf4j.Slf4j;

/**
 * A utility class for working with DICOM objects.
 */
@Slf4j
public class DicomUtils {

    /**
     * Checks if a file is a DICOM file based on its extension.
     *
     * @param file the file to check
     * @return true if the file is a DICOM file, false otherwise
     */
    public static boolean isDicomFile(File file) {
        // Check if the file has a .dcm extension or any other DICOM file validation logic
        return file.isFile() && file.getName().toLowerCase().endsWith(".dcm");
    }

    /**
     * Extracts DICOM attributes from a DICOM file.
     *
     * @param file the DICOM file to extract attributes from
     * @return the extracted DICOM attributes as an Attributes object, or null if extraction fails
     */
    public static Attributes extractAttributesFromDicomFile(File file) {
        if (!isDicomFile(file)) {
            System.out.println("Not a DICOM file: " + file.getName());
            return null;
        }

        try (DicomInputStream dicomInputStream = new DicomInputStream(file)) {
            Attributes attributes = dicomInputStream.readDataset(-1, -1);
            return attributes;
        } catch (Exception e) {
            log.warn("Exception while processing file: " + file.getName());
            log.warn(e.toString());
        }

        return null;
    }

    /**
     * Extracts a summary of a DICOM file including the study instance ID, series instance ID, and image ID.
     *
     * @param file the DICOM file to extract the summary from
     * @return the DICOM file summary as a string, or null if extraction fails
     */
    public static String extractDicomFileSummary(File file) {
        Attributes attributes = extractAttributesFromDicomFile(file);
        if (attributes == null)
            return null;
        String studyInstanceId = attributes.getString(Tag.StudyInstanceUID);
        String studySeriesId = attributes.getString(Tag.SeriesInstanceUID);
        String studyImageId = attributes.getString(Tag.InstanceNumber);
        return "Instance: " + studyInstanceId + ", Series: " + studySeriesId + ", Image: " + studyImageId;
    }

    /**
     * Converts DICOMweb JSON to a {@link Attributes} object.
     *
     * @param json the DICOMweb JSON string to be converted
     * @return the {@link Attributes} object populated with DICOM attributes from the JSON
     */
    public static Attributes convertJsonToAttributes(String json) {
        JsonParser jsonParser = Json.createParser(new StringReader(json));
        JSONReader reader = new JSONReader(jsonParser);
        Attributes attrs = new Attributes();

        // Parse the DICOMweb JSON and populate the Attributes object
        reader.readDataset(attrs);

        return attrs;
    }
}
