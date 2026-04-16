# Petstore

A faithful re-expression of the canonical [Swagger Petstore](https://github.com/swagger-api/swagger-petstore/blob/8f0dd286987880b4af7bce552aca3813166f3049/src/main/resources/openapi.yaml) spec as a protobuf file with `protoc-gen-openapi` annotations. The test validates that the plugin's output matches the reference YAML from the upstream repository.

## What it exercises

**Security schemes.** Declares both an OAuth2 scheme (`petstore_auth` with implicit flow and scopes) and an API key scheme (`api_key`). Individual operations attach `security` requirements referencing these schemes, including scoped OAuth2 requirements.

**Response headers.** `LoginUser` returns `X-Rate-Limit` and `X-Expires-After` headers, exercising the `headers` field of a `ResponseObject`.

**Extensions.** Operations carry `x-swagger-router-controller` extensions, exercising the `extensions` map on `OperationObject`.

**Tags with external documentation.** The three top-level tags (`pet`, `store`, `user`) each include an `externalDocs` URL, exercising the full `TagObject`.

**All common HTTP methods.** The service uses GET, POST, PUT, and DELETE, covering the typical CRUD surface of an API.

**Complex schemas.** `Pet`, `Order`, and `User` messages include enums, repeated fields, optional fields, and nested messages, exercising basic `SchemaObject` field mapping.

## Peculiarities

The reference spec is OpenAPI 3.0 but the plugin emits OpenAPI 3.1. The stored reference file (`swagger-api.petstore.openapi.yaml`) has been manually adjusted to 3.1 conventions so the test comparison is valid against plugin output.

The `UploadFile` operation uses `multipart/form-data`, but because the spec predates the encoding object from OAS 3.1 this is expressed as a simple schema rather than a full encoding annotation.
