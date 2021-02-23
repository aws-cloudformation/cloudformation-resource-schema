## AWS CloudFormation Resource Schema

[![Build Status](https://travis-ci.com/aws-cloudformation/aws-cloudformation-resource-schema.svg?branch=master)](https://travis-ci.com/aws-cloudformation/aws-cloudformation-resource-schema)

This document describes the [Resource Provider Definition Schema](https://github.com/aws-cloudformation/aws-cloudformation-resource-schema/blob/master/src/main/resources/schema/provider.definition.schema.v1.json) which is a _meta-schema_ that extends [draft-07](http://json-schema.org/draft-07/json-schema-release-notes.html) of [JSON Schema](http://json-schema.org/) to define a validating document against which resource schemas can be authored.

## Examples

Numerous [examples](https://github.com/aws-cloudformation/aws-cloudformation-resource-schema/tree/master/src/main/resources/examples/resource) exist in this repository to help you understand various shape and semantic definition models you can apply to your own resource definitions.

## Defining Resources

### Overview

The _meta-schema_ which controls and validates your resource type definition is called the [Resource Provider Definition Schema](https://github.com/aws-cloudformation/aws-cloudformation-resource-schema/blob/master/src/main/resources/schema/provider.definition.schema.v1.json). It is fully compliant with [draft-07](http://json-schema.org/draft-07/json-schema-release-notes.html) of [JSON Schema](http://json-schema.org/) and many IDEs including [IntelliJ](https://www.jetbrains.com/idea/), [PyCharm](https://www.jetbrains.com/pycharm/) and [Visual Studio Code](https://code.visualstudio.com/) come with built-in or plugin-based support for code-completion and syntax validation while editing documents for JSON Schema compliance. Comprehensive [documentation](http://json-schema.org/understanding-json-schema/reference/) for JSON Schema exists and can answer many questions around correct usage.

To get started, you will author a _specification_ for your resource type in a JSON document, which must be compliant with this _meta-schema_. To make authoring resource _specifications_ simpler, we have constrained the scope of the full JSON Schema standard to apply opinions around how certain validations can be expressed and encourage consistent modelling for all resource schemas. These opinions are codified in the _meta-schema_ and described in this document.


### Resource Type Name

All resources **MUST** specify a `typeName` which adheres to the Regular Expression `^[a-zA-Z0-9]{2,64}::[a-zA-Z0-9]{2,64}::[a-zA-Z0-9]{2,64}$`. This expression defines a 3-part namespace for your resource, with a suggested shape of `Organization::Service::Resource`. For example `AWS::EC2::Instance` or `Initech::TPS::Report`. This `typeName` is how you will address your resources for use in CloudFormation and other provisioning tools.

### Resource Shape

The _shape_ of your resource defines the properties for that resource and how they should be applied. This includes the type of each property, validation patterns or enums, and additional descriptive metadata such as documentation and example usage. Refer to the `#/definitions/properties` section of the _meta-schema_ for the full set of supported properties you can use to describe your resource _shape_.

### Resource Semantics

Certain properties of a resource are _semantic_ and have special meaning when used in different contexts. For example, a property of a resource may be `readOnly` when read back for state changes - but can be specified in a settable context when used as the target of a `$ref` from a related resource. Because of this semantic difference in how this property metadata should be interpreted, certain aspects of the resource definition are applied to the parent resource definition, rather than at a property level. Those elements are;

* **`primaryIdentifier`**: Must be either a single property, or a set of properties which can be used to uniquely identify the resource. If multiple properties are specified, these are treated as a **composite key** and combined into a single logical identifier. You would use this modelling to express contained identity (such as a named service within a container). This property can be independently provided as keys to a **READ** or **DELETE** request and **MUST** be supported as the only input to those operations. These properties are usually also marked as `readOnlyProperties` and **MUST** be returned from **READ** and **LIST** operations.
* **`additionalIdentifiers`**: Each property listed in the `additionalIdentifiers` section must be able to be used to uniquely identify the resource. These properties can be independently provided as keys to a **READ** or **DELETE** request and **MUST** be supported as the only input to those operations. These properties are usually also marked as `readOnlyProperties` and **MUST** be returned from **READ** and **LIST** operations. A provider is not required to support `additionalIdentifiers`; doing so allows for other unique keys to be used to **READ** resources.
* **`readOnlyProperties`**: A property in the `readOnlyProperties` list cannot be specified by the customer.
* **`writeOnlyProperties`**: A property in the `writeOnlyProperties` cannot be returned in a **READ** or **LIST** request, and can be used to express things like passwords, secrets or other sensitive data.
* **`createOnlyProperties`**: A property in the `createOnlyProperties` cannot be specified in an **UPDATE** request, and can only be specified in a **CREATE** request. Another way to think about this - these are properties which are 'write-once', such as the [`Engine`](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-rds-database-instance.html#cfn-rds-dbinstance-engine) property for an [`AWS::RDS::DBInstance`](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-rds-database-instance.html) and if you wish to change such a property on a live resource, you should replace that resource by creating a new instance of the resource and terminating the old one. This is the behaviour CloudFormation follows for all properties documented as _'Update Requires: Replacement'_. An attempt to supply these properties to an **UPDATE** request will produce a runtime error from the handler.
* **`deprecatedProperties`**: A property in the `deprecatedProperties` is not guaranteed to be present in the response from a **READ** request. These fields will still be accepted as input to **CREATE** and **UPDATE** requests however they may be ignored, or converted to new API forms when outbound service calls are made.
* **`replacementStrategy`**: As mentioned above, changing a `createOnlyProperty` requires replacement of the resource by creating a new one and deleting the old one. The default CloudFormation replacement behavior is to create a new resource first, then delete the old resource, so as to avoid any downtime. However, some resources are singleton resources, meaning that only one can exist at a time. In this case, it is not possible to create a second resource first, so CloudFormation must Delete first and then Create. Specify either `create_then_delete` or `delete_then_create`. Default value is `create_then_delete`
* **`taggable`**: A boolean type property which defaults to true, indicating this resource type supports updatable [`tagging property`](https://docs.aws.amazon.com/general/latest/gr/aws_tagging.html). Otherwise, it indicates this resource type does not contain any updatable [`tagging properties`](https://docs.aws.amazon.com/general/latest/gr/aws_tagging.html).
* **`propertyTransform`**: Is a map (Map<String, String>) with the keys being property paths and values being jsonata transformation functions (https://jsonata.org/). This property is used to avoid falsely drifted resources. If the handler transforms the input to the resource to an expected value a transform function can be defined for this property to avoid drift.
#### Application

When defining resource semantics like `createOnlyProperties`, `primaryIdentifier` you are expected to use a JSON Pointer to a property definition in the same resource document. Schemas you author can be checked with the CFN CLI `validate` command.

The following (truncated) example shows some of the semantic definitions for an `AWS::S3::Bucket` resource type;

```
{
    "$id": "aws-s3-bucket.json",
    "typeName": "AWS::S3::Bucket",
    "resourceLink": {
        "templateUri": "/s3/home?region=${awsRegion}&bucket=${BucketName}",
        "mappings": {
            "BucketName": "/BucketName"
        }
    },
    "definitions": {
        "NestedDefinitions" : {
              "type" : "object",
              "additionalProperties" : false,
              "properties" : {
                "ReturnData" : {
                  "type" : "boolean"
                },
                "Expression" : {
                  "type" : "string"
                }
          },
    },
    "properties": {
        "Arn": {
            "$ref": "aws.common.types.v1.json#/definitions/Arn"
        },
        "BucketName": {
            "type": "string"
        },
        "Id": {
            "type": "integer"
        },
        "NestedProperty": {
            "$ref": "#/definitions/NestedDefinitions"
        }
    },
    "createOnlyProperties": [
        "/properties/BucketName"
    ],
    "readOnlyProperties": [
        "/properties/Arn"
    ],
    "primaryIdentifier": [
        "/properties/BucketName"
    ],
    "additionalIdentifiers": [
        "/properties/Arn",
        "/properties/WebsiteURL"
    ],
    "propertyTransform": {
        "/properties/Id": "$abs(Id) $OR $power(Id, 2)",
        "/properties/NestedProperty/Expression": $join(["Prefix", Expression])
    }
}
```
**Note:** $OR is supported between 2 Jsontata functions or experessions. It is not supported as part of a string.
Following use of $OR is not supported in propertyTransform:
```"/properties/e": '$join([e, "T $OR Y"])',```

### Relationships

Relationships between resources can be expressed through the use of the `$ref` keyword when defining a property schema. The use of the `$ref` keyword to establish relationships is described in [JSON Schema documentation](https://cswr.github.io/JsonSchema/spec/definitions_references/#reference-specification).

#### Example

The following example shows a property relationship between an `AWS::EC2::Subnet.VpcId` and an `AWS::EC2::VPC.Id`. The schema for the 'remote' type (`AWS::EC2::VPC`) is used to validate the content of the 'local' type (`AWS::EC2::Subnet`) and can be inferred as a dependency from the local to the remote type.

Setting the $id property to a remote location will make validation framework to pull dependencies expressed using relative `$ref` URIs from the remote hosts. In this example, `VpcId` property will be verified against the schema for `AWS::EC2::VPC.Id` hosted at `https://schema.cloudformation.us-east-1.amazonaws.com/aws-ec2-vpc.json`

```
{
    "$id": "https://schema.cloudformation.us-east-1.amazonaws.com/aws-ec2-subnet.json",
    "typeName": "AWS::EC2::Subnet",
    "definitions": { ... },
    "properties": {
        { ... }
        "VpcId": {
            "$ref": "aws-ec2-vpc.json#/properties/Id"
        }
    }
}
```

```
{
    "$id": "https://schema.cloudformation.us-east-1.amazonaws.com/aws-ec2-vpc.json",
    "typeName": "AWS::EC2::VPC",
    "definitions": { ... },
    "properties": {
        "Id": {
            "type": "string",
            "pattern": "$vpc-[0-9]{8,10}^"
        }
    }
}
```

## Divergence From JSON Schema

### Changes

We have taken an opinion on certain aspects of the core JSON Schema and introduced certain constrains and changes from the core schema. In the context of this project, we are not building arbitrary documents, but rather, defining a very specific shape and semantic for cloud resources.

* **`readOnly`**: the readOnly field as defined in JSON Schema does not align with our determination that this is actually a restriction with semantic meaning. A property may be readOnly when specified for a particular resource (for example it's `Arn`), but when that same property is _referenced_ (using `$ref` tokens) from a dependency, the dependency must be allowed to specify an input for that property, and as such, it is no longer `readOnly` in that context. The AWS CloudFormation Resource Schema uses the concept of `readOnlyProperties` for this mechanic.
* **`writeOnly`**: see above

### New Schema-Level Properties

#### insertionOrder

Array types can define a boolean `insertionOrder`, which specifies whether the order in which elements are specified should be honored when processing a diff between two sets of properties.  If `insertionOrder` is true, then a change in order of the elements will constitute a diff.  The default for `insertionOrder` is true.

Together with the `uniqueItems` property (which is native to JSON Schema), complex array types can be defined, as in the following table:

| insertionOrder | uniqueItems    | result   |
| ---------------- | ---------------- | ---------- |
| true           | false          | list     |
| false          | false          | multiset    |
| true           | true           | ordered set    |
| false          | true           | set      |


### Constraints

* **`$id`**: an `$id` property is not valid for a resource property.
* **`$schema`**: a `$schema` property is not valid for a resource property.
* **`if`, `then`, `else`, `not`**: these imperative constructs can lead to confusion both in authoring a resource definition, and for customers authoring a resource description against your schema. Also this construct is not widely supported by validation tools and is disallowed here.
* **`propertyNames`**: use of `propertyNames` implies a set of properties without a defined shape and is disallowed. To constrain property names, use `patternProperties` statements with defined shapes.
* **`additionalProperties`** use of `additionalProperties` is not valid for a resource property. Use `patternProperties` instead to define the shape and allowed values of extraneous keys.
* **`properties` and `patternProperties`** it is not valid to use both properties and patternProperties together in the same shape, as a shape should not contain both defined and undefined values. In order to implement this, the set of undefined values should itself be a subshape.
* **`items` and `additionalItems`** the `items` in an array may only have one schema and may not use a list of schemas, as an ordered tuple of different objects is confusing for both developers and customers. This should be expressed as key:value object pairs. Similarly, `additionalItems` is not allowed.
* **`replacementStrategy`**: a `replacementStrategy` is not valid for a mutable resource that does not need replacement during an update.
* **`taggable`**: marking a resource `taggable` as true requires an update handler to handle tagging update.

## handlers

The `handlers` section of the schema allows you to specify which CRUDL operations (create, read, update, delete, list) are available for your resource, as well as some additional metadata about each handler.

### permissions

For each handler, you should define a list of API `permissions` required to perform the operation.  Currently, this is used to generate IAM policy templates and is assumed to be AWS API permissions, but you may list 3rd party APIs here as well.

### timeoutInMinutes

For each handler, you may define a `timeoutInMinutes` property, which defines the *maximum* timeout of the operation.  This timeout is used by the invoker of the handler (such as CloudFormation) to stop listening and cancel the operation.  Note that the handler may of course decide to timeout and return a failure prior to this max timeout period.  Currently, this value is only used for `Create`, `Update`, and `Delete` handlers, while `Read` and `List` handlers are expected to return synchronously within 30 seconds.

## License

This library is licensed under the Apache 2.0 License.
