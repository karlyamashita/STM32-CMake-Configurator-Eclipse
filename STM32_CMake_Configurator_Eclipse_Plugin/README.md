# KTYLabs STM32 CMake Project Configurator — Eclipse/STM32CubeIDE plugin

This is an **initial native Java/SWT Eclipse plugin project**. It is **not** the Rust executable embedded in Eclipse. It opens a resizable native dialog from the **KTYLabs** main menu, an Eclipse toolbar command, or a project context menu.

## Current features

- Auto-detect the selected project from Project Explorer (or browse to another project directory).
- Open `CMakeLists.txt`; enable BIN, HEX, and size output; edit seven existing `target_*` argument blocks; add PRE_LINK and POST_BUILD commands; preview and save with `.bak` backup.
- Find a FLASH linker `.ld` file; edit FLASH start/end inclusive; preview and save with `.bak` backup.
- Locate an ELF and compare its first FLASH PT_LOAD address to the selected linker FLASH start (initial verification, not full section/symbol validation).
- Custom post-build commands and generated firmware output commands are combined in one managed `POST_BUILD` block. Existing unmanaged BIN/HEX commands are preserved; the plugin does not add another equivalent command if it detects `-O binary` or `-O ihex` outside its block.

## Build from Eclipse PDE (recommended)

1. Install **Eclipse IDE for RCP and RAP Developers** (or an Eclipse package with **Plug-in Development Environment / PDE**) and a Java 17+ JDK. For compatibility testing, use the Eclipse/PDE release closest to the Eclipse platform in your STM32CubeIDE installation.
2. In Eclipse choose **File > Import > General > Existing Projects into Workspace**.
3. Choose the extracted directory `STM32_CMake_Configurator_Eclipse_Plugin`, check `com.ktylabs.stm32configurator`, and click **Finish**.
4. Open **Window > Preferences > Plug-in Development > Target Platform**. Start with the running Eclipse installation for a smoke test. For STM32CubeIDE compatibility, define a target platform using the STM32CubeIDE installation's Eclipse plugins/features, then resolve and test against that target. Target compatibility can require additional work depending on the STM32CubeIDE release.
5. Open `META-INF/MANIFEST.MF` and check **Dependencies**; wait for Eclipse to finish building. There should be no Java compilation errors.
6. Right-click the plugin project > **Run As > Eclipse Application** (or create a Run Configuration of type **Eclipse Application**). In the launched test Eclipse, choose **KTYLabs > STM32 CMake Project Configurator**.
7. To package, right-click the plugin project > **Export > Plug-in Development > Deployable plug-ins and fragments**. Choose a destination directory. Eclipse creates a versioned plugin JAR inside `plugins/` (depending on your export settings).

## Installing the built plugin into STM32CubeIDE

**For development/testing**, use a disposable copy of STM32CubeIDE or an isolated configuration; do not modify ST-supplied JARs.

- A PDE-exported plugin JAR by itself is **not** an Eclipse update-site ZIP. For a simple local test, copy the exported `com.ktylabs.stm32configurator_*.jar` into the target Eclipse installation's `dropins/` directory, restart STM32CubeIDE with `-clean` once if needed, and check the KTYLabs menu. Eclipse `dropins` behavior depends on the IDE packaging.
- For a supported **Help > Install New Software > Add > Archive** experience, first build an Eclipse **Feature Project** containing this plugin and an **Update Site Project** containing the feature, then export/build the repository. This source ZIP is **not** an update site.
- A test in a standalone Eclipse Application does **not** prove compatibility with STM32CubeIDE; test there before distributing.

## Launching the GUI after installation

1. Open STM32CubeIDE and select your STM32 project in Project Explorer.
2. Click **KTYLabs > STM32 CMake Project Configurator** (or the toolbar/context-menu command).
3. Check BIN/HEX options or edit arguments, preview the CMake changes, then click **Apply CMake Changes + Backup**.
4. Changes to `CMakeLists.txt` take effect when CMake reconfigures and the project rebuilds.

## Important limitations of this initial version

- The Rust program has more sophisticated parsing and ELF analysis. This plugin is a **functional first implementation**, not feature-for-feature parity.
- The target argument editors are blank initially and act as **replacement values** when edited; existing target calls are preserved untouched until you enter replacements. Do not assume a blank field means the existing CMake arguments are empty.
- `PRE_LINK` runs after compilation and before linking; it is **not** a pre-compilation step.
- User-provided build commands are inserted into CMake; review the preview before saving.
- CMake output-generation options only add managed commands if equivalent unmanaged `-O binary` / `-O ihex` text is not detected. The check is textual, not a full CMake parser.
- Existing manual preview edits may be overwritten if you change GUI controls afterward. Save the final preview after making changes.
- `CMakeLists.txt` and `.ld` are saved as UTF-8, with a sibling `.bak` copy.
- The plugin has not been compiled or launched in STM32CubeIDE in this environment; Eclipse/PDE and the target IDE must be used to validate it.

## Project layout

- `plugin.xml` — menu, toolbar, context menu, command and handler registrations.
- `META-INF/MANIFEST.MF` — OSGi bundle metadata and Eclipse dependencies.
- `src/.../OpenConfiguratorHandler.java` — detects selected project and launches the dialog.
- `src/.../ConfiguratorDialog.java` — native SWT/JFace GUI.
- `src/.../ProjectFiles.java` — CMake and linker text transformations, backup I/O.
- `src/.../ElfVerifier.java` — basic ELF program-header FLASH origin verification.


## Automatically open the selected project

In STM32CubeIDE, select the STM32 project in **Project Explorer**, then choose
**KTYLabs > STM32 CMake Project Configurator** (or the toolbar/context-menu command).
The plugin resolves the Eclipse `IProject` from the selection and loads its
`CMakeLists.txt` automatically, including when the file is under a subdirectory.
If no project is selected, it attempts to use the active editor's project.
The **Browse** controls remain available for manual selection.

The linker script and ELF are searched within the selected project by the
existing discovery logic; automatic linker selection will be refined separately.

**Safety:** The current initial GUI does not yet populate existing `target_*`
arguments into the left-side controls. Review the preview before applying any
CMake changes; full argument parsing is planned as a separate improvement.

### CMake GUI corrections

- The CMake settings column has a vertical scrollbar so all seven `target_*` groups and the build hooks remain accessible.
- Existing `target_*` command arguments populate from `CMakeLists.txt` in their original order; comments and empty lines are excluded from the editable argument fields.
- Edits update the original matching command in the preview while retaining its `PRIVATE`, `PUBLIC`, or `INTERFACE` scope.
- Check the preview before applying; a `.bak` is created on save.

### Linker tabs

The **Flash Linker Script** and **RAM Linker Script** tabs independently discover matching `.ld` files under the selected project. If multiple files of a type are found, choose from the dropdown (relative paths are displayed). A RAM linker script is optional and may not be used by the active build. Each tab has a separate editable preview and backup-on-save. The ELF verification tab uses the selected FLASH linker script.

### Live linker address preview

FLASH and SRAM linker-script previews update automatically as the start/end hex addresses change. Invalid or incomplete values display an inline error and disable Apply until corrected; no separate Update Preview button is needed. Changes are saved only when Apply is clicked, with a `.bak` backup. The RAM linker tab is now labeled **SRAM Linker Script**.

## FLASH erase-boundary validation and persistent-data reservation

The Flash Linker Script tab has an explicit erase unit size selector (512 B to 256 KB),
and a number of erase units to reserve at the end of the FLASH region. Choose the
**actual** erase size from the MCU reference manual; this is not automatically
detected. FLASH start and end+1 must be aligned to the chosen unit. Invalid
addresses or an overlapping reservation disable Apply and report inline errors.
Selecting a positive reservation count adjusts the application end in the preview
without writing the linker file until Apply. For MCUs with variable-sized sectors
(e.g. some STM32F4 variants), this uniform-size validator is not sufficient:
verify sector boundaries independently. Reserving FLASH in the linker script
does not protect it from mass erase or an external programmer; configure the
programmer/bootloader to preserve user-data pages.
