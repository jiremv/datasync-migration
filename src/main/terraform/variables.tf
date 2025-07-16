variable "project_id" {
  description = "ID del proyecto de Google Cloud"
  type        = string
}

variable "region" {
  description = "Región donde crear el bucket"
  type        = string
  default     = "US"
}

variable "bucket_name" {
  description = "Nombre único del bucket"
  type        = string
}
