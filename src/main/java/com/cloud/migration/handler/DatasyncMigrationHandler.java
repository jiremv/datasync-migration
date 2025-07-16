package com.cloud.migration.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DatasyncMigrationHandler implements RequestHandler<S3Event, String> {
    private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final String gcsBucket = System.getenv("gcsBucket");
    private final String gcpProjectId = System.getenv("projectId");
    private final String gcpCredentialsPath = "/tmp/gcp-service-account.json";
    @Override
    public String handleRequest(S3Event event, Context context) {
        try {
            String s3Bucket = event.getRecords().get(0).getS3().getBucket().getName();
            String s3Key = event.getRecords().get(0).getS3().getObject().getKey();

            context.getLogger().log("Nuevo archivo en S3: " + s3Bucket + "/" + s3Key);

            // Descargar archivo de S3
            Path tempFile = downloadFromS3(s3Bucket, s3Key, context);

            // Subir archivo a GCS
            uploadToGCS(tempFile, s3Key, context);

            return "Archivo transferido exitosamente: " + s3Key;

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
            return "Error transfiriendo archivo a GCS";
        }
    }

    private Path downloadFromS3(String bucket, String key, Context context) throws Exception {
        S3Object s3Object = s3Client.getObject(bucket, key);
        InputStream inputStream = s3Object.getObjectContent();

        Path tempFile = Paths.get("/tmp/" + key.replace("/", "_"));
        OutputStream outputStream = new FileOutputStream(tempFile.toFile());

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, bytesRead);
        }

        inputStream.close();
        outputStream.close();

        context.getLogger().log("Archivo descargado desde S3 a " + tempFile.toString());
        return tempFile;
    }

    private void uploadToGCS(Path filePath, String destinationName, Context context) throws Exception {
        Storage storage = StorageOptions.newBuilder()
                .setProjectId(gcpProjectId)
                .setCredentials(ServiceAccountCredentials.fromStream(new FileInputStream(gcpCredentialsPath)))
                .build()
                .getService();

        BlobId blobId = BlobId.of(gcsBucket, destinationName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

        storage.create(blobInfo, Files.readAllBytes(filePath));

        context.getLogger().log("Archivo subido a GCS: gs://" + gcsBucket + "/" + destinationName);
    }
}
