package repository;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.UserProfile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


public class S3Repository {
  final AmazonS3 client = AmazonS3ClientBuilder.standard()
    .withRegion(Regions.EU_WEST_2)
    .build();

  final String bucketName = "social-app-user-profiles";
  final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static Logger LOG = Logger.getLogger(UserProfile.class.getName());

  public UserProfile getUserProfile(String userId)  {
    final String KEY = userId + ".json";
    try {
      final S3Object result = this.client.getObject(bucketName, KEY);
      S3ObjectInputStream inputStream = result.getObjectContent();
      return objectMapper.readValue(inputStream, UserProfile.class);
    } catch (Exception e) {
      e.printStackTrace();
    }

    return null;
  }

  public void saveUserProfile(UserProfile profile) {
    try {
      String profileJson  = objectMapper.writeValueAsString(profile);
      this.client.putObject(bucketName, profile.getUser().getId() + ".json", profileJson);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public List<UserProfile> getUserProfilesByUniversity(final String university)  {

    final String searchQuery = "SELECT * FROM S3object s WHERE s.university = '" + university + "'";

    // Fetch the list of files in this directory
    ObjectListing objectListing = this.client.listObjects(bucketName);

    List<UserProfile> profiles = new ArrayList<>();

    for (S3ObjectSummary summary : objectListing.getObjectSummaries()) {
      SelectObjectContentRequest request = new SelectObjectContentRequest();
      request.setBucketName(bucketName);
      request.setExpression(searchQuery);
      request.setKey(summary.getKey());
      request.setExpressionType(ExpressionType.SQL);

      InputSerialization inputSerialization = new InputSerialization();
      inputSerialization.setJson(new JSONInput().withType("Document")); // Uncomment this for JSON format input file

      OutputSerialization outputSerialization = new OutputSerialization();
      outputSerialization.setJson(new JSONOutput());

      request.setInputSerialization(inputSerialization);
      request.setOutputSerialization(outputSerialization);


      try {
        SelectObjectContentResult result = this.client.selectObjectContent(request);
        InputStream inputStream = result.getPayload().getRecordsInputStream();
        UserProfile profile;
        try {
          profile = objectMapper.readValue(inputStream, UserProfile.class);
          profiles.add(profile);
        } catch (Exception e) {
          LOG.info("S3 file " + summary.getKey() + " did not meet SQL expression");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    return profiles;
  }
}
