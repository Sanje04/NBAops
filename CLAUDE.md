# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`nba-props` is an early-stage Spring Boot app for NBA player props. The owner is learning with Claude Code and building it in small, incremental steps: keep each change small, verify it with tests before moving on, and explain what changed and why.

## Layout

The repo root (`NBAops/`) is a wrapper. The actual Maven project lives in `nba-props/`, so run all build commands from that directory.

- Stack: Spring Boot 4.1.1 (`spring-boot-starter-webmvc`), Java 21, Maven Wrapper 3.9.16
- Base package: `com.example.nbaprops`. `NbaPropsApplication` is the `@SpringBootApplication` entry point, so new components must live in this package or its subpackages to be component-scanned.
- Config: `nba-props/src/main/resources/application.properties`
- `.github/modernize/java-upgrade/` holds artifacts from an automated Java 21 → 25 upgrade (branch `appmod/java-upgrade-*`). It is not application code.

## Commands

The shell is Windows. Use `.\mvnw.cmd` in PowerShell, or `./mvnw` in Git Bash. Run these from `nba-props/`.

```
.\mvnw.cmd test                                             # run all tests
.\mvnw.cmd test -Dtest=NbaPropsApplicationTests             # single test class
.\mvnw.cmd test -Dtest=NbaPropsApplicationTests#contextLoads   # single test method
.\mvnw.cmd spring-boot:run                                  # start app on http://localhost:8080
.\mvnw.cmd clean package                                    # build jar into target/
```

No linter or formatter is configured.
