# KTYLabs Eclipse Plugin — Self-Signed Release Signing

## One-time setup

1. Install a JDK with `keytool` and `jarsigner` in PATH and Python 3 (`py -3`).
2. Run `GenerateCertificate.bat` and choose a strong password.
3. **Never distribute `KTYLabs-Signing.p12` or its password.** Back it up securely.
4. Record the certificate SHA-256 fingerprint and publish it on the official KTYLabs website using a trusted channel.

## Every release

1. In Eclipse PDE, update `MANIFEST.MF`, `feature.xml`, and `site.xml` to the same release version.
2. In the update-site project, choose **Build All** and wait for completion.
3. Run `SignRelease.bat` from this source ZIP's extracted root.
4. Enter your signing-keystore password when prompted. The script signs the plugin and feature JARs, verifies their signatures, refreshes p2 artifact sizes, and produces `features.zip` at the root.
5. Distribute **`features.zip`** alongside **`INSTALLATION.md`**. Do not include the private keystore.

**Important:** Rebuilding the Eclipse update site overwrites signed JARs. Always run `SignRelease.bat` after the final build. Do not repackage or modify signed JAR contents after signing. `features.zip` is the installable update-site archive, not the source ZIP.

## Trust and limitations

Self-signing is free but **does not establish a publicly trusted publisher identity**. Depending on the Eclipse/STM32CubeIDE trust dialog, it may still show `Unknown`, `Untrusted`, or the certificate subject `KTYLabs`. Users should verify the displayed certificate's SHA-256 fingerprint against the value on ktylabs.com before trusting. **Do not trust expired certificates** merely to complete installation.

The scripts do not sign third-party Eclipse dependencies included by the PDE build; those retain their original signatures and trust status. This script updates existing p2 artifact sizes and recognized hashes for the signed KTYLabs JARs, but it does not rebuild full p2 metadata from scratch.
