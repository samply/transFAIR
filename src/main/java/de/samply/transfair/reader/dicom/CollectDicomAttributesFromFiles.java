package de.samply.transfair.reader.dicom;

import org.dcm4che3.data.Attributes;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A utility class for extracting DICOM files from a directory hierarchy and
 * adding the extracted Attribute objects to a list.
 */
@Slf4j
public class CollectDicomAttributesFromFiles {
    private String path;

    /**
     * Constructs a CollectDicomAttributesFromFiles object with the specified path.
     *
     * @param path the path to the root directory containing DICOM files
     */
    public CollectDicomAttributesFromFiles(String path) {
        this.path = path;
    }

    /**
     * Recursively collects DICOM attributes from files in the specified path and its subdirectories.
     *
     * @return a list of Attributes objects representing the collected DICOM attributes
     */
    public List<Attributes> collectAttributes() {
        Path root = Path.of(path);
        List<Attributes> attributesList = new ArrayList<>();
        descendFileHierarchyAndCollectAttributes(attributesList, root);

        return attributesList;
    }

    /**
     * Recursive helper method that descends the file hierarchy and adds DICOM instances to the provided FHIR bundle.
     *
     * @param attributesList
     * @param path           the current file or directory being processed in the file hierarchy
     */
    private void descendFileHierarchyAndCollectAttributes(List<Attributes> attributesList, Path path) {
        if (!Files.exists(path)) {
            log.warn("Invalid file path or directory does not exist: " + path);
            return;
        }

        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.list(path)) {
                stream.forEach(file -> descendFileHierarchyAndCollectAttributes(attributesList, file));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (DicomUtils.isDicomFile(path.toFile())) {
            Attributes attributes = DicomUtils.extractAttributesFromDicomFile(path.toFile());
            attributesList.add(attributes);
        }
        else
            log.info("CollectDicomAttributesFromFiles.descendFileHierarchyAndCollectAttributes: " + path + " is a not DICOM file, ignoring");
    }
}
