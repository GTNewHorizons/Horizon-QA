package com.gtnewhorizons.horizonqa.report;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class AtomicReportWriter {

    interface TempFileWriter {

        void write(Path tempFile) throws IOException;
    }

    interface MoveOperation {

        void move(Path source, Path target, CopyOption... options) throws IOException;
    }

    private AtomicReportWriter() {}

    static void write(File outputFile, TempFileWriter writer) throws IOException {
        write(outputFile.toPath(), writer, AtomicReportWriter::move);
    }

    static void write(Path target, TempFileWriter writer, MoveOperation mover) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = tempFileFor(absoluteTarget);
        try {
            Files.createFile(tempFile);
            writer.write(tempFile);
            moveAtomically(tempFile, absoluteTarget, mover);
        } catch (IOException e) {
            cleanupTempFile(tempFile, e);
            throw e;
        }
    }

    private static Path tempFileFor(Path target) throws IOException {
        Path parent = target.getParent();
        Path fileName = target.getFileName();
        if (parent == null || fileName == null) {
            throw new IOException("Missing report output file name: " + target);
        }
        return parent.resolve(fileName + "." + UUID.randomUUID() + ".tmp");
    }

    private static void moveAtomically(Path tempFile, Path target, MoveOperation mover) throws IOException {
        try {
            mover.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            mover.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void move(Path source, Path target, CopyOption... options) throws IOException {
        Files.move(source, target, options);
    }

    private static void cleanupTempFile(Path tempFile, IOException original) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException cleanupError) {
            original.addSuppressed(cleanupError);
        }
    }
}
