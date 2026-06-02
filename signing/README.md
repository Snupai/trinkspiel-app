# Release Signing

Real release keystores and passwords must stay local. The Gradle build reads signing values in this order:

1. Gradle properties such as `-PSEEMOPS_RELEASE_STORE_FILE=...`
2. Environment variables such as `SEEMOPS_RELEASE_STORE_FILE`
3. `signing/release-signing.properties`

The default keystore path is `signing/seemops-release.keystore`, relative to the project root.

Use `scripts/create-release-keystore.sh` to create a keystore. The helper requires `keytool`, refuses to overwrite an existing keystore, and creates the file with private permissions. Then either export the four `SEEMOPS_RELEASE_*` environment variables or create an ignored `signing/release-signing.properties` file from `release-signing.properties.example`.

Use `scripts/prepare-release-signing-handoff.sh` when signing needs to happen on a different trusted machine. It writes `build/release-signing-handoff/` with the current non-secret signing-check output, safe env/properties templates, checksum evidence, and rebuild/verify steps. It does not include a keystore or real signing passwords.

Keep local signing files private:

```sh
chmod 600 signing/seemops-release.keystore
[[ ! -f signing/release-signing.properties ]] || chmod 600 signing/release-signing.properties
```

Run `scripts/check-release-signing-config.sh` before the final build. It checks that the visible signing inputs are complete, local signing files are not group/world-readable, the keystore file exists, `keytool` can open a private-key alias without printing passwords, the configured key password can sign a temporary verification JAR, and the configured keystore/alias/certificate do not look like Android debug signing material. The final upload gate still verifies the built AAB signature with `scripts/verify-release-ready.sh` and rejects debug-signed bundles.
