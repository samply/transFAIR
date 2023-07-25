package de.samply.transfair.writer;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertOneModel;
import de.samply.transfair.util.JsonSerializer;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Export objects to MongoDB.
 */
@Slf4j
public class BeaconMongoSaver {
  private static final String MONGO_DB = "beacon";
  private static final int BATCH_SIZE = 100;

  private BeaconMongoSaver() {
  }
  
  /**
   * Serialize the given data into JSON and upload to MongoDB.
   *
   * @param data The object to be serialized.
   * @param collectionName Name of the collection to put the data in.
   * @param mongoHost Base URL of MongoDB.
   * @param mongoPort Port of MongoDB.
   */
  public static void export(Object data,
                            String collectionName,
                            String mongoHost,
                            String mongoPort,
                            String mongoUser,
                            String mongoPass) {
    String mongoUri = createMongoUri(mongoHost, mongoPort, mongoUser, mongoPass);
    try (MongoClient client = new MongoClient(new MongoClientURI(mongoUri))) {
      MongoDatabase database = client.getDatabase(MONGO_DB);
      MongoCollection<Document> collection = database.getCollection(collectionName);
      collection.drop(); // Clean out any existing collection
      List<String> dataList = JsonSerializer.toJsonList(data);
      List<InsertOneModel<Document>> models = new ArrayList<>();
      int recordCount = 0;
      for (String jsonString : dataList) {
        Document jsonDocument = Document.parse(jsonString);
        InsertOneModel<Document> model = new InsertOneModel<>(jsonDocument);
        models.add(model);
        if (models.size() == BATCH_SIZE) {
          collection.bulkWrite(models, new BulkWriteOptions().ordered(false));
          recordCount += models.size();
          models.clear();
        }
      }
      if (models.isEmpty()) {
        collection.bulkWrite(models, new BulkWriteOptions().ordered(false));
        recordCount += models.size();
      }
      log.info("dataList size " + dataList.size());
      log.info("Number of records inserted " + recordCount);
      collection.createIndex(Indexes.ascending("$**")); // Wildcard index.
    }
  }

  /**
   * Create a connection URI for the Mongo database.
   *
   * @param data The object to be serialized.
   * @param collectionName Name of the collection to put the data in.
   * @param mongoHost Base URL of MongoDB.
   * @param mongoPort Port of MongoDB.
   * @return Mongo URI.
   */
  private static String createMongoUri(String mongoHost,
                                       String mongoPort,
                                       String mongoUser,
                                       String mongoPass) {
    log.info("export: mongoHost=" + mongoHost + ", mongoPort=" + mongoPort);
    if (Objects.isNull(mongoUser) || mongoUser.isEmpty()) {
      log.warn("Mongo user has not been defined");
    }
    if (Objects.isNull(mongoPass) || mongoPass.isEmpty()) {
      log.warn("Mongo password has not been defined");
    }

    return "mongodb://" + mongoUser + ":" + mongoPass + "@"
        + mongoHost + ":" + mongoPort;
  }
}
