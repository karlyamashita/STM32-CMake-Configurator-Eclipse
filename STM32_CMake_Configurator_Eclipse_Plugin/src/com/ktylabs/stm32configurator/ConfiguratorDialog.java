package com.ktylabs.stm32configurator;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public final class ConfiguratorDialog extends Dialog {
    private Path project, cmake, linker, ramLinker, elf;
    private Path workspaceCmake;
    private String originalCmake = "", originalLinker = "", originalRamLinker = "";
    private Text projectPath, cmakePath, cmakePreview, linkerPath, linkerPreview, ramLinkerPath, ramLinkerPreview, elfPath, elfReport;
    private Text preCommands, postCommands, flashStart, flashEnd, ramStart, ramEnd;
    private Label flashValidation, sramValidation;
    private Button flashSave, sramSave;
    private Combo flashSelector, ramSelector, flashEraseSize;
    private Spinner reservedFlashUnits;
    private Label flashReservationInfo;
    private java.util.List<Path> flashScripts = new ArrayList<>(), ramScripts = new ArrayList<>();
    private Button bin, hex, size;
    private final Map<String, Text> targets = new LinkedHashMap<>();
    private final Map<String, String> loadedTargetArgs = new LinkedHashMap<>();
    private boolean loadedBin, loadedHex, loadedSize;
    private String loadedPre = "", loadedPost = "";
    private boolean loading;
    private boolean loadingLinker;

    ConfiguratorDialog(Shell parent, Path project, Path workspaceCmake) {
        super(parent); this.project = project; this.workspaceCmake = workspaceCmake;
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
    }
    @Override protected void configureShell(Shell shell) { super.configureShell(shell); shell.setText("KTYLabs - STM32 CMake Project Configurator"); }
    @Override protected org.eclipse.swt.graphics.Point getInitialSize() { return new org.eclipse.swt.graphics.Point(1150, 780); }
    @Override protected Control createDialogArea(Composite parent) {
        Composite root = (Composite) super.createDialogArea(parent);
        root.setLayout(new GridLayout(1, false));
        Composite row = new Composite(root, SWT.NONE); row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        row.setLayout(new GridLayout(3, false));
        new Label(row, SWT.NONE).setText("STM32 project:");
        projectPath = new Text(row, SWT.BORDER); projectPath.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button choose = new Button(row, SWT.PUSH); choose.setText("Browse...");
        choose.addListener(SWT.Selection, e -> {
            DirectoryDialog dialog = new DirectoryDialog(getShell()); dialog.setText("Select STM32 project folder");
            String path = dialog.open(); if (path != null) { project = Path.of(path); workspaceCmake = null; projectPath.setText(path); loadProject(); }
        });
        TabFolder tabs = new TabFolder(root, SWT.NONE); tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        createCmakeTab(tabs); createLinkerTab(tabs); createRamLinkerTab(tabs); createElfTab(tabs);
        if (project != null) { projectPath.setText(project.toString()); loadProject(); }
        return root;
    }
    private Composite tab(TabFolder tabs, String name) {
        TabItem item = new TabItem(tabs, SWT.NONE); item.setText(name);
        Composite content = new Composite(tabs, SWT.NONE); content.setLayout(new GridLayout(1, false)); item.setControl(content); return content;
    }
    private void createCmakeTab(TabFolder tabs) {
        Composite page = tab(tabs, "CMake Configurator");
        cmakePath = pathBar(page, "CMakeLists.txt", () -> {
            FileDialog d = new FileDialog(getShell(), SWT.OPEN); d.setFilterExtensions(new String[]{"CMakeLists.txt", "*.txt", "*.*"});
            String s = d.open(); if (s != null) { cmake = Path.of(s); loadCmake(); }
        });
        SashForm split = new SashForm(page, SWT.HORIZONTAL); split.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        ScrolledComposite scroll = new ScrolledComposite(split, SWT.V_SCROLL | SWT.BORDER);
        scroll.setExpandHorizontal(true); scroll.setExpandVertical(true);
        Composite left = new Composite(scroll, SWT.NONE); left.setLayout(new GridLayout(1, false));
        bin = checkbox(left, "Generate BIN firmware image"); hex = checkbox(left, "Generate HEX firmware image");
        size = checkbox(left, "Print firmware size after build");
        ModifyListener changed = e -> updateCmakePreview();
        for (String name : ProjectFiles.TARGETS) {
            Group group = new Group(left, SWT.NONE); group.setText(name + " (one argument per line)");
            group.setLayout(new GridLayout(1, false)); group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            Text t = new Text(group, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
            GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false); gd.heightHint = 66; t.setLayoutData(gd);
            t.addModifyListener(changed); targets.put(name, t);
        }
        preCommands = multiGroup(left, "Pre-link commands (one command per line; runs before linking)", 65);
        postCommands = multiGroup(left, "Post-build commands (one command per line)", 65);
        preCommands.addModifyListener(changed); postCommands.addModifyListener(changed);
        bin.addListener(SWT.Selection, e -> updateCmakePreview());
        hex.addListener(SWT.Selection, e -> updateCmakePreview());
        size.addListener(SWT.Selection, e -> updateCmakePreview());
        scroll.setContent(left);
        scroll.setMinSize(left.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        scroll.addListener(SWT.Resize, e -> {
            int width = Math.max(200, scroll.getClientArea().width);
            left.setSize(width, left.computeSize(width, SWT.DEFAULT, true).y);
            scroll.setMinSize(200, left.getSize().y);
        });
        Composite right = new Composite(split, SWT.NONE); right.setLayout(new GridLayout(1, false));
        new Label(right, SWT.NONE).setText("CMakeLists.txt preview (editable)");
        cmakePreview = new Text(right, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        cmakePreview.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        Button save = new Button(right, SWT.PUSH); save.setText("Apply CMake Changes + Backup");
        save.addListener(SWT.Selection, e -> saveCmake()); split.setWeights(new int[]{45, 55});
    }
    private void createLinkerTab(TabFolder tabs) {
        Composite page = tab(tabs, "Flash Linker Script");
        flashSelector = linkerSelector(page, false);
        linkerPath = pathBar(page, "Linker .ld", () -> {
            FileDialog d = new FileDialog(getShell(), SWT.OPEN); d.setFilterExtensions(new String[]{"*.ld", "*.*"});
            String s = d.open(); if (s != null) { linker = Path.of(s); loadLinker(); }
        });
        Composite settings = new Composite(page, SWT.NONE); settings.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        settings.setLayout(new GridLayout(5, false));
        new Label(settings, SWT.NONE).setText("FLASH start:"); flashStart = new Text(settings, SWT.BORDER); flashStart.setLayoutData(new GridData(180, SWT.DEFAULT));
        new Label(settings, SWT.NONE).setText("FLASH end (inclusive):"); flashEnd = new Text(settings, SWT.BORDER); flashEnd.setLayoutData(new GridData(180, SWT.DEFAULT));
        Button resetFlash = new Button(settings, SWT.PUSH); resetFlash.setText("Reset");
        resetFlash.setToolTipText("Restore the FLASH start/end from the loaded linker script and clear reserved erase units.");
        resetFlash.addListener(SWT.Selection, e -> resetFlashSettings());
        flashStart.addModifyListener(e -> updateFlashPreview());
        flashEnd.addModifyListener(e -> updateFlashPreview());
        Composite eraseRow = new Composite(page, SWT.NONE);
        eraseRow.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        eraseRow.setLayout(new GridLayout(4, false));
        new Label(eraseRow, SWT.NONE).setText("FLASH erase unit size:");
        flashEraseSize = new Combo(eraseRow, SWT.DROP_DOWN | SWT.READ_ONLY);
        flashEraseSize.setItems(new String[]{"Select MCU erase size", "512 B", "1 KB", "2 KB", "4 KB", "8 KB", "16 KB", "32 KB", "64 KB", "128 KB", "256 KB"});
        flashEraseSize.select(3); // Default 2 KB erase unit; confirm MCU geometry before programming.
        flashEraseSize.setToolTipText("Select the MCU's actual erase page/sector size from its reference manual. For variable-sector devices, verify the boundaries manually.");
        new Label(eraseRow, SWT.NONE).setText("Reserve erase units at FLASH end:");
        reservedFlashUnits = new Spinner(eraseRow, SWT.BORDER);
        reservedFlashUnits.setMinimum(0); reservedFlashUnits.setMaximum(4096);
        reservedFlashUnits.setSelection(0);
        flashEraseSize.addListener(SWT.Selection, e -> { updateReservedFlashEnd(); updateFlashPreview(); });
        reservedFlashUnits.addModifyListener(e -> { updateReservedFlashEnd(); updateFlashPreview(); });
        flashReservationInfo = new Label(page, SWT.WRAP);
        flashReservationInfo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        flashReservationInfo.setText("Choose an erase size to validate boundaries and optionally reserve complete pages for persistent data.");
        flashValidation = new Label(page, SWT.WRAP);
        flashValidation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        flashValidation.setText("Enter FLASH addresses to update the preview automatically.");
        linkerPreview = new Text(page, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        linkerPreview.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        flashSave = new Button(page, SWT.PUSH); flashSave.setText("Apply Linker Changes + Backup");
        flashSave.addListener(SWT.Selection, e -> {
            if (!validateFlash()) return;
            try { ProjectFiles.saveWithBackup(linker, linkerPreview.getText()); originalLinker = linkerPreview.getText(); info("Linker script saved. Backup: " + linker + ".bak"); }
            catch (Exception ex) { error(ex); }
        });
    }
    private Combo linkerSelector(Composite page, boolean ram) {
        Composite row = new Composite(page, SWT.NONE);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        row.setLayout(new GridLayout(2, false));
        new Label(row, SWT.NONE).setText(ram ? "SRAM linker scripts:" : "FLASH linker scripts:");
        Combo combo = new Combo(row, SWT.DROP_DOWN | SWT.READ_ONLY);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        combo.addListener(SWT.Selection, e -> {
            int i = combo.getSelectionIndex();
            java.util.List<Path> scripts = ram ? ramScripts : flashScripts;
            if (i < 0 || i >= scripts.size()) return;
            if (ram) { ramLinker = scripts.get(i); loadRamLinker(); }
            else { linker = scripts.get(i); loadLinker(); }
        });
        return combo;
    }
    private void updateLinkerSelectors() {
        flashScripts = ProjectFiles.locateLinkers(cmake != null ? cmake.getParent() : project, false);
        ramScripts = ProjectFiles.locateLinkers(cmake != null ? cmake.getParent() : project, true);
        populateLinkerSelector(flashSelector, flashScripts);
        populateLinkerSelector(ramSelector, ramScripts);
        linker = flashScripts.isEmpty() ? null : flashScripts.get(0);
        ramLinker = ramScripts.isEmpty() ? null : ramScripts.get(0);
        if (!flashScripts.isEmpty()) flashSelector.select(0);
        if (!ramScripts.isEmpty()) ramSelector.select(0);
    }
    private void populateLinkerSelector(Combo selector, java.util.List<Path> scripts) {
        selector.removeAll();
        for (Path script : scripts) {
            selector.add(project != null && script.startsWith(project) ? project.relativize(script).toString() : script.toString());
        }
        selector.setEnabled(!scripts.isEmpty());
    }
    private void createRamLinkerTab(TabFolder tabs) {
        Composite page = tab(tabs, "SRAM Linker Script");
        Label notice = new Label(page, SWT.WRAP);
        notice.setText("Note: Some STM32 projects include SRAM linker scripts for optional SRAM execution. These scripts may not apply to your current build configuration.");
        notice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        ramSelector = linkerSelector(page, true);
        ramLinkerPath = pathBar(page, "SRAM linker .ld", () -> {
            FileDialog d = new FileDialog(getShell(), SWT.OPEN);
            d.setFilterExtensions(new String[]{"*.ld", "*.*"});
            String selected = d.open();
            if (selected != null) { ramLinker = Path.of(selected); loadRamLinker(); }
        });
        Composite settings = new Composite(page, SWT.NONE);
        settings.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        settings.setLayout(new GridLayout(4, false));
        new Label(settings, SWT.NONE).setText("SRAM origin:");
        ramStart = new Text(settings, SWT.BORDER); ramStart.setLayoutData(new GridData(180, SWT.DEFAULT));
        new Label(settings, SWT.NONE).setText("SRAM end (inclusive):");
        ramEnd = new Text(settings, SWT.BORDER); ramEnd.setLayoutData(new GridData(180, SWT.DEFAULT));
        ramStart.addModifyListener(e -> updateSramPreview());
        ramEnd.addModifyListener(e -> updateSramPreview());
        sramValidation = new Label(page, SWT.WRAP);
        sramValidation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        sramValidation.setText("Enter SRAM addresses to update the preview automatically.");
        ramLinkerPreview = new Text(page, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        ramLinkerPreview.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        sramSave = new Button(page, SWT.PUSH); sramSave.setText("Apply SRAM Linker Changes + Backup");
        sramSave.addListener(SWT.Selection, e -> {
            if (!validateSram()) return;
            try {
                ProjectFiles.saveWithBackup(ramLinker, ramLinkerPreview.getText());
                originalRamLinker = ramLinkerPreview.getText();
                info("SRAM linker script saved. Backup: " + ramLinker + ".bak");
            } catch (Exception ex) { error(ex); }
        });
        Label status = new Label(page, SWT.WRAP);
        status.setText("SRAM linker scripts are optional and may not pertain to the selected project's active build. Confirm the build configuration before editing.");
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
    private void loadRamLinker() {
        if (ramLinker == null) {
            loadingLinker = true;
            ramLinkerPath.setText("No SRAM linker script found - optional for most projects");
            ramLinkerPreview.setText(""); ramStart.setText(""); ramEnd.setText("");
            loadingLinker = false; updateSramPreview();
            return;
        }
        try {
            loadingLinker = true;
            originalRamLinker = ProjectFiles.read(ramLinker);
            ramLinkerPath.setText(ramLinker.toString());
            ramLinkerPreview.setText(originalRamLinker);
            long[] region = ProjectFiles.ramRegion(originalRamLinker);
            ramStart.setText(String.format("0x%08X", region[0]));
            ramEnd.setText(String.format("0x%08X", region[1]));
        } catch (Exception ex) { error(ex); }
        finally { loadingLinker = false; updateSramPreview(); }
    }
    private void createElfTab(TabFolder tabs) {
        Composite page = tab(tabs, "Verify ELF");
        new Label(page, SWT.WRAP).setText("Select a FLASH linker script first. This initial plugin checks the ELF's ARM vector table address against the configured FLASH start. It is not a full ELF section/symbol verifier.");
        elfPath = pathBar(page, "Built ELF", () -> {
            FileDialog d = new FileDialog(getShell(), SWT.OPEN); d.setFilterExtensions(new String[]{"*.elf", "*.*"});
            String s = d.open(); if (s != null) { elf = Path.of(s); elfPath.setText(s); }
        });
        Button verify = new Button(page, SWT.PUSH); verify.setText("Verify ELF against linker FLASH start");
        verify.addListener(SWT.Selection, e -> {
            try {
                if (linker == null || !Files.isRegularFile(linker)) throw new IOException("Select a linker script in the Flash Linker Script tab first");
                if (elf == null || !Files.isRegularFile(elf)) throw new IOException("Select a built ELF first");
                long[] flash = ProjectFiles.flashRegion(ProjectFiles.read(linker));
                elfReport.setText(ElfVerifier.verify(elf, flash[0], flash[1]));
            } catch (Exception ex) { error(ex); }
        });
        elfReport = new Text(page, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        elfReport.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }
    private Text pathBar(Composite parent, String label, Runnable browse) {
        Composite row = new Composite(parent, SWT.NONE); row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        row.setLayout(new GridLayout(3, false)); new Label(row, SWT.NONE).setText(label + ":");
        Text field = new Text(row, SWT.BORDER | SWT.READ_ONLY); field.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button button = new Button(row, SWT.PUSH); button.setText("Browse..."); button.addListener(SWT.Selection, e -> browse.run());
        return field;
    }
    private Button checkbox(Composite parent, String label) { Button b = new Button(parent, SWT.CHECK); b.setText(label); return b; }
    private Text multiGroup(Composite parent, String label, int height) {
        Group g = new Group(parent, SWT.NONE); g.setText(label); g.setLayout(new GridLayout(1, false));
        g.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Text t = new Text(g, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false); gd.heightHint = height; t.setLayoutData(gd); return t;
    }
    private void loadProject() {
        cmake = workspaceCmake != null && Files.isRegularFile(workspaceCmake)
                ? workspaceCmake : ProjectFiles.locate(project, "CMakeLists.txt");
        // Use the actual CMake folder for other artifacts when the IDE project
        // is a wrapper project or the CMake folder is an Eclipse linked resource.
        Path cmakeRoot = cmake != null ? cmake.getParent() : project;
        if (cmake != null && project != null && !cmake.getParent().equals(project)) {
            projectPath.setText(cmakeRoot.toString());
        }
        elf = ProjectFiles.locateElf(cmakeRoot);
        updateLinkerSelectors(); loadCmake(); loadLinker(); loadRamLinker(); elfPath.setText(elf == null ? "Not found - Browse to select" : elf.toString());
    }
    private void loadCmake() {
        if (cmake == null) { cmakePath.setText("Not found - Browse to select"); return; }
        try {
            loading = true; originalCmake = ProjectFiles.read(cmake); cmakePath.setText(cmake.toString());
            // Existing user CMake commands are preserved. We do not replace or delete them.
            String generated = originalCmake.substring(Math.max(0, originalCmake.indexOf(ProjectFiles.BEGIN)));
            boolean managed = originalCmake.contains(ProjectFiles.BEGIN);
            bin.setSelection(managed && generated.contains("-O binary"));
            hex.setSelection(managed && generated.contains("-O ihex"));
            size.setSelection(managed && generated.contains("${CMAKE_SIZE}"));
            loadedTargetArgs.clear();
            for (Map.Entry<String, Text> entry : targets.entrySet()) {
                String value = ProjectFiles.readTargetArguments(originalCmake, entry.getKey());
                loadedTargetArgs.put(entry.getKey(), value);
                entry.getValue().setText(value);
            }
            loadedBin = bin.getSelection(); loadedHex = hex.getSelection(); loadedSize = size.getSelection();
            preCommands.setText(""); postCommands.setText("");
            loadedPre = preCommands.getText(); loadedPost = postCommands.getText();
            cmakePreview.setText(originalCmake);
        } catch (Exception ex) { error(ex); } finally { loading = false; }
    }
    private void loadLinker() {
        if (linker == null) {
            loadingLinker = true;
            linkerPath.setText("No FLASH linker script found - Browse to select"); linkerPreview.setText(""); flashStart.setText(""); flashEnd.setText("");
            loadingLinker = false; updateFlashPreview(); return;
        }
        try {
            loadingLinker = true;
            originalLinker = ProjectFiles.read(linker); linkerPath.setText(linker.toString()); linkerPreview.setText(originalLinker);
            if (reservedFlashUnits != null) reservedFlashUnits.setSelection(0);
            if (flashEraseSize != null) flashEraseSize.select(3);
            long[] flash = ProjectFiles.flashRegion(originalLinker);
            flashStart.setText(String.format("0x%08X", flash[0])); flashEnd.setText(String.format("0x%08X", flash[1]));
        } catch (Exception ex) { error(ex); }
        finally { loadingLinker = false; updateFlashPreview(); }
    }
    private void resetFlashSettings() {
        if (linker == null || originalLinker.isEmpty()) return;
        try {
            long[] original = ProjectFiles.flashRegion(originalLinker);
            loadingLinker = true;
            flashStart.setText(String.format("0x%08X", original[0]));
            flashEnd.setText(String.format("0x%08X", original[1]));
            reservedFlashUnits.setSelection(0);
            flashEraseSize.select(3); // 2 KB default
        } catch (Exception ex) { error(ex); }
        finally { loadingLinker = false; updateFlashPreview(); }
    }
    // Validation is inline: incomplete addresses while typing never trigger modal dialogs.
    // Invalid input leaves the last valid preview visible and disables saving.
    private long selectedFlashEraseSize() {
        if (flashEraseSize == null || flashEraseSize.isDisposed()) return 0;
        int i = flashEraseSize.getSelectionIndex();
        return i <= 0 ? 0 : (i == 1 ? 512L : 1024L << (i - 2));
    }
    private void updateReservedFlashEnd() {
        if (loadingLinker || reservedFlashUnits == null || reservedFlashUnits.isDisposed()
                || originalLinker.isEmpty() || flashEnd == null || flashEnd.isDisposed()) return;
        long unit = selectedFlashEraseSize();
        int count = reservedFlashUnits.getSelection();
        if (count == 0) {
            try { long[] original = ProjectFiles.flashRegion(originalLinker);
                flashEnd.setText(String.format("0x%08X", original[1]));
            } catch (Exception ignored) { }
            return;
        }
        if (unit == 0) return;
        try {
            long[] original = ProjectFiles.flashRegion(originalLinker);
            long newEnd = Math.subtractExact(original[1], Math.multiplyExact(unit, count));
            if (newEnd < original[0]) return; // Validation reports an oversized reservation.
            flashEnd.setText(String.format("0x%08X", newEnd));
        } catch (Exception ignored) { /* Inline validation handles malformed or overflowing values. */ }
    }
    private void updateFlashPreview() {
        if (loadingLinker || flashValidation == null || flashValidation.isDisposed()) return;
        if (linker == null || originalLinker.isEmpty()) {
            setValidation(flashValidation, flashSave, "No FLASH linker script selected.", false); return;
        }
        try {
            long start = ProjectFiles.parseHex(flashStart.getText());
            long end = ProjectFiles.parseHex(flashEnd.getText());
            long unit = selectedFlashEraseSize();
            if (unit == 0 || start % unit != 0 || (end + 1) % unit != 0) {
                if (start % 2048 == 0 && (end + 1) % 2048 == 0) {
                    flashEraseSize.select(3); // Automatically choose 2 KB when it fits the selected boundaries.
                    unit = 2048;
                }
            }
            if (unit == 0) throw new IllegalArgumentException("Select the MCU's erase page/sector size before saving.");
            if (start % unit != 0) throw new IllegalArgumentException(
                String.format("Start 0x%08X is not aligned to a %d-byte erase boundary.", start, unit));
            if ((end + 1) % unit != 0) throw new IllegalArgumentException(
                String.format("End + 1 (0x%08X) is not aligned to a %d-byte erase boundary.", end + 1, unit));
            long[] original = ProjectFiles.flashRegion(originalLinker);
            int count = reservedFlashUnits.getSelection();
            long bytes = Math.multiplyExact(unit, count);
            if (count > 0) {
                if (bytes >= original[1] - original[0] + 1)
                    throw new IllegalArgumentException("Reserved user data occupies the entire FLASH region or more.");
                long reservedStart = original[1] - bytes + 1;
                if (reservedStart % unit != 0)
                    throw new IllegalArgumentException("Reserved area does not start on an erase boundary.");
                if (end >= reservedStart)
                    throw new IllegalArgumentException("Application overlaps reserved data region. Set FLASH end at or before 0x" + Long.toHexString(reservedStart - 1).toUpperCase());
                flashReservationInfo.setText(String.format("Reserved data: 0x%08X - 0x%08X (%d bytes, %d erase units). Full-chip erase will still erase this data.",
                    reservedStart, original[1], bytes, count));
            } else {
                flashReservationInfo.setText("No data pages reserved. Verify actual MCU erase geometry; variable-sized sectors require per-sector checks.");
            }
            String preview = ProjectFiles.updateFlash(originalLinker, start, end);
            linkerPreview.setText(preview);
            setValidation(flashValidation, flashSave,
                String.format("Valid FLASH range: 0x%08X - 0x%08X (%d bytes); erase unit %d bytes", start, end, end - start + 1, unit), true);
        } catch (Exception ex) {
            if (flashReservationInfo != null && !flashReservationInfo.isDisposed())
                flashReservationInfo.setText("Reservation not applied. Check erase geometry and FLASH addresses.");
            setValidation(flashValidation, flashSave, "FLASH address error: " + ex.getMessage(), false);
        }
    }
    private void updateSramPreview() {
        if (loadingLinker || sramValidation == null || sramValidation.isDisposed()) return;
        if (ramLinker == null || originalRamLinker.isEmpty()) {
            setValidation(sramValidation, sramSave, "No SRAM linker script selected (optional).", false); return;
        }
        try {
            long start = ProjectFiles.parseHex(ramStart.getText());
            long end = ProjectFiles.parseHex(ramEnd.getText());
            String preview = ProjectFiles.updateRam(originalRamLinker, start, end);
            ramLinkerPreview.setText(preview);
            setValidation(sramValidation, sramSave,
                String.format("Valid SRAM range: 0x%08X - 0x%08X (%d bytes)", start, end, end - start + 1), true);
        } catch (Exception ex) {
            setValidation(sramValidation, sramSave, "SRAM address error: " + ex.getMessage(), false);
        }
    }
    private void setValidation(Label label, Button save, String message, boolean valid) {
        label.setText(message);
        label.setForeground(label.getDisplay().getSystemColor(valid ? SWT.COLOR_DARK_GREEN : SWT.COLOR_DARK_RED));
        if (save != null && !save.isDisposed()) save.setEnabled(valid);
        label.getParent().layout(true, true);
    }
    private boolean validateFlash() {
        updateFlashPreview();
        return flashSave.getEnabled();
    }
    private boolean validateSram() {
        updateSramPreview();
        return sramSave.getEnabled();
    }
    private void updateCmakePreview() {
        if (loading || cmakePreview == null || cmakePreview.isDisposed() || originalCmake.isEmpty()) return;
        String updated = originalCmake;
        // Only rewrite a target_* command when its textbox was actually changed.
        for (Map.Entry<String, Text> entry : targets.entrySet()) {
            String args = entry.getValue().getText();
            if (!args.equals(loadedTargetArgs.get(entry.getKey()))) {
                updated = ProjectFiles.replaceTargetArguments(updated, entry.getKey(), args);
            }
        }
        if (bin.getSelection() != loadedBin || hex.getSelection() != loadedHex ||
                size.getSelection() != loadedSize || !preCommands.getText().equals(loadedPre) ||
                !postCommands.getText().equals(loadedPost)) {
            updated = ProjectFiles.updateGeneratedOnly(updated, bin.getSelection(), hex.getSelection(),
                    size.getSelection(), preCommands.getText(), postCommands.getText());
        }
        cmakePreview.setText(updated);
    }
    private void saveCmake() {
        try { ProjectFiles.saveWithBackup(cmake, cmakePreview.getText()); originalCmake = cmakePreview.getText(); loadCmake(); info("CMakeLists.txt saved. Backup: " + cmake + ".bak"); }
        catch (Exception ex) { error(ex); }
    }
    private void info(String message) { MessageDialog.openInformation(getShell(), "STM32 CMake Configurator", message); }
    private void error(Exception e) { MessageDialog.openError(getShell(), "STM32 CMake Configurator", e.getMessage() == null ? e.toString() : e.getMessage()); }
    @Override protected void createButtonsForButtonBar(Composite parent) { createButton(parent, CANCEL, "Close", false); }
}
