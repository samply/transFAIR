package de.samply.transfair.reader;

import org.dcm4che3.data.Attributes;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.transfair.reader.dicom.CollectDicomAttributesFromFiles;
import de.samply.transfair.reader.dicom.CollectDicomAttributesFromWebClient;
import de.samply.transfair.reader.dicom.DicomAttributesToFhir;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * Read data into ImagingStudy objects.
 *
 * Data can come either from a DICOM source, such as DICOM web or files,
 * or it can come from a FHIR store.
 *
 */
@Slf4j
public class FhirImagingStudyReader implements ItemReader<Bundle> {

  private final IGenericClient client;
  private Bundle bundle;
  @Value("${imgmeta.fromFhir}")
  private Boolean fromFhir;
  @Value("${imgmeta.dicomWebUrl}")
  private String dicomWebUrl;
  @Value("${imgmeta.dicomFilePath}")
  private String dicomFilePath;

  public FhirImagingStudyReader(IGenericClient client){
    this.client = client;
  }

  /**
   * Reads data from a DICOM source. Depending on the settings of the relevant
   * parameters in application.yml, different sources will be used.
   *
   * If imgmeta.dicomWebUrl has a value, then this method will assume that it is
   * the URL of a valid DICOM Web service and will attempt to copy over all of
   * the metadata available.
   *
   * If imgmeta.dicomFilePath has a value, then it will be treated as the path
   * to a file or directory containíng .dcm files. The metadata from all of these
   * files will be extracted.
   *
   * If both have a value, then data will be pulled from both sources.
   *
   * If imgmeta.fromFhir is true or both imgmeta.dicomWebUrl and imgmeta.dicomFilePath
   * are empty, then this method will attempt to read image metadata from
   * ImagingStudy resources in the source FHIR store.
   * @return
   */
  @Override
  public Bundle read() {
    if (!dicomWebUrl.isEmpty() || !dicomFilePath.isEmpty())
      fromFhir = false;

    if (fromFhir)
      readFromFhir();
    else
      readFromDicom();

    return bundle;
  }

  private void readFromFhir() {
    if (bundle == null){
      bundle = client.search()
              .forResource("ImagingStudy")
              .include(new Include("ImagingStudy:subject"))
              .count(10)
              .returnBundle(Bundle.class)
              .execute();
    } else if (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
      bundle = client
              .loadPage()
              .next(bundle)
              .execute();
    } else
      // Terminate reading
      bundle = null;
  }

  private void readFromDicom() {
    if (bundle == null)
      bundle = new Bundle();
    else {
      // Terminate reading
      bundle = null;
      return;
    }
    DicomAttributesToFhir dicomAttributesToFhir = new DicomAttributesToFhir();
    if (!dicomFilePath.isEmpty()) {
      CollectDicomAttributesFromFiles collectDicomAttributes = new CollectDicomAttributesFromFiles(dicomFilePath);
      List<Attributes> attributesList = collectDicomAttributes.collectAttributes();
      dicomAttributesToFhir.dicomAttributesListToBundle(attributesList, bundle);
    }
    if (!dicomWebUrl.isEmpty()) {
      CollectDicomAttributesFromWebClient collectDicomAttributes = new CollectDicomAttributesFromWebClient(dicomWebUrl);
      List<Attributes> attributesList = collectDicomAttributes.collectAttributes();
      dicomAttributesToFhir.dicomAttributesListToBundle(attributesList, bundle);
    }
  }
}
