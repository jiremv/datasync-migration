package com.cloud.migration.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;

import com.amazonaws.services.secretsmanager.*;
import com.amazonaws.services.secretsmanager.model.*;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class DatasyncMigrationHandler implements RequestHandler<S3Event, String> {

    private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final String gcsBucket = System.getenv("gcsBucket");
    private final String gcpProjectId = System.getenv("projectId");
    private final String secretName = System.getenv("secretName");

    @Override
    public String handleRequest(S3Event event, Context context) {
        try {
            String s3Bucket = event.getRecords().get(0).getS3().getBucket().getName();
            String s3Key = event.getRecords().get(0).getS3().getObject().getKey();

            context.getLogger().log("Archivo detectado en S3: " + s3Bucket + "/" + s3Key);

            // Recuperar las credenciales desde Secrets Manager
            String gcpCredentialsJson = getGcpCredentialsFromSecretsManager(context);

            // Descargar archivo de S3
            Path tempFile = downloadFromS3(s3Bucket, s3Key, context);

            // Subir el archivo a GCS
            uploadToGCS(tempFile, s3Key, gcpCredentialsJson, context);

            return "Transferencia completada para: " + s3Key;

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String getGcpCredentialsFromSecretsManager(Context context) {
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.defaultClient();
        GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(secretName);

        GetSecretValueResult result = client.getSecretValue(request);

        context.getLogger().log("Credenciales GCP recuperadas desde Secrets Manager.");

        return result.getSecretString();
    }

    private Path downloadFromS3(String bucket, String key, Context context) throws IOException {
        S3Object s3Object = s3Client.getObject(bucket, key);

        Path tempFile = Files.createTempFile("s3file-", "-" + key.replace("/", "_"));
        try (InputStream in = s3Object.getObjectContent();
             OutputStream out = Files.newOutputStream(tempFile)) {
            in.transferTo(out);
        }

        context.getLogger().log("Archivo descargado de S3 a: " + tempFile.toString());
        return tempFile;
    }

    private void uploadToGCS(Path filePath, String objectName, String gcpCredentialsJson, Context context) throws IOException {
        InputStream credentialsStream = new ByteArrayInputStream(gcpCredentialsJson.getBytes(StandardCharsets.UTF_8));

        Storage storage = StorageOptions.newBuilder()
                .setProjectId(gcpProjectId)
                .setCredentials(ServiceAccountCredentials.fromStream(credentialsStream))
                .build()
                .getService();

        BlobId blobId = BlobId.of(gcsBucket, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

        storage.create(blobInfo, Files.readAllBytes(filePath));

        context.getLogger().log("Archivo subido a GCS: gs://" + gcsBucket + "/" + objectName);
    }
}