package modhero.data.timetable;

import static modhero.common.Constants.AcademicConstants;

import modhero.common.util.PrerequisiteUtil;
import modhero.exceptions.InvalidYearOrSemException;
import modhero.exceptions.ModHeroException;
import modhero.exceptions.ModuleAlreadyExistsException;
import modhero.exceptions.ModuleDeletionBlockedException;
import modhero.exceptions.ModuleNotFoundException;
import modhero.data.modules.Module;
import modhero.exceptions.ModuleAdditionBlockedException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a multi-year academic timetable, storing modules organized
 * by year and term.
 */
public class Timetable {
    public static final Logger logger = Logger.getLogger(Timetable.class.getName());

    private List<List<List<Module>>> timetable;

    /**
     * Creates a timetable structure for the specified number of years and terms.
     * Each year contains a list of terms, which in turn contain modules.
     *
     */
    public Timetable() {
        timetable = new ArrayList<>();

        // Initialize the timetable
        for (int year = 0; year < AcademicConstants.NUM_YEARS; year++) {
            List<List<Module>> yearSemesters = new ArrayList<>();
            for (int sem = 0; sem < AcademicConstants.NUM_TERMS; sem++) {
                yearSemesters.add(new ArrayList<>()); // each semester starts empty
            }
            timetable.add(yearSemesters);
        }

        logger.log(Level.FINE, () -> String.format("Timetable initialised for %d years and %d terms", AcademicConstants.NUM_YEARS, AcademicConstants.NUM_TERMS));
    }

    /**
     * Adds a module to the timetable, performing all business logic checks.
     *
     * @param module   The Module object to add.
     * @param year     The academic year (1-based).
     * @param semester The semester (1-based).
     */
    public void addModule(int year, int semester, Module module, List<String> exemptedModules) throws ModHeroException {
        // Bounds check
        if (year < 1 || year > AcademicConstants.NUM_YEARS ||
                semester < 1 || semester > AcademicConstants.NUM_TERMS) {
            throw new InvalidYearOrSemException(year, semester);
        }

        // Check addability
        checkModuleAddable(year, semester, module, exemptedModules);

        // Add to timetable
        addModuleDirect(year - 1, semester - 1, module);
    }

    private void checkModuleAddable(int year, int semester, Module moduleToAdd, List<String> exemptedModules) throws ModHeroException {
        if (getAllModules().stream().anyMatch(m -> m.getCode().equalsIgnoreCase(moduleToAdd.getCode()))) {
            throw new ModuleAlreadyExistsException(moduleToAdd.getCode());
        }
        if (PrerequisiteUtil.isExemptedModule(moduleToAdd.getCode(), exemptedModules)) {
            return;
        }

        List<Module> completedModules = getModulesTakenUpTo(year - 1, semester - 1);
        List<String> completedCodes = completedModules.stream().map(Module::getCode).toList();

        PrerequisiteUtil.validatePrerequisites(moduleToAdd.getCode(), moduleToAdd.getPrerequisites(), completedCodes, exemptedModules);
    }

    /**
     * Internal method to add a module to a specific year and term.
     * No checks are performed here.
     *
     * @param year   the year index (0-based)
     * @param term   the term index (0-based)
     * @param module the module to add
     */
    public void addModuleDirect(int year, int term, Module module) {
        timetable.get(year).get(term).add(module);
        logger.log(Level.FINEST, () -> String.format("Module %s added to year %d term %d", module.getCode(), year, term));
    }

    /**
     * Deletes a module from the timetable, checking prerequisites.
     *
     * @param moduleCode the code of the module to delete
     * @throws ModuleNotFoundException     if the module is not found in the timetable
     * @throws ModuleAdditionBlockedException if other modules depend on this module as a prerequisite
     */
    public void deleteModule(String moduleCode, List<String> exemptedModules) throws ModHeroException {
        // Find the module location
        int[] location = findModuleLocation(moduleCode);
        int year = location[0];
        int semester = location[1];

        // Check if deletion is safe
        checkModuleDeletable(year, semester, moduleCode, exemptedModules);

        // Delete the module
        deleteModuleDirect(year, semester, moduleCode);
    }

    /**
     * Checks if a module can be safely deleted without breaking prerequisites.
     *
     * @param year       the year index (0-based)
     * @param semester   the semester index (0-based)
     * @param moduleCode the code of the module to delete
     * @throws ModuleDeletionBlockedException if other modules depend on this module
     */
    private void checkModuleDeletable(int year, int semester, String moduleCode, List<String> exemptedModules) throws ModuleDeletionBlockedException {
        // Get all modules taken after this module
        List<Module> futureModules = getModulesTakenAfter(year, semester);

        // Simulate what completed modules would be if we delete this module
        // Include the current semester here
        List<Module> completedModules = getModulesTakenUpTo(year, semester + 1);
        completedModules.removeIf(m -> m.getCode().equals(moduleCode));
        List<String> completedCodes = completedModules.stream().map(Module::getCode).toList();

        PrerequisiteUtil.validateFutureDependencies(moduleCode, futureModules, completedCodes, exemptedModules);
    }

    /**
     * Internal method to delete a module from a specific year and semester.
     * No checks are performed here.
     *
     * @param year       the year index (0-based)
     * @param semester   the semester index (0-based)
     * @param moduleCode the code of the module to delete
     */
    private void deleteModuleDirect(int year, int semester, String moduleCode) {
        List<Module> modules = timetable.get(year).get(semester);
        modules.removeIf(m -> m.getCode().equals(moduleCode));
        logger.log(Level.FINEST, () -> String.format("Module %s deleted from year %d semester %d",
                moduleCode, year, semester));
    }

    /**
     * Finds the year and term indices where a module is scheduled.
     *
     * @param moduleCode the code of the module to find
     * @return an int array [year, term] where the module is found, or null if not found
     */
    public int[] findModuleLocation(String moduleCode) throws ModuleNotFoundException {
        for (int year = 0; year < timetable.size(); year++) {
            List<List<Module>> yearSemesters = timetable.get(year);
            for (int term = 0; term < yearSemesters.size(); term++) {
                List<Module> modules = yearSemesters.get(term);
                for (Module module : modules) {
                    if (module.getCode().equals(moduleCode)) {
                        return new int[]{year, term};
                    }
                }
            }
        }
        throw new ModuleNotFoundException(moduleCode, "timetable");
    }

    /**
     * Retrieves the list of modules for a given year and term.
     *
     * @param year the year index (0-based)
     * @param term the term index (0-based)
     * @return list of modules in the specified term
     */
    public List<Module> getModules(int year, int term) {
        assert year >= 0 && year < AcademicConstants.NUM_YEARS : "getModules year out of bounds";
        assert term >= 0 && term < AcademicConstants.NUM_TERMS : "getModules term out of bounds";

        return timetable.get(year).get(term);
    }

    /**
     * Retrieves modules relative to a specific year and semester.
     * Note: This method uses 0-based indices internally.
     *
     * @param targetYear the target year (0-based)
     * @param targetSem  the target semester (0-based)
     * @param range      whether to get modules before or after the target
     * @return a flat list of modules matching the criteria
     */
    private List<Module> getModulesRelativeTo(int targetYear, int targetSem, TimeRange range) {
        List<Module> modules = new ArrayList<>();

        for (int y = 0; y < AcademicConstants.NUM_YEARS; y++) {
            for (int s = 0; s < AcademicConstants.NUM_TERMS; s++) {
                boolean shouldInclude;

                if (range == TimeRange.BEFORE) {
                    // Include if (year < target) OR (same year AND semester < target)
                    shouldInclude = (y < targetYear) || (y == targetYear && s < targetSem);
                } else { // TimeRange.AFTER
                    // Include if (year > target) OR (same year AND semester > target)
                    shouldInclude = (y > targetYear) || (y == targetYear && s > targetSem);
                }

                if (shouldInclude) {
                    try {
                        modules.addAll(timetable.get(y).get(s));
                    } catch (IndexOutOfBoundsException e) {
                        logger.log(Level.WARNING, "Error accessing timetable slot: Y" + y + " S" + s, e);
                    }
                }
            }
        }
        return modules;
    }

    private List<Module> getModulesTakenUpTo(int targetYear, int targetSem) {
        return getModulesRelativeTo(targetYear, targetSem, TimeRange.BEFORE);
    }

    private List<Module> getModulesTakenAfter(int targetYear, int targetSem) {
        return getModulesRelativeTo(targetYear, targetSem, TimeRange.AFTER);
    }

    /**
     * Retrieves all modules across all years and terms in the timetable.
     *
     * @return a flat list of all modules
     */
    public List<Module> getAllModules() {
        List<Module> all = new ArrayList<>();
        for (List<List<Module>> year : timetable) {
            for (List<Module> term : year) {
                all.addAll(term);
            }
        }
        return all;
    }

    /**
     * Prints a formatted view of the timetable to the console,
     * organized by year and term in table format.
     */
    public void printTimetable() {
        for (int year = 0; year < timetable.size(); year++) {
            System.out.println("+--------------------+--------------------+");
            String yearTitle = "YEAR " + (year + 1);
            System.out.printf("%22s\n", yearTitle);
            System.out.println("+--------------------+--------------------+");
            System.out.printf("|%-20s|%-20s|\n", "Semester 1", "Semester 2");
            System.out.println("+--------------------+--------------------+");

            List<List<Module>> semesters = timetable.get(year);
            int maxRows = Math.max(semesters.get(0).size(), semesters.get(1).size());

            for (int row = 0; row < maxRows; row++) {
                String s1 = row < semesters.get(0).size() ? semesters.get(0).get(row).getCode() : "";
                String s2 = row < semesters.get(1).size() ? semesters.get(1).get(row).getCode() : "";
                System.out.printf("|%-20s|%-20s|\n", s1, s2);
            }

            System.out.println("+--------------------+--------------------+\n");
        }
    }

    /**
     * Removes all modules from the entire timetable.
     */
    public void clearTimetable() {
        for (List<List<Module>> year : timetable) {
            for (List<Module> sem : year) {
                sem.clear();
            }
        }
    }
}
