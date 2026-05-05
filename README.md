# protoc-gen-openapi
protoc compiler to turn gRPC services into openapi v3.1 specs

Early chicken scratch notes:
This project ultimately compiles the plugin down to a native binary so it can be used directly in a protoc invocation without special setup.
Protobuf compilation uses some amount of reflection by default, which makes setting up the graalvm native-image somewhat tricky.

## Run Configuration

There are three intellij run configurations checked in to the repo to assist in this process.

* `protoc-gen-openapi-example:generateProto`: Run the native plugin on the example project, capturing the Code Generator Request to /var/tmp/protoc-gen-openapi.cgreq
* `MainKt`: Run the plugin from the command line, using the contents of /var/tmp/protoc-gen-openapi.cgreq as its input.  Useful for debugging.
* `protoc-gen-openapi:run`: Run the non-native version of the plugin with the Graalvm agent to capture the reflection metadata.

## Development/PR Process
1. Do your general work, write your tests, mostly using `MainKt` or unit tests
2. Run `protoc-gen-openapi-example:generateProto` to run your plugin in context of the example project.
3. If the previous step failed...
  * run `protoc-gen-openapi:run` to generate new metadata
  * run `protoc-gen-openapi:metadataCopy` to copy and merge the metadata
  * Repeat steps 1-3

## Plugin Options

Options are passed via `--openapi_out=option=value,option2=value2:outdir`.

| name                          | type    | default | description                                                                                                                                                                       |
|-------------------------------|---------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `merge`                       | boolean | `false` | Merge all services from all target files into a single OpenAPI document.                                                                                                          |
| `version`                     | string  |         | Fallback `info.version` written to every generated document that does not already have a version from an annotation.                                                              |
| `outputFormat`                | enum    | `JSON`  | Serialization format of generated documents. `JSON` (default) or `YAML` (case-insensitive).                                                                                      |
| `autoTagServices`             | boolean | `false` | Automatically tag every operation with its enclosing service name, and emit a top-level `tags` entry per service using the service's proto comment as the description.            |
| `validateOutput`              | boolean | `false` | Validate each generated document against the official OAS 3.1.1 schema and surface validation errors as compiler errors.                                                         |
| `schemaNamespaceStrategy`          | enum    | `NONE`  | Controls how proto package segments are incorporated into `components/schemas` keys. `NONE` uses the unqualified message name. `FULL_PACKAGE` prefixes the key with every package segment. `SIMPLIFIED_PACKAGE` strips the longest common package prefix shared by all schemas in the same document before prefixing. |
| `schemaNamespaceSeparator`         | enum    | `NONE`  | Separator between package segments and the message name. `NONE` concatenates directly. `UNDERSCORE` uses `_`. `DASH` uses `-`. `DOT` uses `.`. |
| `schemaNamespaceCasing`            | enum    | `NONE`  | Capitalisation applied to each package segment. `NONE` leaves segments as written. `CAPITALIZED` uppercases the first character of each segment. `UPPER_CASE` uppercases every character. Version segments extracted via `schemaNamespaceVersionExtraction` are always kept lowercase. |
| `schemaNamespaceVersionExtraction` | boolean | `false` | When `true`, package segments matching the proto versioning convention (`v1`, `v2beta1`, etc.) are moved to the end of the schema key, after the message name, and are never capitalised. |
| `setSchemaTitleToMessageName`       | boolean | `false` | When `true`, adds a `"title"` field to each schema in `components/schemas` set to the unqualified proto message name. If a `engine.protoc.openapi.message` annotation explicitly sets `title`, the annotation value takes precedence. |
| `serviceInclude`              | regex   | `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)*$` | Regex matched (via `containsMatchIn`) against the fully-qualified service name (`<package>.<ServiceName>`). Only services whose name contains a match are included. Default matches every valid proto fully-qualified identifier, so all services pass. Schema types referenced exclusively by excluded services are also suppressed. |
| `serviceExclude`              | regex   |         | Regex matched (via `containsMatchIn`) against the fully-qualified service name. Services whose name contains a match are excluded, even if they also matched `serviceInclude`. Absent by default — no services excluded. |
| `recordCodeGeneratorRequest`  | path    |         | A path to which the Code Generator Request will be written after having been read from `stdin`.                                                                                   |
| `recordCodeGeneratorResponse` | path    |         | A path to which the Code Generator Response will be written, in addition to `stdout`.                                                                                             |

## Environment Variables

Plugin options sent to protoc plugins are sent as a part of the serialized protobuf Code Generator Request pass as bytes via stdin.
Unfortunately, any deserialization errors that occur (due to a improperly created native-image, for example) prevent you from capturing these options.

The following environment variables are honored to support development of the plugin and debugging of issues.

| name                              | description                                                                                                                                                                                                       |
|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PROTOC_GEN_OPENAPI_RECORD_CGREQ` | A path to which the full contents of `stdin` will be written.<br/>This file can then be passed back in via the `PROTOC_GEN_OPENAPI_REPLAY_CGREQ` environment variable to debug the code outside the native-image. |
| `PROTOC_GEN_OPENAPI_REPLAY_CGREQ` | A path from which a Code Generator Request will be read in place of `stdin`.                                                                                                                                      |

