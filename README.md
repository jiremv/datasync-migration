# DataSync Migration Lambda - AWS S3 to Google Cloud Storage

## Descripción

Este proyecto implementa una **función AWS Lambda en Java 21** que transfiere archivos cargados en un bucket S3 directamente hacia un bucket de **Google Cloud Storage (GCS)**. El flujo es 100% serverless, sin intervención manual.

---

## Arquitectura

```plaintext
┌──────────────────────┐
│   Usuario / Sistema  │
│   sube archivo a S3  │
└──────────┬───────────┘
           │ Evento: s3:ObjectCreated
           ▼
┌──────────────────────┐
│   AWS S3 Bucket      │
│ (S3SourceBucket)     │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────────────┐
│  AWS Lambda (Java 21)        │
│ ─ Descarga archivo desde S3  │
│ ─ Usa SDK GCP                │
│ ─ Sube archivo a GCS         │
└──────────┬───────────────────┘
           │
           ▼
┌──────────────────────────────┐
│ Google Cloud Storage (GCS)   │
│ (Bucket destino en GCP)      │
└──────────────────────────────┘
