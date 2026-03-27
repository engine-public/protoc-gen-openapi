# Missing from generated output (expected but actual: null):
[x] $.info.x-namespace — "swagger" not emitted
[ ] $.paths./pet.put/post request body schema.$ref — "#/components/schemas/Pet" missing (5 occurrences)
[ ] $.paths./pet/{petId} — entire path missing (generated as /pet/{value} instead — diff 8)
[ ] $.components.schemas.Category.$id, .description, .xml — schema-level annotations not emitted
[ ] $.components.schemas.Pet.$schema, .description, .xml, .properties.availableInstances.swagger-extension
[ ] $.components.schemas.PetDetails.$schema, .$vocabulary, .xml
[ ] $.components.schemas.Tag.$id, .xml

# Wrong values:
[ ] $.paths./pet.post.responses.200.content.application/json.schema.description — "A Pet in JSON format" vs "A Pet in JSON Format" (capitalization)
[ ] $.components.schemas.Pet.properties.availableInstances.examples[0] — string "7" vs integer 7
[ ] $.components.schemas.Pet.properties.availableInstances.exclusiveMaximum/Minimum — IntNode(10/1) vs DoubleNode(10.0/1.0)
[ ] $.components.schemas.Pet.required[1] — "photoUrls" vs "photo_urls" (case strategy not applied)
[ ] $.components.schemas.PetDetails.properties.id.examples[0] — string "10" vs integer 10
