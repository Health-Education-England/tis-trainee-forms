![CI/CD Workflow](https://github.com/Health-Education-England/tis-trainee-forms/workflows/CI/CD%20Workflow/badge.svg?branch=master)
![CI/CD Workflow](https://github.com/Health-Education-England/tis-trainee-forms/workflows/CI/CD%20Workflow/badge.svg?branch=master&event=deployment_status)
# TIS Trainee Forms

## About
This is a service to manage trainee forms with the following technology:

 - Java 11
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

## Deployment
 - Provide `SENTRY_DSN` and `SENTRY_ENVIRONMENT` as environmental variables
   during deployment.

## Versioning
This project uses [Semantic Versioning](semver.org).

## License
This project is license under [The MIT License (MIT)](LICENSE).
