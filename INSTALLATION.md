# Install KTYLabs STM32 CMake Project Configurator in STM32CubeIDE

1. Launch STM32CubeIDE.
2. Select **Help → Install New Software…**.
3. Click **Add… → Archive…**, select the supplied **`features.zip`**, and confirm.
4. If no software appears, uncheck **Group items by category**. Select **KTYLabs STM32 CMake Project Configurator**, then **Next**.
5. Review the installation and license prompts, then choose **Finish**.
6. If prompted to restart STM32CubeIDE, select **Restart Now**.

## Trust Artifacts: avoid expired certificates

The KTYLabs plugin may be **self-signed**. Depending on STM32CubeIDE's Eclipse trust dialog, the signer may appear as **KTYLabs**, **Untrusted**, or **Unknown**. **Do not assume that the label alone proves authenticity.**

- **Do not select or trust an expired Eclipse.org certificate** merely to enable **Trust Selected**.
- Inspect the KTYLabs certificate details and compare its **SHA-256 fingerprint** against the fingerprint published on the official KTYLabs website before accepting it.
- A self-signed KTYLabs certificate is **not automatically trusted** by Java/Eclipse; explicitly trusting it is a user decision.
- Review other certificates independently. Do not enable **Always trust all content**.
- If the installer requires trusting an expired or unverifiable certificate, **cancel** and contact the publisher instead.

## Show installation Progress view

If installation appears stuck (for example at 12%), select **Window → Show View → Other… → General → Progress → Open**. The **Progress** view displays active repository downloads and dependency resolution. Slow progress does not necessarily mean installation has frozen. If remote repository access is blocking the installation, retry with **Contact all update sites during install to find required software** unchecked, provided all dependencies are already installed.

## Verify installation

After restarting, open **Help → About STM32CubeIDE → Installation Details** and search for `ktylabs` in **Installed Software** or **Plug-ins**. Select your STM32 project in Project Explorer and launch the configurator from the **KTYLabs** menu or its toolbar command, if present.

## Updating

For a newer release, repeat **Help → Install New Software…** with its new update-site ZIP, or use **Help → Check for Updates** if the repository has been configured for updates. The ZIP filename itself can be changed; its internal update-site structure is what matters.
