plugins {
  java
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)

  // Code Quality
  checkstyle
  jacoco
  alias(libs.plugins.sonarqube)
}

group = "uk.nhs.hee.tis.trainee"
version = "0.46.0"

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}

dependencyManagement {
  imports {
    mavenBom(libs.spring.cloud.dependencies.aws.get().toString())
  }
}

dependencies {
  // Spring Boot
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-validation")

  // Lombok
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")

  // Mapstruct
  implementation(libs.mapstruct.core)
  annotationProcessor(libs.mapstruct.processor)

  implementation(libs.bundles.mongock)

  // Sentry reporting
  implementation(libs.sentry.core)

  // AWS
  implementation("io.awspring.cloud:spring-cloud-aws-starter-s3")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sns")
  implementation(libs.aws.xray)

  implementation("commons-beanutils:commons-beanutils:1.9.4")

  // PDF
  implementation(libs.bundles.pdf.publishing)
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

testing {
  suites {
    configureEach {
      if (this is JvmTestSuite) {
        useJUnitJupiter()
        dependencies {
          implementation(project())
          implementation("org.springframework.boot:spring-boot-starter-test")
        }
      }
    }

    val test by getting(JvmTestSuite::class) {
      dependencies {
        annotationProcessor(libs.mapstruct.processor)
      }
    }

    register<JvmTestSuite>("integrationTest") {
      dependencies {
        implementation("org.springframework.boot:spring-boot-testcontainers")
        implementation("org.testcontainers:junit-jupiter")
        implementation("org.testcontainers:mongodb")
      }

      targets {
        all {
          testTask.configure {
            shouldRunAfter(test)
            systemProperty("spring.profiles.active", "test")
          }
        }
      }
    }

    // Include implementation dependencies.
    val integrationTestImplementation by configurations.getting {
      extendsFrom(configurations.implementation.get())
    }
  }
}

tasks.named("check") {
  dependsOn(testing.suites.named("integrationTest"))
}

tasks.jacocoTestReport {
  reports {
    html.required.set(true)
    xml.required.set(true)
  }
}

tasks.test {
  finalizedBy(tasks.jacocoTestReport)
}
