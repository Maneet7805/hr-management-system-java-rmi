package com.bhel.hrm.server;

import com.bhel.hrm.common.exception.HrmException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.server.db.DbSettings;
import com.bhel.hrm.server.db.JdbcDatabase;
import com.bhel.hrm.server.security.Pbkdf2PasswordHasher;
import com.bhel.hrm.server.service.HrSeeder;
import java.io.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Creates an HR account so there is someone who can log in and register employees.
 *
 * <p>Usage: {@code SeedHrMain [config-file] [first-name last-name ic-passport]}. Without the three
 * details it asks for them. The password is typed without echo; where there is no console (some IDE
 * terminals) it is read from the environment variable HRM_SEED_PASSWORD instead.
 * Needs HRM_DB_PASSWORD. Run it again to add another HR account.
 */
public final class SeedHrMain {

    private SeedHrMain() {
    }

    public static void main(String[] args) {
        try {
            boolean hasConfigFile = args.length == 1 || args.length == 4;
            Path configFile = Path.of(hasConfigFile ? args[0] : "config/server.properties");
            String[] details = Arrays.copyOfRange(args, hasConfigFile ? 1 : 0, args.length);
            Console console = System.console();

            String firstName = details.length == 3 ? details[0] : ask(console, "First name: ");
            String lastName = details.length == 3 ? details[1] : ask(console, "Last name: ");
            String icPassport = details.length == 3 ? details[2] : ask(console, "IC/passport number: ");
            String password = readPassword(console);

            DbSettings dbSettings = DbSettings.load(configFile, System.getenv());
            HrSeeder seeder = new HrSeeder(new JdbcDatabase(dbSettings), new Pbkdf2PasswordHasher());
            Employee hr = seeder.createHr(firstName, lastName, icPassport, password);
            System.out.println("HR account created for " + hr.firstName() + " " + hr.lastName()
                    + ". Log in with the IC/passport number " + hr.icPassport() + ".");
        } catch (HrmException | IOException | IllegalArgumentException e) {
            System.out.println("Cannot create the HR account: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String ask(Console console, String prompt) {
        if (console == null) {
            throw new IllegalArgumentException(
                    "No console to ask on. Pass the first name, last name and IC/passport number as arguments.");
        }
        return console.readLine(prompt);
    }

    private static String readPassword(Console console) {
        if (console == null) {
            String fromEnvironment = System.getenv("HRM_SEED_PASSWORD");
            if (fromEnvironment == null || fromEnvironment.isBlank()) {
                throw new IllegalArgumentException(
                        "No console to ask on. Set the environment variable HRM_SEED_PASSWORD first.");
            }
            return fromEnvironment;
        }
        char[] first = console.readPassword("Password: ");
        char[] second = console.readPassword("Password again: ");
        try {
            if (first == null || second == null || !Arrays.equals(first, second)) {
                throw new IllegalArgumentException("The two passwords are not the same.");
            }
            return new String(first);
        } finally {
            if (first != null) {
                Arrays.fill(first, ' ');
            }
            if (second != null) {
                Arrays.fill(second, ' ');
            }
        }
    }
}
