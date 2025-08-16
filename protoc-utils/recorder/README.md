# protoc-utils-recorder

An exceedingly simple protoc plugin that echos the bytes of the CodeGeneratorRequest sent to the plugin back out as a CodeGeneratorResponse.
This plugin does not deserialize the request, so standard options are ignored, but preserved in the output.
The name of the output file is `code-generator-request.binpb`, and the location of that output can be influenced via the normal protoc `<plugin_name>_out` option.
