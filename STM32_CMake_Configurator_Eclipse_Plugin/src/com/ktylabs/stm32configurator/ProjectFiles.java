package com.ktylabs.stm32configurator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

final class ProjectFiles {
    private ProjectFiles() {}
    static final String[] TARGETS = {"target_sources", "target_include_directories", "target_compile_definitions",
        "target_compile_options", "target_link_options", "target_link_directories", "target_link_libraries"};
    static final String BEGIN = "# >>> KTYLabs STM32 Configurator generated >>>";
    static final String END = "# <<< KTYLabs STM32 Configurator generated <<<";
    static final String TARGET = "${CMAKE_PROJECT_NAME}";

    static Path locate(Path root, String filename) {
        if (root == null || !Files.isDirectory(root)) return null;
        Path direct = root.resolve(filename);
        if (Files.isRegularFile(direct)) return direct;
        try (Stream<Path> paths = Files.walk(root, 5)) {
            return paths.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals(filename))
                .findFirst().orElse(null);
        } catch (IOException e) { return null; }
    }
    private static boolean containsDirectory(Path path, String name) {
        Path parent = path.getParent();
        if (parent == null) return false;
        for (Path part : parent) {
            if (part.toString().equalsIgnoreCase(name)) return true;
        }
        return false;
    }
    static List<Path> locateLinkers(Path root, boolean ram) {
        List<Path> found = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) return found;
        try (Stream<Path> paths = Files.walk(root, 10)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> !containsDirectory(path, "templates"))
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ld"))
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (ram) return name.contains("ram") && !name.contains("flash");
                    return name.contains("flash") && !name.contains("ram");
                }).forEach(found::add);
        } catch (IOException ignored) { }
        found.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
        return found;
    }
    static Path locateLinker(Path root) {
        List<Path> flash = locateLinkers(root, false);
        return flash.isEmpty() ? null : flash.get(0);
    }
    static Path locateElf(Path root) {
        if (root == null || !Files.isDirectory(root)) return null;
        try (Stream<Path> paths = Files.walk(root, 8)) {
            return paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".elf"))
                .findFirst().orElse(null);
        } catch (IOException e) { return null; }
    }
    // Read existing target_* blocks without losing argument order or the CMake scope.
    // The target name can be ${CMAKE_PROJECT_NAME} or the actual project target.
    private static Pattern targetPattern(String command) {
        return Pattern.compile("(?m)(^\\s*" + Pattern.quote(command)
            + "\\s*\\(\\s*([^\\s()]+)\\s+(PRIVATE|PUBLIC|INTERFACE)\\b)([^)]*)(\\))");
    }
    static String readTargetArguments(String source, String command) {
        Matcher matcher = targetPattern(command).matcher(source);
        if (!matcher.find()) return "";
        StringBuilder result = new StringBuilder();
        for (String line : matcher.group(4).split("\\R")) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) continue;
            if (result.length() > 0) result.append('\n');
            result.append(value);
        }
        return result.toString();
    }
    static String replaceTargetArguments(String source, String command, String args) {
        Matcher matcher = targetPattern(command).matcher(source);
        if (!matcher.find()) return source;
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        String oldBody = matcher.group(4);
        String indent = "  ";
        Matcher indentation = Pattern.compile("(?m)(?:^|\\R)([ \t]+)\\S").matcher(oldBody);
        if (indentation.find()) indent = indentation.group(1);
        StringBuilder body = new StringBuilder(newline);
        for (String line : args.split("\\R")) {
            if (!line.isBlank()) body.append(indent).append(line.trim()).append(newline);
        }
        // Only the selected command's argument body is replaced. Everything else,
        // including comments, whitespace and line endings, is kept byte-for-byte.
        return source.substring(0, matcher.start(4)) + body + source.substring(matcher.end(4));
    }

    static String updateGeneratedOnly(String source, boolean bin, boolean hex, boolean size,
            String pre, String post) {
        // generate() is used solely to build the managed block; never use its
        // normalized base as the output, since that reformats unrelated CMake.
        String generated = generate("", bin, hex, size, pre, post);
        int start = generated.indexOf(BEGIN);
        int end = generated.indexOf(END, start) + END.length();
        String block = generated.substring(start, end);
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        block = block.replace("\n", newline);
        int oldStart = source.indexOf(BEGIN);
        if (oldStart >= 0) {
            int oldEnd = source.indexOf(END, oldStart);
            if (oldEnd < 0) throw new IllegalArgumentException("Managed CMake block has no closing marker");
            oldEnd += END.length();
            return source.substring(0, oldStart) + block + source.substring(oldEnd);
        }
        if (!bin && !hex && !size && pre.isBlank() && post.isBlank()) return source;
        String separator = source.isEmpty() ? "" :
            (source.endsWith(newline + newline) ? "" : source.endsWith(newline) ? newline : newline + newline);
        return source + separator + block + newline;
    }
    static String read(Path p) throws IOException { return Files.readString(p, StandardCharsets.UTF_8); }
    static void saveWithBackup(Path path, String contents) throws IOException {
        if (path == null || !Files.isRegularFile(path)) throw new IOException("Select an existing file first");
        Path backup = path.resolveSibling(path.getFileName() + ".bak");
        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(path, contents, StandardCharsets.UTF_8);
    }
    static String stripGenerated(String source) {
        int a = source.indexOf(BEGIN);
        if (a < 0) return source;
        int b = source.indexOf(END, a);
        if (b < 0) return source;
        b += END.length();
        if (b < source.length() && source.charAt(b) == '\r') b++;
        if (b < source.length() && source.charAt(b) == '\n') b++;
        return source.substring(0, a) + source.substring(b);
    }
    static String generate(String original, boolean bin, boolean hex, boolean size,
            String pre, String post) {
        String base = stripGenerated(original).stripTrailing();
        StringBuilder b = new StringBuilder(base).append("\n\n").append(BEGIN).append('\n');
        if (bin || hex) {
            b.append("if(NOT CMAKE_OBJCOPY)\n  find_program(CMAKE_OBJCOPY NAMES arm-none-eabi-objcopy REQUIRED)\nendif()\n");
        }
        if (size) b.append("if(NOT CMAKE_SIZE)\n  find_program(CMAKE_SIZE NAMES arm-none-eabi-size REQUIRED)\nendif()\n");
        // Multiple POST_BUILD blocks on the same target are legal; keep custom commands together here.
        List<String> commands = new ArrayList<>();
        if (bin && !base.contains("-O binary")) commands.add("  COMMAND \"${CMAKE_OBJCOPY}\" -O binary \"$<TARGET_FILE:" + TARGET + ">\" \"$<TARGET_FILE_DIR:" + TARGET + ">/$<TARGET_FILE_BASE_NAME:" + TARGET + ">.bin\"");
        if (hex && !base.contains("-O ihex")) commands.add("  COMMAND \"${CMAKE_OBJCOPY}\" -O ihex \"$<TARGET_FILE:" + TARGET + ">\" \"$<TARGET_FILE_DIR:" + TARGET + ">/$<TARGET_FILE_BASE_NAME:" + TARGET + ">.hex\"");
        if (size) commands.add("  COMMAND \"${CMAKE_SIZE}\" \"$<TARGET_FILE:" + TARGET + ">\"");
        for (String line : post.split("\\R")) if (!line.isBlank()) commands.add("  COMMAND " + line.trim());
        if (!commands.isEmpty()) {
            b.append("add_custom_command(TARGET ").append(TARGET).append(" POST_BUILD\n");
            for (String command : commands) b.append(command).append('\n');
            b.append("  VERBATIM\n)\n");
        }
        // PRE_LINK is before linking, NOT before compiling.
        List<String> preCommands = new ArrayList<>();
        for (String line : pre.split("\\R")) if (!line.isBlank()) preCommands.add("  COMMAND " + line.trim());
        if (!preCommands.isEmpty()) {
            b.append("add_custom_command(TARGET ").append(TARGET).append(" PRE_LINK\n");
            for (String command : preCommands) b.append(command).append('\n');
            b.append("  VERBATIM\n)\n");
        }
        b.append(END).append('\n');
        return b.toString();
    }
    static String updateFlash(String source, long start, long end) {
        if (start < 0 || end < start || end > 0xFFFFFFFFL) throw new IllegalArgumentException("Invalid FLASH start/end");
        Pattern p = Pattern.compile("(?im)(\\b(?:ROM|FLASH)\\s*\\([^)]*\\)\\s*:\\s*(?:org|ORIGIN)\\s*=\\s*)(0x[0-9a-f]+)(\\s*,\\s*(?:len|LENGTH)\\s*=\\s*)(0x[0-9a-f]+|\\d+[KkMm]?)");
        Matcher m = p.matcher(source);
        if (!m.find()) throw new IllegalArgumentException("No ROM/FLASH MEMORY region found");
        String replacement = m.group(1) + String.format("0x%08X", start) + m.group(3) + String.format("0x%X", end - start + 1);
        return m.replaceFirst(Matcher.quoteReplacement(replacement));
    }
    static long[] ramRegion(String source) {
        Matcher m = Pattern.compile("(?im)\\b(?:RAM|SRAM|RAM_D1|RAM_D2)\\s*\\([^)]*\\)\\s*:\\s*(?:org|ORIGIN)\\s*=\\s*(0x[0-9a-f]+)\\s*,\\s*(?:len|LENGTH)\\s*=\\s*(0x[0-9a-f]+|\\d+)").matcher(source);
        if (!m.find()) throw new IllegalArgumentException("RAM MEMORY region not found");
        long start = Long.decode(m.group(1)); long length = Long.decode(m.group(2));
        return new long[]{start, start + length - 1};
    }
    static String updateRam(String source, long start, long end) {
        if (start < 0 || end < start || end > 0xFFFFFFFFL) throw new IllegalArgumentException("Invalid RAM start/end");
        Pattern p = Pattern.compile("(?im)(\\b(?:RAM|SRAM|RAM_D1|RAM_D2)\\s*\\([^)]*\\)\\s*:\\s*(?:org|ORIGIN)\\s*=\\s*)(0x[0-9a-f]+)(\\s*,\\s*(?:len|LENGTH)\\s*=\\s*)(0x[0-9a-f]+|\\d+[KkMm]?)");
        Matcher m = p.matcher(source);
        if (!m.find()) throw new IllegalArgumentException("No RAM MEMORY region found");
        return m.replaceFirst(Matcher.quoteReplacement(m.group(1) + String.format("0x%08X", start) + m.group(3) + String.format("0x%X", end - start + 1)));
    }
    static long parseHex(String value) { return Long.decode(value.trim()); }
    static long[] flashRegion(String source) {
        Matcher m = Pattern.compile("(?im)\\b(?:ROM|FLASH)\\s*\\([^)]*\\)\\s*:\\s*(?:org|ORIGIN)\\s*=\\s*(0x[0-9a-f]+)\\s*,\\s*(?:len|LENGTH)\\s*=\\s*(0x[0-9a-f]+|\\d+)").matcher(source);
        if (!m.find()) throw new IllegalArgumentException("FLASH region not found");
        long start = Long.decode(m.group(1)); long length = Long.decode(m.group(2));
        return new long[]{start, start + length - 1};
    }
}
