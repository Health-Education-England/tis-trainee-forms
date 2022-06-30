Main Branch Status: ![CI/CD Workflow](https://github.com/Health-Education-England/tis-trainee-forms/workflows/CI/CD%20Workflow/badge.svg?branch=main)  
Deployment Status: ![CI/CD Workflow](https://github.com/Health-Education-England/tis-trainee-forms/workflows/CI/CD%20Workflow/badge.svg?branch=main&event=deployment_status)
# TIS Trainee Forms Service

## About

This service is used to retrieve and parse forms from an S3 Bucket, and to attach to saved forms
the 'submissionDate' and 'lastModified' values before saving to the bucket. 


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

| Name               | Description                                   | Default   |
|--------------------|-----------------------------------------------|-----------|
| DB_HOST                         | The MongoDB host to connect to.               | localhost |
| DB_PORT                         | The port to connect to MongoDB on.            |   27017   |
| DB_USER                         | The username to access the MongoDB instance.  |   admin   |
| DB_NAME                         | The name of the MongoDB instance.             |   forms   |
| DB_PASSWORD                     | The password to access the MongoDB instance.  |    pwd    |
| SENTRY_DSN                      | A Sentry error monitoring Data Source Name.   |   local   |
| APPLICATION_FILESTORE_BUCKET    | the S3 bucket the forms are stored in.        |tis-trainee-documents-upload-preprod|

## Deployment
 - Provide `SENTRY_DSN` and `SENTRY_ENVIRONMENT` as environmental variables
   during deployment.
   
## Usage
### Saving Forms
When a form is saved in the front end it is sent to either 'FormRPartAServiceImpl' or 
FormRPartBServiceImpl depending on the form, the metadata is then mapped an FormRPartA
or B object and sent to the AbstractCloudRepository.
from there the trainee-ui a key is created containing the template for the form
recieved, the traineeTisId and the ID of the form. an ObjectMetadata is created containing the 
information from the recieved form and the SubmissionDate is generated and added to the metadata.
##### Save Forms Example
```
PUT api/forms/{formr-parta}
```
```
PUT api/forms/{formr-partb}
```

### getFormRPartBsByTraineeTisId
This method is used to return a collection of submitted and draft forms. when this request is 
received with the TraineeTisId it retrives a list of forms from the AbstractCloudRepository using
the traineeTisId recived, this method maps the stream of formRs to a collection of objects, for 
each object the date type of submissionType is checked, if it is a LocalDateTime format it is added
to the metadata, otherwise it is parsed from LocalDate to LocalDateTime.

##### Get Forms By TraineeTisId Example
```
api/forms/formr-partb/{TraineeTisId}
```
```
api/forms/formr-parta/{TraineeTisId}
```

###getFormRPartBById
this method is used when loading a submitted form to view or continuing a draft form. this method 
needs the form ID, traineeTisId in order to find the form. 





## Versioning
This project uses [Semantic Versioning](semver.org).

## License
This project is license under [The MIT License (MIT)](LICENSE).
