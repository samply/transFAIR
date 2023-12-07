package de.samply.transfair.reader.dicom;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.TagUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ImagingStudy;
import org.hl7.fhir.r4.model.ImagingStudy.ImagingStudySeriesComponent;
import org.hl7.fhir.r4.model.ImagingStudy.ImagingStudySeriesInstanceComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * A utility class for converting DICOM Attributes to FHIR ImagingStudy resources.
 */
@Slf4j
public class DicomAttributesToFhir {
    private final ImagingStudyBundler imagingStudyBundler = new ImagingStudyBundler();

    /**
     * Converts a list of DICOM Attributes objects to an ImagingStudy resources and adds them
     * to a Bundle.
     *
     * @param attributesList the list of DICOM Attributes objects to be converted
     * @param bundle the Bundle to which the converted ImagingStudy resources will be added
     */
    public void dicomAttributesListToBundle(List<Attributes> attributesList, Bundle bundle) {
        for (Attributes attributes: attributesList){
            ImagingStudy imagingStudy = dicomAttributesToImagingStudy(attributes);
            if (imagingStudy != null)
                imagingStudyBundler.addImagingStudyToBundle(imagingStudy, bundle);
        }
    }

    /**
     * Converts DICOM attributes to a FHIR ImagingStudy resource.
     *
     * @param attributes the DICOM attributes to be converted
     * @return the converted ImagingStudy resource, or null if attributes is null
     */
    private ImagingStudy dicomAttributesToImagingStudy(Attributes attributes) {
        if (attributes == null) {
            log.warn("convertDicomToImagingStudy: attributes == null");
            return null;
        }

        ImagingStudy imagingStudy = createImagingStudy(attributes);
        ImagingStudySeriesComponent series = createSeries(attributes);
        ImagingStudySeriesInstanceComponent instance = createInstance(attributes);
        series.addInstance(instance);
        imagingStudy.addSeries(series);

        return imagingStudy;
    }

    /**
     * Creates and configures an ImagingStudy resource.
     *
     * @param attributes the DICOM attributes
     * @return the created ImagingStudy resource
     */
    private ImagingStudy createImagingStudy(Attributes attributes) {
        ImagingStudy imagingStudy = new ImagingStudy();

        imagingStudy.setId(attributes.getString(Tag.StudyInstanceUID));
        imagingStudy.setStarted(attributes.getDate(Tag.StudyDate));
        imagingStudy.setDescription(attributes.getString(Tag.StudyDescription));

        Meta meta = new Meta();
        meta.addProfile("http://dicom.nema.org/resources/ontology/DCM");
        imagingStudy.setMeta(meta);

        Reference patientReference = new Reference("Patient/" + attributes.getString(Tag.PatientID));
        imagingStudy.setSubject(patientReference);

        CodeableConcept procedureCodeableConcept = FhirUtils.createCodeableConcept(
                attributes.getString(Tag.PerformedProcedureStepDescription),
                "http://nema.org/dicom/dss",
                TagUtils.toHexString(Tag.PerformedProcedureStepDescription)
        );
        imagingStudy.addProcedureCode(procedureCodeableConcept);

        return imagingStudy;
    }

    /**
     * Creates and configures an ImagingStudySeriesComponent.
     *
     * @param attributes the DICOM attributes
     * @return the created ImagingStudySeriesComponent
     */
    private ImagingStudySeriesComponent createSeries(Attributes attributes) {
        ImagingStudySeriesComponent series = new ImagingStudySeriesComponent();
        series.setUid(attributes.getString(Tag.SeriesInstanceUID));
        series.setNumber(attributes.getInt(Tag.SeriesNumber, 0));
        series.setDescription(attributes.getString(Tag.SeriesDescription));

        Coding modalityCoding = FhirUtils.createCoding(
                attributes.getString(Tag.Modality),
                "http://terminology.hl7.org/CodeSystem/dicomMDLTY",
                TagUtils.toHexString(Tag.Modality)
        );
        series.setModality(modalityCoding);

        Coding bodySiteCoding = FhirUtils.createCoding(
                attributes.getString(Tag.BodyPartExamined),
                "http://snomed.info/sct",
                TagUtils.toHexString(Tag.BodyPartExamined)
        );
        series.setBodySite(bodySiteCoding);

        Coding laterality = FhirUtils.createCoding(
                attributes.getString(Tag.Laterality),
                "http://loinc.org/property/rad-anatomic-location-laterality",
                TagUtils.toHexString(Tag.Laterality)
        );
        series.setLaterality(laterality);

        return series;
    }

    /**
     * Creates and configures an ImagingStudySeriesInstanceComponent.
     *
     * @param attributes the DICOM attributes
     * @return the created ImagingStudySeriesInstanceComponent
     */
    private ImagingStudySeriesInstanceComponent createInstance(Attributes attributes) {
        ImagingStudySeriesInstanceComponent instance = new ImagingStudySeriesInstanceComponent();
        instance.setUid(attributes.getString(Tag.SOPInstanceUID));
        instance.setNumber(attributes.getInt(Tag.InstanceNumber, 0));
        instance.setTitle(attributes.getString(Tag.DocumentTitle));

        Coding sopClass = FhirUtils.createCoding(
                attributes.getString(Tag.SOPClassUID),
                "http://dicom.nema.org/resources/ontology/DCM",
                TagUtils.toHexString(Tag.SOPClassUID)
        );
        instance.setSopClass(sopClass);

        return instance;
    }
}
