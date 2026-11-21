package com.ktylabs.stm32configurator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;

final class ElfVerifier {
    private ElfVerifier() {}
    static String verify(Path path, long expectedStart, long expectedEnd) throws IOException {
        byte[] data = Files.readAllBytes(path);
        if (data.length < 52 || data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F')
            throw new IOException("Not a valid ELF file");
        boolean is64 = data[4] == 2;
        if (data[4] != 1 && data[4] != 2) throw new IOException("Unsupported ELF class");
        ByteOrder order = data[5] == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        ByteBuffer b = ByteBuffer.wrap(data).order(order);
        long phoff = is64 ? b.getLong(32) : Integer.toUnsignedLong(b.getInt(28));
        int phentsize = Short.toUnsignedInt(b.getShort(is64 ? 54 : 42));
        int phnum = Short.toUnsignedInt(b.getShort(is64 ? 56 : 44));
        long firstLoad = Long.MAX_VALUE;
        for (int i = 0; i < phnum; i++) {
            long pos = phoff + (long) i * phentsize;
            if (pos < 0 || pos + phentsize > data.length) throw new IOException("Invalid ELF program header bounds");
            int type = b.getInt((int) pos);
            if (type != 1) continue; // PT_LOAD
            long vaddr = is64 ? b.getLong((int) pos + 16) : Integer.toUnsignedLong(b.getInt((int) pos + 8));
            long paddr = is64 ? b.getLong((int) pos + 24) : Integer.toUnsignedLong(b.getInt((int) pos + 12));
            long filesz = is64 ? b.getLong((int) pos + 32) : Integer.toUnsignedLong(b.getInt((int) pos + 16));
            if (filesz > 0 && paddr >= 0x08000000L && paddr < 0x10000000L) firstLoad = Math.min(firstLoad, paddr);
            else if (filesz > 0 && vaddr >= 0x08000000L && vaddr < 0x10000000L) firstLoad = Math.min(firstLoad, vaddr);
        }
        if (firstLoad == Long.MAX_VALUE) return "No FLASH PT_LOAD segment found; cannot verify ELF FLASH origin.";
        return String.format("Linker FLASH start: 0x%08X%nLinker FLASH end:   0x%08X%nFirst ELF FLASH load: 0x%08X%n%n%s%n%nNote: This initial verifier compares the first FLASH load address, not every ELF section or symbol.",
            expectedStart, expectedEnd, firstLoad, firstLoad == expectedStart ? "PASS - ELF load address matches linker start" : "FAIL - ELF load address differs from linker start");
    }
}
