package com.bhel.hrm.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.model.RegistrationResult;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Rules the whole contract must keep, whatever fields are added later. */
class ContractRulesTest {

    private static final Path MODEL_SOURCES = Path.of("src/main/java/com/bhel/hrm/common/model");

    /** Every record in the model package, found from the source files so a new class is checked too. */
    private static List<Class<?>> dataClasses() throws IOException, ClassNotFoundException {
        List<Class<?>> records = new ArrayList<>();
        try (Stream<Path> files = Files.list(MODEL_SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                Class<?> type = Class.forName("com.bhel.hrm.common.model." + name);
                if (type.isRecord()) {
                    records.add(type);
                }
            }
        }
        return records;
    }

    @Test
    void theModelPackageStillHoldsTheAgreedRecords() throws Exception {
        assertEquals(8, dataClasses().size());
    }

    @Test
    void noDataClassCarriesAPasswordOrHashExceptTheOneTimeTemporaryPassword() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : dataClasses()) {
            for (RecordComponent component : type.getRecordComponents()) {
                String name = component.getName().toLowerCase();
                boolean holdsText = component.getType() == String.class || component.getType() == char[].class
                        || component.getType() == byte[].class;
                boolean secret = holdsText && (name.contains("password") || name.contains("hash"));
                boolean allowed = type == RegistrationResult.class && component.getName().equals("temporaryPassword");
                if (secret && !allowed) {
                    offenders.add(type.getSimpleName() + "." + component.getName());
                }
            }
        }

        assertEquals(List.of(), offenders);
    }

    @Test
    void commonPackageImportsOnlyJavaPackagesAndItself() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/bhel/hrm/common"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (line.startsWith("import ") && !line.startsWith("import java.")
                            && !line.startsWith("import com.bhel.hrm.common.")) {
                        offenders.add(file.getFileName() + ": " + line);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(), "imports other than java.* or the common package: " + offenders);
    }
}
