package com.cloud.migration,handler

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
