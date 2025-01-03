plugins {
  java
  id("org.springframework.boot") version "3.3.2"
  id("io.spring.dependency-management") version "1.1.6"

  // Code quality plugins
  checkstyle
  jacoco
  id("org.sonarqube") version "5.1.0.4882"
}

group = "uk.nhs.hee.tis.trainee"
version = "0.21.2"

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}

repositories {
  mavenCentral()
}

dependencyManagement {
  imports {
    mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:3.1.1")
  }
}

dependencies {
  // Spring Boot starters
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  testImplementation("org.springframework.boot:spring-boot-starter-test")

  // AWS-XRay
  implementation("com.amazonaws:aws-xray-recorder-sdk-spring:2.15.1")

  // Lombok
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")

  // MapStruct
  val mapstructVersion = "1.5.5.Final"
  implementation("org.mapstruct:mapstruct:${mapstructVersion}")
  annotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")
  testAnnotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")

  val mongockVersion = "5.4.4"
  implementation("io.mongock:mongock-springboot-v3:${mongockVersion}")
  implementation("io.mongock:mongodb-springdata-v4-driver:${mongockVersion}")

  // Sentry reporting
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:7.14.0")

  // SQS
  implementation("io.awspring.cloud:spring-cloud-aws-starter-s3")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sns")

  implementation("commons-beanutils:commons-beanutils:1.9.4")

  val openHtmlToPdfVersion = "1.0.10"
  implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:${openHtmlToPdfVersion}")
  implementation("com.openhtmltopdf:openhtmltopdf-slf4j:${openHtmlToPdfVersion}")
  implementation("org.jsoup:jsoup:1.17.2")
}

checkstyle {
  config = resources.text.fromArchiveEntry(configurations.checkstyle.get().first(), "google_checks.xml")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
    vendor.set(JvmVendorSpec.ADOPTIUM)
  }
}

sonarqube {
  properties {
    property("sonar.host.url", "https://sonarcloud.io")
    property("sonar.login", System.getenv("SONAR_TOKEN"))
    property("sonar.organization", "health-education-england")
    property("sonar.projectKey", "Health-Education-England_tis-trainee-forms")
    property("sonar.java.checkstyle.reportPaths",
      "build/reports/checkstyle/main.xml,build/reports/checkstyle/test.xml")
  }
}

tasks.jacocoTestReport {
  reports {
    html.required.set(true)
    xml.required.set(true)
  }
}

tasks.test {
  finalizedBy(tasks.jacocoTestReport)
  useJUnitPlatform()
}
