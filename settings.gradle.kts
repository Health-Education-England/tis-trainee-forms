rootProject.name = "tis-trainee-forms"

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
  }

  versionCatalogs {
    create("libs") {
      from("uk.nhs.tis.trainee:version-catalog:0.0.8")
    }
  }
}
