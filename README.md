# KTYLabs STM32 CMake Project Configurator — Eclipse Java Source

Java/Eclipse PDE source for the KTYLabs STM32 CMake Project Configurator plugin for STM32CubeIDE. This repository is for **building and signing releases**. The separate distribution repository provides the installable `STM32-CMake-Configurator.zip` and its SHA-256 checksum.

## Why I created this configurator

STM32CubeIDE does not provide a straightforward GUI option in its CMake workflow to generate **HEX** or **BIN** files after a build. Normally, I would have to edit `CMakeLists.txt` manually and add the appropriate post-build commands. I could never remember the CMake keywords and syntax for generating those files, so I created this configurator to do it for me.

The configurator also makes common linker-script changes easier. Instead of manually editing memory addresses, I can adjust the **FLASH start address** to leave space for a bootloader and place the application code where it belongs. I can also **reserve the last FLASH page or erase unit** for user arguments or other persistent application data. The GUI makes these changes more convenient and reduces the chance of mistakes when editing CMake and linker scripts by hand.

## Download Prebuilt Plugin

If you prefer not to build the Java project from source, you can download the prebuilt plugin from the [GitHub Releases](../../releases) page.

The release includes the files needed to install the plugin directly into STM32CubeIDE. See the installation instructions in the release repository for setup details.

<img width="394" height="712" alt="GitHub Releases download section" src="https://github.com/user-attachments/assets/0b01bfa3-42da-4f7f-8a64-b043430c59db" />

## Configurator GUI preview

The screenshots below shows the STM32 CMake Project Configurator interface inside STM32CubeIDE.

<img width="1914" height="1025" alt="STM32 CMake Project Configurator GUI" src="https://github.com/user-attachments/assets/65296b3d-cecf-4345-92d2-654a694ce9a2" />
<img width="1046" height="394" alt="image" src="https://github.com/user-attachments/assets/1354ea63-4add-47ae-a068-995db108fd2f" />

## Project layout

```text
STM32-CMake-Configurator-Eclipse/
├── STM32_CMake_Configurator_Eclipse_Plugin/
├── com.ktylabs.stm32configurator.feature/
├── com.ktylabs.stm32configurator.updatesite/
├── GenerateCertificate.bat
├── SignRelease.bat
├── ReleaseTools.py
└── .gitignore
```

## Build environment and requirements

**Tested build environment:** Windows 11 Pro **x64**, Eclipse IDE **2021-03** with Plug-in Development Environment (PDE), and **JDK 17**. The resulting plugin targets STM32CubeIDE 2.3.0. **Windows ARM64 builds have not been tested.**

- Install a **64-bit JDK 17** (not just a JRE). Configure Eclipse to launch using JDK 17; this is also the JDK used for the `keytool` and `jarsigner` commands in the release scripts.
- Install **Eclipse IDE 2021-03 with PDE** (or add Plug-in Development Environment to that Eclipse installation). Import all three Eclipse projects into the workspace.
- Ensure the JDK `bin` directory is on `PATH`, and check `java -version`, `keytool -help`, and `jarsigner -help` in Command Prompt.
- The plugin may declare an older Java execution environment for runtime compatibility; that does not mean you should use an older JDK for the signing/build workstation.
- Python 3 with the Windows `py` launcher (`py -3`).
- Windows Command Prompt to run the `.bat` scripts.

## First-time setup: `GenerateCertificate.bat`

**The developer signing keystore is intentionally NOT included in this repository.** Each developer must create their own keystore locally by running `GenerateCertificate.bat`. The script:

1. Checks that the JDK `keytool` command is available.
2. Creates a **self-signed KTYLabs code-signing certificate** in `KTYLabs-Signing.p12` (RSA 3072-bit, 10-year validity).
3. Prompts the developer to create their own private keystore password.
4. Displays the certificate's SHA-256 fingerprint, which you can publish on the official KTYLabs website for users to compare.

Double-click `GenerateCertificate.bat`, or run it in Command Prompt:

```bat
GenerateCertificate.bat
```

**Developer security:** The generated `KTYLabs-Signing.p12` is private to the developer who runs the script. **Never commit, upload, publish, or distribute the keystore file or its password**, including in GitHub commits, releases, screenshots, or documentation. Store the password securely and keep an encrypted backup of the keystore outside the repository. The script refuses to overwrite an existing keystore.

**Signing identity:** A newly generated keystore is a **different signing identity** from the original KTYLabs developer certificate, even if its certificate subject says KTYLabs. Its SHA-256 certificate fingerprint will differ. Developers should not claim their independently generated certificate is the official KTYLabs signing certificate. To publish an official release, use an authorized signing process and publish the corresponding certificate fingerprint through an official channel.

## Build the Eclipse update site

1. Import the **three Eclipse projects** listed above into your Eclipse PDE workspace.
2. Update the version consistently in:
   - `STM32_CMake_Configurator_Eclipse_Plugin/META-INF/MANIFEST.MF` (`Bundle-Version`)
   - `com.ktylabs.stm32configurator.feature/feature.xml` (`version`)
   - `com.ktylabs.stm32configurator.updatesite/site.xml` (feature reference)
3. Open `site.xml` in the update-site project and select **Build All**.
4. Wait until the update-site project contains `artifacts.jar`, `content.jar`, `site.xml`, and the built `features/` and `plugins/` directories.

Use a concrete release version rather than `.qualifier` for the signing workflow.

## Every release: `SignRelease.bat`

**Run this after the final Eclipse Build All**, not before. From the repository root:

```bat
SignRelease.bat
```

The script:

1. Confirms `KTYLabs-Signing.p12` exists and that `jarsigner` and Python are available.
2. Reads the release version from `feature.xml` using `ReleaseTools.py`.
3. Signs the current-version KTYLabs **plugin JAR** and **feature JAR** with your certificate. Enter the keystore password when prompted.
4. Verifies both JAR signatures.
5. Runs `ReleaseTools.py package` to package the update site and refresh release metadata/checksums.

Expected output in the repository root:

```text
STM32-CMake-Configurator.zip
STM32-CMake-Configurator.sha256.txt
```

The ZIP contains the Eclipse update-site files (`artifacts.jar`, `content.jar`, `site.xml`, `features/`, and `plugins/`). **Do not manually ZIP those files**: the script packages them for you.

The checksum file contains the SHA-256 of the final ZIP. You can verify it in PowerShell:

```powershell
Get-FileHash -Algorithm SHA256 .\STM32-CMake-Configurator.zip
```

Compare the output with `STM32-CMake-Configurator.sha256.txt`. Upload both release files to your distribution repository, together with its end-user README.

**Important:** If you run Eclipse **Build All** again, rerun `SignRelease.bat` afterward; rebuilding can overwrite signed JARs. Do not modify JARs after signing. Keep the signing keystore out of the release ZIP.

## Troubleshooting

- **`keytool` or `jarsigner` not found:** Add your JDK `bin` directory to `PATH` and reopen Command Prompt.
- **`py` not found:** Install Python 3 with the Windows launcher, or configure the launcher in your environment.
- **Plugin/feature JAR not found:** Build the update site in Eclipse first and confirm the version numbers match.
- **Certificate already exists:** Reuse your own existing `KTYLabs-Signing.p12`; do not regenerate it for each release. The original developer keystore is not distributed with the repository.
- **STM32CubeIDE shows Unknown/Unsigned:** Self-signed certificates are not automatically trusted. The trust dialog may still show untrusted or unsigned artifacts; users should inspect the component list and verify the certificate fingerprint before trusting. Do not trust expired certificates merely to proceed.

## Distribution and licensing

This repository contains the Java source and release tooling. Distribute the generated ZIP and checksum separately. A self-signed certificate is free but does **not** provide third-party verification of publisher identity.

KTYLabs: https://ktylabs.com/
