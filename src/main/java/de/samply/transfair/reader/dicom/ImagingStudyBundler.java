package de.samply.transfair.reader.dicom;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.ImagingStudy;

import java.util.List;

/**
 * This class provides functionality to add ImagingStudy resources to FHIR bundles.
 */
public class ImagingStudyBundler {

    /**
     * Combines the given ImagingStudy resource with compatible ImagingStudy resources found in the bundle.
     * If no compatible ImagingStudy resources are found, add the whole ImagingStudy to the bundle.
     *
     * @param imagingStudy The ImagingStudy resource to combine with other compatible ImagingStudy resources.
     * @param bundle       The bundle containing ImagingStudy resources to be combined.
     */
    public void addImagingStudyToBundle(ImagingStudy imagingStudy, Bundle bundle) {
        boolean isInBundle = false;
        for (BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof ImagingStudy) {
                ImagingStudy resource = (ImagingStudy) entry.getResource();
                if (areImagingStudiesCompatible(imagingStudy, resource)) {
                    addImagingStudyToBundle(imagingStudy, resource);
                    isInBundle = true;
                    break;
                }
            }
        }
        if (!isInBundle) {
            // Create a new BundleEntryComponent
            BundleEntryComponent entry = new BundleEntryComponent();
            // Set the ImagingStudy resource as the resource of the entry
            entry.setResource(imagingStudy);

            // Add the entry to the entry list of the Bundle
            bundle.addEntry(entry);
        }
    }

    /**
     * Checks if two ImagingStudy resources are compatible by comparing their study IDs.
     *
     * @param imagingStudy1 The first ImagingStudy resource.
     * @param imagingStudy2 The second ImagingStudy resource.
     * @return True if the ImagingStudy resources have the same study ID, false otherwise.
     */
    private boolean areImagingStudiesCompatible(ImagingStudy imagingStudy1, ImagingStudy imagingStudy2) {
        String studyId1 = imagingStudy1.getId();
        String studyId2 = imagingStudy2.getId();
        return studyId1.equals(studyId2);
    }

    /**
     * Combines compatible ImagingStudy resources by copying non-common series and combining common series.
     *
     * @param imagingStudy1 The first ImagingStudy resource.
     * @param imagingStudy2 The second ImagingStudy resource.
     */
    private void addImagingStudyToBundle(ImagingStudy imagingStudy1, ImagingStudy imagingStudy2) {
        List<ImagingStudy.ImagingStudySeriesComponent> series1 = imagingStudy1.getSeries();
        List<ImagingStudy.ImagingStudySeriesComponent> series2 = imagingStudy2.getSeries();

        // Copy non-common series from imagingStudy1 to imagingStudy2
        for (ImagingStudy.ImagingStudySeriesComponent series : series1) {
            if (!hasCommonSeries(series, series2)) {
                //imagingStudy2.addSeries().setCode(series.getCode()).setDescription(series.getDescription());
                imagingStudy2.addSeries(series);
            }
        }

        // Combine common series using a placeholder function
        for (ImagingStudy.ImagingStudySeriesComponent series : series1) {
            if (hasCommonSeries(series, series2)) {
                combineSeries(series, findMatchingSeries(series, series2));
            }
        }
    }

    /**
     * Checks if a series is present in a list of series by comparing their UIDs.
     *
     * @param series     The series to check.
     * @param seriesList The list of series to search within.
     * @return True if the series is present in the list, false otherwise.
     */
    private boolean hasCommonSeries(ImagingStudy.ImagingStudySeriesComponent series, List<ImagingStudy.ImagingStudySeriesComponent> seriesList) {
        for (ImagingStudy.ImagingStudySeriesComponent existingSeries : seriesList) {
            if (existingSeries.getUid().equals(series.getUid())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a matching series in a list of series by comparing their UIDs.
     *
     * @param series     The series to find a match for.
     * @param seriesList The list of series to search within.
     * @return The matching series if found, or null if not found.
     */
    private ImagingStudy.ImagingStudySeriesComponent findMatchingSeries(ImagingStudy.ImagingStudySeriesComponent series, List<ImagingStudy.ImagingStudySeriesComponent> seriesList) {
        for (ImagingStudy.ImagingStudySeriesComponent existingSeries : seriesList) {
            if (existingSeries.getUid().equals(series.getUid())) {
                return existingSeries;
            }
        }
        return null;
    }

    /**
     * Combines two series by copying images from series1 to series2 if they are not already present in series2.
     *
     * @param series1 The first series.
     * @param series2 The second series.
     */
    private void combineSeries(ImagingStudy.ImagingStudySeriesComponent series1, ImagingStudy.ImagingStudySeriesComponent series2) {
        List<ImagingStudy.ImagingStudySeriesInstanceComponent> instances1 = series1.getInstance();
        List<ImagingStudy.ImagingStudySeriesInstanceComponent> instances2 = series2.getInstance();

        // Copy images from series1 to series2 if they are not already present in series2
        for (ImagingStudy.ImagingStudySeriesInstanceComponent instance : instances1) {
            if (!hasMatchingImage(instance, instances2)) {
                series2.addInstance().setUid(instance.getUid()).setNumber(instance.getNumber());
            }
        }
    }

    /**
     * Checks if an image has a matching UID in a list of images.
     *
     * @param instance  The image to check.
     * @param instances The list of images to search within.
     * @return True if the image has a matching UID in the list, false otherwise.
     */
    private boolean hasMatchingImage(ImagingStudy.ImagingStudySeriesInstanceComponent instance, List<ImagingStudy.ImagingStudySeriesInstanceComponent> instances) {
        for (ImagingStudy.ImagingStudySeriesInstanceComponent existingInstance : instances) {
            if (existingInstance.getUid().equals(instance.getUid())) {
                return true;
            }
        }
        return false;
    }
}


