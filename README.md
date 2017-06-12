Extends https://github.com/jenkinsci/jenkins-test-harness to include utilities to install standard build tools in the Jenkins under test.

TODO should consider deprecating this repository, and instead allowing plugins to specify `test`-scoped dependencies on tool `zip`s, with some utility to extract those in `jenkins-test-harness`, and an extension handler in `maven-hpi-plugin` allowing them to be added to the classpath.

# Changelog

## 2.1 (upcoming)

* Updated Maven installations.
* Added a Gradle installation.

## 2.0

Initial release during split out from core.
