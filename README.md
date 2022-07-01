Main Branch Status: ![CI/CD Workflow](https://github.com/Health-Education-England/tis-trainee-forms/workflows/CI/CD%20Workflow/badge.svg?branch=main)  
Deployment Status: ![CI/CD Workflow](https://github.com/Health-Education-England/tis-trainee-forms/workflows/CI/CD%20Workflow/badge.svg?branch=main&event=deployment_status)
# TIS Trainee Forms Service

## About

This service is used to retrieve and parse forms from an S3 Bucket, and to attach to saved forms
the 'submissionDate' and 'lastModified' values before saving to the bucket.  Forms that are submitted
are saved into the S3 bucket while those that are saved as draft are stored within a Mongo database


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
| SENTRY_DSN                            | A Sentry error monitoring Data Source Name.                                    |           |
| SENTRY_ENVIRONMENT                    | A Sentry error monitoring DSN environment.                                     | local     |
| FEATURE_FORMR_PARTB_COVID_DECLARATION | this controls whether forms are stored in the S3 regardless of Lifecycle State | true      |
| APPLICATION_FILESTORE_BUCKET          | the S3 bucket the forms are stored in.                                         |           |
| APPLICATION_FILESTORE_ALWAYSSTORE     | this feature flag controls whether to show this section in FormR PartB         | false     |

## Usage
### Saving Forms

To save the form that is received a key is made from the ObjectKeyTemplate, TraineeTisID and form
Id and the metadata is mapped from the form into an ObjectMetadata and the submissionDate is generated
in a LocalDateTime format. these objects are then made into a PutObjectRequest and put into the amazonS3
bucket. 

##### Save Forms Example
```
PUT api/forms/formr-parta
```
```
PUT api/forms/formr-partb
```

### getFormRPartBsByTraineeTisId

This method is used to return a collection of submitted and draft forms. when this request is 
received with the TraineeTisId it retrives a list of forms from the S3 bucket using
the traineeTisId, this method maps the stream of formRs received to a collection of objects, for 
each object the date type of submissionType is checked, if it is a LocalDateTime format it is added
to the metadata, otherwise it is parsed from LocalDate to LocalDateTime and then added.

##### Get Forms By TraineeTisId Example
```
GET api/forms/formr-partbs
```
```
GET api/forms/formr-partas
```

###findByIdAndTraineeTisId
this method is used when requesting a single form to view. this method uses an S3ObjectInputStream
to get the form object from the amazonS3 bucket. the data from the InputStream is then mapped
using an objectMapper into a form matching the type requested, this is then returned as an optional
of that form type. 

```
GET api/forms/formr-parta/{Id}
```
```
GET api/forms/formr-partb/{Id}
```

## Testing

to test this service you can right-click on the test file in the context menu and 
click 'run 'tests in trainee-forms'

otherwise you can run the  Gradle `test` task can be used to run automated tests
and produce coverage reports.
```shell
gradlew test
```

## Versioning
This project uses [Semantic Versioning](semver.org).

## License
This project is license under [The MIT License (MIT)](LICENSE).
