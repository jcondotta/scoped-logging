# Maven Central publishing

The project uses the verified `io.github.jcondotta` namespace and MIT license. Version
`0.1.0-alpha.1` is an immutable pre-release for testing from Maven Central. Do not reuse
this version after publication; make a new alpha version for later changes.

## Local preparation

Install GnuPG and create a signing key following [Sonatype's GPG guide](https://central.sonatype.org/publish/requirements/gpg/).
Publish the public key to a keyserver and configure the same signing key in the Central Portal.
Keep the private key and passphrase private. This repository does not contain them.

Create a Central Portal user token and put its username/password in the ignored local
`.mvn/settings.xml` under a `<server>` whose id is `central`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>PORTAL_TOKEN_USERNAME</username>
      <password>PORTAL_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

Pass `-s .mvn/settings.xml` when uploading with these credentials. Maven does not
automatically load a project-local settings file; its default user settings location is
`~/.m2/settings.xml`. Run the commands below from the repository root.

Do not commit settings or put credentials in the POM or command history. The GPG Maven
plugin uses `gpg-agent` in interactive sessions. Configure its signing key locally if the
default GPG key is not the one registered with the Portal.

## Build and inspect before upload

Run the full tests and attach source/Javadoc archives without signing:

```sh
mvn -Prelease clean verify -Dgpg.skip=true
```

Review the POM metadata and the generated `*-sources.jar` and `*-javadoc.jar` files under
each module's `target` directory. Then sign and prepare a bundle locally without uploading:

```sh
mvn -Prelease clean deploy -DskipUpload=true
```

This produces `target/central-publishing/central-bundle.zip`. Review the generated bundle
before uploading it manually through the Central Portal. The `deploy` phase invokes the
Central publishing plugin; `skipUpload` keeps the bundle local. This step requires the
GPG signing key but does not upload using the Portal token.

## Validated deployment

After GnuPG and the local Central Portal token are configured, this command signs, uploads
and waits for Central Portal validation. The POM sets `autoPublish` to `false`, so the
validated deployment still needs an explicit publish action in the Portal:

```sh
mvn -s .mvn/settings.xml -Prelease clean deploy
```

Check all three components (parent POM, core, Spring Boot integration) in the deployment.
Once validation succeeds, publish it in the Portal. Then verify that
`io.github.jcondotta:scoped-logging-core:0.1.0-alpha.1` and
`io.github.jcondotta:scoped-logging-spring-boot:0.1.0-alpha.1` resolve from Maven Central.
