# Project Coding Instructions

## Technology Stack

- Java 21
- Spring Boot
- Spring Data JPA
- Maven
- MySQL

## Architecture

Follow these layers:

Controller -> Service -> Repository -> Database

## Coding Standards
Before implementing or modifying code:
1. Read docs/coding-standards.md.
2. Follow its rules.
3. Do not introduce a different coding style without justification.

## REST API Standards
Before implementing REST endpoints:
1. Read docs/rest-api-standards.md.
2. Follow its HTTP method, status code, validation,
   and exception-handling conventions.

## Testing
Before writing or modifying tests:
1. Read docs/product-api-test-cases.md.
2. Follow applicable scenarios.
3. Add tests for new behavior and error cases.

## General Rules
- Inspect existing code before making changes.
- Reuse existing project patterns.
- Do not add dependencies without justification.
- Do not modify unrelated files.
- Run relevant tests after implementation.
- Report test failures honestly.