# SCIM 2.0 Server for PingIDM

A Java-based SCIM 2.0 (System for Cross-domain Identity Management) server that acts as a gateway/proxy for PingOne Advanced Identity Cloud. It translates SCIM 2.0 requests into PingIDM-compatible calls, providing a standard interface for identity management.

## Features

- **SCIM 2.0 Compliant**: Supports standard SCIM 2.0 operations for Users and Groups.
- **Dynamic Schema Management**: Automatically fetches and translates PingIDM schema to SCIM schema.
- **OAuth2 Authentication**: Implements Bearer token authentication for secure API access.
- **Embedded Jetty**: Lightweight, standalone executable using Jetty and Jersey.
- **Attribute Mapping**: Configurable mapping between SCIM attributes and PingIDM managed object fields.
- **Health Monitoring**: Includes a dedicated `/health` endpoint for monitoring.
- **Docker Ready**: Includes a Dockerfile for easy containerization.

## Technologies Used

- **Java 17**
- **Jetty 11**: Embedded Servlet Engine
- **Jersey 3**: JAX-RS Implementation
- **UnboundID SCIM2 SDK**: SCIM 2.0 library
- **Jackson**: JSON Processing
- **Apache HttpClient 5**: For downstream communication with PingIDM
- **Maven**: Build and dependency management

## Prerequisites

- JDK 17 or higher
- Maven 3.6+
- Access to a PingIDM instance

## Configuration

The server is configured via environment variables. The following variables are required:

| Variable | Description | Default |
|----------|-------------|---------|
| `PINGIDM_BASE_URL` | Base URL of the PingIDM instance | (Required) |
| `OAUTH_TOKEN_URL` | OAuth2 Token endpoint URL | (Required) |
| `OAUTH_CLIENT_ID` | OAuth2 Client ID for authentication | (Required) |
| `OAUTH_CLIENT_SECRET` | OAuth2 Client Secret | (Required) |
| `PORT` | Port on which the SCIM server will run | `8080` |
| `SCIM_SERVER_BASE_URL`| Public base URL of this SCIM server | `http://localhost:{PORT}` |
| `SCIM_REALM` | Security realm name for WWW-Authenticate | `scim` |
| `OAUTH_SCOPE` | OAuth2 scope requested | (empty) |
| `PINGIDM_MANAGED_USER_OBJECT` | Name of the managed user object in PingIDM | `alpha_user` |
| `PINGIDM_MANAGED_ROLE_OBJECT` | Name of the managed role object in PingIDM | `alpha_role` |

## Getting Started

### Building the Project

Build the project using Maven:

```bash
mvn clean package
```

This will generate a shaded (executable) JAR file in the `target` directory: `scimserver-1.0-SNAPSHOT.jar`. The Dockerfile expects it to be renamed to `scimserver.jar` in the root directory if building via Docker.

### Running the Server

Set the required environment variables and run the JAR:

```bash
export PINGIDM_BASE_URL=https://idm.example.com/openidm
export OAUTH_TOKEN_URL=https://auth.example.com/as/token.oauth2
export OAUTH_CLIENT_ID=scim-client
export OAUTH_CLIENT_SECRET=secret-password

java -jar target/scimserver-1.0-SNAPSHOT.jar
```

### Running with Docker

Build the Docker image:

```bash
cp target/scimserver-1.0-SNAPSHOT.jar scimserver.jar
docker build -t pingidentity/scim-server .
```

Run the container:

```bash
docker run -p 8080:8080 \
  -e PINGIDM_BASE_URL=https://idm.example.com/openidm \
  -e OAUTH_TOKEN_URL=https://auth.example.com/as/token.oauth2 \
  -e OAUTH_CLIENT_ID=scim-client \
  -e OAUTH_CLIENT_SECRET=secret-password \
  pingidentity/scim-server
```

## API Endpoints

The SCIM API is available at the `/scim/v2` context path.

### Public Endpoints (No Auth Required)

- `GET /scim/v2/ServiceProviderConfig`: Returns SCIM server configuration.
- `GET /scim/v2/Schemas`: Returns supported SCIM schemas.
- `GET /scim/v2/ResourceTypes`: Returns supported resource types (User, Group).
- `GET /health`: Basic health check.

### Protected Endpoints (OAuth2 Bearer Token Required)

- `GET /scim/v2/Users`: List Users (supports filtering and pagination).
- `POST /scim/v2/Users`: Create a new User.
- `GET /scim/v2/Users/{id}`: Get a specific User.
- `PUT /scim/v2/Users/{id}`: Update a User.
- `DELETE /scim/v2/Users/{id}`: Delete a User.
- `GET /scim/v2/Groups`: List Groups.
- `POST /scim/v2/Groups`: Create a new Group.
- `GET /scim/v2/Groups/{id}`: Get a specific Group.

## Testing

Run unit and integration tests using Maven:

```bash
mvn test
```

For OAuth integration tests, a helper script is provided:
`src/test/run-oauth-tests.sh`

## License

Copyright (c) 2026 Ping Identity. All rights reserved.
