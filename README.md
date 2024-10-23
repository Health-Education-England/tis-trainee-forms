# TIS Trainee Forms Service


[![Build Status][build-badge]][build-href]
[![License][license-badge]][license-href]
[![Quality Gate Status][quality-gate-badge]][quality-gate-href]
[![Coverage Stats][coverage-badge]][coverage-href]


## About

This service is used to retrieve and parse forms from an S3 Bucket, and to attach to saved forms
the 'submissionDate' and 'lastModified' values before saving to the bucket. Forms that are not in a Draft state
are saved into the S3 bucket; all forms are saved in the Mongo database

This is a service to manage trainee forms with the following technology:

 - Java 17
 - Spring Boot
 - Gradle
 - JUnit 5

Boilerplate code is to be generated with:
 - Lombok
 - MapStruct

Code quality checking and enforcement is done with the following tools:
 - EditorConfig
 - Checkstyle
 - JaCoCo
 - SonarQube

Error and exception logging is done using Sentry.

#### Environmental Variables

| Name                                  | Description                                                                    | Default   |
|---------------------------------------|--------------------------------------------------------------------------------|-----------|
| DB_HOST                               | The MongoDB host to connect to.                                                | localhost |
| DB_PORT                               | The port to connect to MongoDB on.                                             | 27017     |
| DB_USER                               | The username to access the MongoDB instance.                                   | admin     |
| DB_NAME                               | The name of the MongoDB instance.                                              | forms     |
| DB_PASSWORD                           | The password to access the MongoDB instance.                                   | pwd       |
| ENVIRONMENT                           | The environment to log events against.                                         | local     |
| SENTRY_DSN                            | A Sentry error monitoring Data Source Name.                                    |           |
| FEATURE_FORMR_PARTB_COVID_DECLARATION | This feature flag controls whether to show this section in FormR PartB         | false     |
| APPLICATION_FILESTORE_BUCKET          | The S3 bucket the forms are stored in.                                         |           |
| APPLICATION_FILESTORE_ALWAYSSTORE     | This controls whether forms are stored in the S3 regardless of Lifecycle State | false     |
| DELETE_EVENT_QUEUE                    | The URL of the SQS queue to partial delete forms from DB.                      |           |
| SIGNATURE_SECRET_KEY                  | The secret key used to validate signed data.                                   |           |

### Saving Forms

To save the form that is received a key is made from the ObjectKeyTemplate (which comprises of TraineeTisID and Id.json)
, TraineeTisID and form Id and the metadata is mapped from the form into an ObjectMetadata and the submissionDate is
generated in a LocalDateTime format.

##### Save Forms Example
```
PUT api/forms/formr-parta
```
```
PUT api/forms/formr-partb
```

### getFormRPartBsByTraineeTisId

This method is used to request submitted and draft forms in a collection of FormROartSimpleDtos in JSON
format. When this method recives the TraineeTisId it creates an ObjectListing, this listing contains each forms
metadata of 'id', 'traineeid', 'submissiondate' and 'lifecyclestate'. 
i.e. [{"id":"62bac33d332e487b84a3bdcc","traineeTisId":"47165","submissionDate":"2022-06-28T09:01:00.337","lifecycleState":"SUBMITTED"}]
If the submission date is not a localDateTime format it is parsed and then entered into the metadata.
##### Get Forms By TraineeTisId Example
```
GET api/forms/formr-partbs
```
```
GET api/forms/formr-partas
```

###findByIdAndTraineeTisId
This method is used when requesting a single form to view. This method uses an S3ObjectInputStream
to get the form object from the amazonS3 bucket. The data from the InputStream is then mapped
using an objectMapper into a form matching the type requested, this is then returned as an optional
of that form type. 

```
GET api/forms/formr-parta/{Id}
```
```
GET api/forms/formr-partb/{Id}
```

### Event Listener

The service responds to available messages on the queues from which it reads. The examples below use
localstack to post messages, but the equivalent example using AWS SQS would mean simply posting the
message-body content to the appropriate queue.

##### Partial Delete FormR Example
```
awslocal sqs send-message 
  --queue-url {DELETE_EVENT_QUEUE} 
  --message-body '{
    "deleteType": "PARTIAL",
    "bucket": "{APPLICATION_FILESTORE_BUCKET}",
    "key": "47165/forms/formr-a/8ca0402a-7d7e-4a23-ae30-6f6716ab4363.json",
    "fixedFields":["id","traineeTisId","lifecycleState","submissionDate","lastModifiedDate"]
  }'
```

## Testing
You can run the  Gradle `test` task can be used to run automated tests
and produce coverage reports.
```shell
gradlew test
```

## Versioning
This project uses [Semantic Versioning](semver.org).

## License
This project is license under [The MIT License (MIT)](LICENSE).

[coverage-badge]:
https://sonarcloud.io/api/project_badges/measure?project=Health-Education-England_tis-trainee-forms&metric=coverage
[coverage-href]:
https://sonarcloud.io/component_measures?metric=coverage&id=Health-Education-England_tis-trainee-forms
[build-badge]: https://badgen.net/github/checks/health-education-england/tis-trainee-forms?label=build&icon=github
[build-href]: https://github.com/Health-Education-England/tis-trainee-forms/actions/workflows/ci-cd-workflow.yml
[license-badge]: https://badgen.net/github/license/health-education-england/tis-trainee-forms
[license-href]: LICENSE
[quality-gate-badge]: https://sonarcloud.io/api/project_badges/measure?project=Health-Education-England_tis-trainee-forms&metric=alert_status
[quality-gate-href]: https://sonarcloud.io/summary/new_code?id=Health-Education-England_tis-trainee-forms
