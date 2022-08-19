# Test driver for testing web services with SoapUI based test projects

[![European Union Public Licence 1.2](https://img.shields.io/badge/license-EUPL%201.2-blue.svg)](https://joinup.ec.europa.eu/software/page/eupl)
[![Latest version](http://img.shields.io/badge/latest%20version-2.1.0-blue.svg)](https://github.com/etf-validator/etf-suitd/releases)
[![Java CI](https://github.com/etf-validator/etf-suitd/actions/workflows/gradle.yml/badge.svg)](https://github.com/etf-validator/etf-suitd/actions/workflows/gradle.yml)

The test driver is loaded by the ETF framework at runtime. The test driver
executes [SoapUI](https://www.soapui.org/) based test projects for testing
services.

Please use the [etf-webapp project](https://github.com/interactive-instruments/etf-webapp) for
reporting [issues](https://github.com/interactive-instruments/etf-webapp/issues).


The project can be build and installed by running the gradlew.sh/.bat wrapper with:
```gradle
$ gradlew build install
```

ETF component version numbers comply with the [Semantic Versioning Specification 2.0.0](http://semver.org/spec/v2.0.0.html).

ETF is an open source test framework developed by [interactive instruments](http://www.interactive-instruments.de/en) for testing geo network services and data.


## Installation
Copy the JAR path to the _$driver_ directory. The $driver directory is configured in your _etf-config.properties_ configuration path as variable _etf.testdrivers.dir_. If the driver is loaded correctly, it is displayed on the status page.

## Updating
Remove the old JAR path from the _$driver_ directory and exchange it with the new version.
