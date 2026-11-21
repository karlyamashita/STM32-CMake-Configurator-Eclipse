# Build and install the KTYLabs STM32 CMake Configurator update site

This ZIP is **Eclipse PDE source projects**, not an already-built p2 repository. Do not select this ZIP in STM32CubeIDE > Install New Software.

## Export the installable update-site ZIP in Eclipse RCP 2021-03

1. Extract this ZIP to a temporary directory, keeping all three projects as sibling directories.
2. In your **Eclipse RCP development IDE**, use File > Import > General > Existing Projects into Workspace; select the extracted parent directory and import all three projects:
   - `com.ktylabs.stm32configurator` (original plugin project)
   - `com.ktylabs.stm32configurator.feature` (feature project)
   - `com.ktylabs.stm32configurator.updatesite` (update-site project)
3. Ensure the plugin project has no build errors. Its manifest targets `JavaSE-11`.
4. Open `com.ktylabs.stm32configurator.updatesite/site.xml` with the **Site Manifest Editor** (right-click > Open With > Site Manifest Editor if necessary).
5. In the editor's **Site** tab, check the feature `com.ktylabs.stm32configurator.feature` appears. Select **Build All** (or **Build**, depending on PDE version). Eclipse will build the feature and plugin and publish p2 metadata.
6. Confirm the update-site project now contains `content.jar` or `content.xml`, `artifacts.jar` or `artifacts.xml`, and `features/` and `plugins/` directories. If metadata was not generated, use the Site Manifest Editor's **Export...** button or File > Export > Plug-in Development > Deployable features, selecting the feature and enabling **Generate metadata repository**.
7. ZIP **the contents of the built update-site directory**, so that `content.jar` / `content.xml` and `artifacts.jar` / `artifacts.xml` are at the ZIP root. Do not ZIP only the plugin JAR or ZIP the enclosing project folder.

## Install into STM32CubeIDE 2.3.0

1. Remove the manually copied KTYLabs JAR from STM32CubeIDE `dropins` to avoid duplicate installations; restart if necessary.
2. Open Help > Install New Software > Add > Archive and select the **built update-site ZIP** from step 7 (not this source ZIP).
3. Select **KTYLabs STM32 CMake Project Configurator** and complete the wizard; restart when prompted.
4. Verify the plugin in Help > About > Installation Details > Plug-ins, then select a CMake project and open KTYLabs > STM32 CMake Project Configurator.

If installation fails with dependency errors, save the exact error details. A successful PDE build does not guarantee compatibility with STM32CubeIDE's particular Eclipse package.

**Important:** The original plugin project files and names are preserved. This source bundle adds only a feature project, update-site project, and these instructions.

## Fix for "Unable to find Installable Unit ... feature.group"

The previous update-site source used `0.1.0.qualifier` in the feature and site definitions. The PDE build resolved the feature to a timestamped version, while the site build could not find the matching installable unit. This revision pins the bundle, feature, and site references to **0.1.0**.

**Before rebuilding:** Delete any previously generated `features/`, `plugins/`, `artifacts.jar`, `content.jar`, `artifacts.xml`, and `content.xml` inside the update-site project, if present. Import/refresh all three updated projects; select **Project > Clean** and rebuild the feature and site. Do not manually edit the generated feature JAR version.

If PDE still cannot find the feature, verify the feature project is open in the workspace and that its `feature.xml` references `com.ktylabs.stm32configurator` version `0.1.0`.


## Resolving “Unable to find plug-in com.ktylabs.stm32configurator_0.1.0”

The feature plug-in reference now uses `version="0.0.0"`, the PDE wildcard that allows Eclipse to find the plug-in bundle regardless of its timestamp qualifier. The feature itself remains version 0.1.0.

1. Import **all three** projects into the **same Eclipse workspace**, including `com.ktylabs.stm32configurator` (project folder `STM32_CMake_Configurator_Eclipse_Plugin`). Do not import only the feature and update-site projects.
2. In Project Explorer, check that the plug-in project is **open** (not greyed out) and its manifest's Bundle-SymbolicName is `com.ktylabs.stm32configurator`.
3. Open `com.ktylabs.stm32configurator.feature/feature.xml`, choose **Plug-ins** tab, and confirm the listed plug-in is `com.ktylabs.stm32configurator`.
4. Refresh all projects (F5), select Project > Clean, and ensure the plug-in project has no Java errors.
5. Open the update site's `site.xml` and select **Build All**.
6. If the plug-in is still not found, use **Run > Run Configurations > Eclipse Application > Plug-ins** to check that the workspace plug-in is visible, or reimport the original plug-in project into the same workspace.

**Do not add the standalone plug-in JAR as a software site.** The update-site build must create repository metadata first.
