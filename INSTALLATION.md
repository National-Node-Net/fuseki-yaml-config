# Installation

**Repository:** `fueski-yaml-config`  
**Description:** `This file provides detailed installation steps, including required dependencies and configurations for fuseki-yaml-config.`
<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

## 1. Build:
```sh
cd fuseki-yaml-config

mvn clean

# Download the specified version of confluentinc/cp-kafka, if the image is missing
docker pull confluentinc/cp-kafka:7.3.3

# Create output directory for tests
mkdir -p target/files/rdf/

# Build
mvn install
```

## 2. Run tests explaining generation of YAML configuration files, e.g.
```sh
mvn test -Dtest=TestRDFConfigGenerator#singleDatabaseTest
```
Refer to [Documentation](documentation.md) for details.

---
© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.

Licensed under the Open Government Licence v3.0.
