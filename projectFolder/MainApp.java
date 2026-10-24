import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public class MainApp {

    private static final Scanner sc = new Scanner(System.in);
    private static final AcademicDAO dao = new AcademicDAO();

    public static void main(String[] args) {
        int choice; 
        do {
            printMenu();
            choice = readChoice();

            try {
                switch (choice) {
                    case 1:  addStudent();       break;
                    case 2:  addCourse();        break;
                    case 3:  enrollStudent();    break;
                    case 4:  updateAttendance(); break;
                    case 5:  enterCIA();         break;
                    case 6:  enterAssignment();  break;
                    case 7:  enterESE();         break;
                    case 8:  markSheet();        break;
                    case 9:  dao.printDefaulterReport(); break;
                    case 10: classSummary();     break;
                    case 11: condonation();      break;
                    case 0:  System.out.println("Goodbye!"); break;
                    default: System.out.println("Invalid choice. Try again.");
                }
            } catch (SQLException e) {
                if (e.getErrorCode() == 1062) {
                    System.out.println("Error: this record already exists.");
                } else if (e.getErrorCode() == 1452) {
                    System.out.println("Error: that student or course does not exist. Add it first.");
                } else {
                    System.out.println("Database error: " + e.getMessage());
                }
            }
        } while (choice != 0);

        sc.close();
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("==============================================");
        System.out.println("  KEC R2024 - Student Academic Evaluation");
        System.out.println("==============================================");
        System.out.println("   1. Add Student");
        System.out.println("   2. Add Course");
        System.out.println("   3. Enroll Student in a Semester's Courses");
        System.out.println("   4. Update Attendance");
        System.out.println("   5. Phase 1: CIA Test 1 & 2");
        System.out.println("   6. Phase 2: Assignment / Quiz / Lab");
        System.out.println("   7. Phase 3: ESE Marks / Mark Absent");
        System.out.println("   8. Student Semester Mark Sheet");
        System.out.println("   9. Attendance Defaulter Report (SA)");
        System.out.println("  10. Class Performance & Result Summary");
        System.out.println("  11. Medical Condonation (65% - 74.9%)");
        System.out.println("   0. Exit");
    }

    private static int readChoice() {
        System.out.print("Enter your choice: ");
        try {
            return Integer.parseInt(sc.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readText(String prompt) {
        while (true) {
            System.out.print(prompt + ": ");
            String s = sc.nextLine().trim();
            if (!s.isEmpty()) return s;
            System.out.println("  Input cannot be empty.");
        }
    }

    private static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt + ": ");
            try {
                int v = Integer.parseInt(sc.nextLine().trim());
                if (v >= min && v <= max) return v;
                System.out.println("  Enter a number between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a valid whole number.");
            }
        }
    }

    private static double readDouble(String prompt, double min, double max) {
        while (true) {
            System.out.print(prompt + ": ");
            try {
                double v = Double.parseDouble(sc.nextLine().trim());
                if (v >= min && v <= max) return v;
                System.out.println("  Enter a value between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a valid number.");
            }
        }
    }

    private static boolean readYesNo(String prompt) {
        while (true) {
            String s = readText(prompt + " (Y/N)").toUpperCase();
            if (s.startsWith("Y")) return true;
            if (s.startsWith("N")) return false;
            System.out.println("  Please answer Y or N.");
        }
    }

    private static String readCourseType() {
        while (true) {
            String t = readText("Course Type (THEORY / PRACTICAL / EMBEDDED)").toUpperCase();
            if (t.equals("THEORY") || t.equals("PRACTICAL") || t.equals("EMBEDDED")) return t;
            System.out.println("  Course type must be THEORY, PRACTICAL or EMBEDDED.");
        }
    }

    private static void notEnrolled() {
        System.out.println("No such enrollment found. Enroll the student in that semester first (option 3).");
    }

    private static void addStudent() throws SQLException {
        System.out.println("\n-- Add Student --");
        String roll = readText("Roll Number (e.g. 24ITR001)").toUpperCase();
        String name = readText("Name");
        String dept = readText("Department (CSE, ECE, MECH, IT ...)").toUpperCase();
        int sem = readInt("Current Semester", 1, 8);
        dao.addStudent(roll, name, dept, sem);
        System.out.println("Student " + roll + " added successfully.");
    }

    private static void addCourse() throws SQLException {
        System.out.println("\n-- Add Course --");
        String code = readText("Course Code (e.g. 24ITR31)").toUpperCase();
        String title = readText("Title");
        int credits = readInt("Credits", 0, 6);
        String type = readCourseType();
        int sem = readInt("Semester in which this course is offered", 1, 8);
        dao.addCourse(code, title, credits, type, sem);
        System.out.println("Course " + code + " added to semester " + sem + ".");
    }

    private static void enrollStudent() throws SQLException {
        System.out.println("\n-- Enroll Student in All Courses of a Semester --");
        String roll = readText("Roll Number").toUpperCase();
        int sem = readInt("Semester", 1, 8);

        List<String> enrolled = dao.enrollSemester(roll, sem);
        if (enrolled == null) {
            System.out.println("No student found with roll number " + roll + ". Add the student first (option 1).");
        } else if (!enrolled.isEmpty()) {
            System.out.println(roll + " enrolled in " + enrolled.size() + " course(s) for semester " + sem + ":");
            for (String code : enrolled) {
                System.out.println("  - " + code);
            }
        } else if (dao.countCoursesInSemester(sem) == 0) {
            System.out.println("No courses are defined for semester " + sem + " yet. Add them first (option 2).");
        } else {
            System.out.println(roll + " is already enrolled in every course of semester " + sem + ".");
        }
    }

    private static void updateAttendance() throws SQLException {
        System.out.println("\n-- Update Attendance --");
        String roll = readText("Roll Number").toUpperCase();
        String code = readText("Course Code").toUpperCase();
        int sem = readInt("Semester", 1, 8);
        int total = readInt("Total classes held", 0, 500);
        int attended = readInt("Classes attended", 0, total);
        if (dao.updateAttendance(roll, code, sem, total, attended)) {
            double pct = total == 0 ? 0 : attended * 100.0 / total;
            System.out.printf("Attendance saved: %.1f%%%n", pct);
        } else {
            notEnrolled();
        }
    }

    private static void enterCIA() throws SQLException {
        System.out.println("\n-- Phase 1: CIA Test 1 & 2 --");
        String roll = readText("Roll Number").toUpperCase();
        String code = readText("Course Code").toUpperCase();
        int sem = readInt("Semester", 1, 8);
        double cia1 = readDouble("CIA Test 1 (0-50)", 0, 50);
        double cia2 = readDouble("CIA Test 2 (0-50)", 0, 50);
        if (dao.enterCIA(roll, code, sem, cia1, cia2)) {
            System.out.println("CIA Test 1 and CIA Test 2 recorded.");
        } else {
            notEnrolled();
        }
    }

    private static void enterAssignment() throws SQLException {
        System.out.println("\n-- Phase 2: Assignment / Quiz / Lab --");
        String roll = readText("Roll Number").toUpperCase();
        String code = readText("Course Code").toUpperCase();
        int sem = readInt("Semester", 1, 8);
        double score = readDouble("Assignment (0 - 20)/ Quiz (0 - 20)/ Lab score (0-100)", 0, 100);
        if (dao.enterAssignment(roll, code, sem, score)) {
            System.out.println("Assignment / Lab score recorded.");
        } else {
            notEnrolled();
        }
    }

    private static void enterESE() throws SQLException {
        System.out.println("\n-- Phase 3: End Semester Examination --");
        String roll = readText("Roll Number").toUpperCase();
        String code = readText("Course Code").toUpperCase();
        int sem = readInt("Semester", 1, 8);

        boolean ok;
        if (readYesNo("Was the student absent in ESE?")) {
            ok = dao.markAbsent(roll, code, sem);
            if (ok) System.out.println("Marked AB (absent in ESE).");
        } else {
            double ese = readDouble("ESE marks (0-100)", 0, 100);
            ok = dao.enterESE(roll, code, sem, ese);
            if (ok) System.out.println("ESE marks recorded.");
        }
        if (!ok) {
            notEnrolled();
        }
    }

    private static void markSheet() throws SQLException {
        System.out.println("\n-- Student Semester Mark Sheet --");
        String roll = readText("Roll Number").toUpperCase();
        int sem = readInt("Semester", 1, 8);
        dao.printMarkSheet(roll, sem);
    }

    private static void classSummary() throws SQLException {
        System.out.println("\n-- Class Performance & Result Summary --");
        int sem = readInt("Semester", 1, 8);
        dao.printClassSummary(sem);
    }

    private static void condonation() throws SQLException {
        System.out.println("\n-- Medical Condonation --");
        String roll = readText("Roll Number").toUpperCase();
        String code = readText("Course Code").toUpperCase();
        int sem = readInt("Semester", 1, 8);
        boolean approved = readYesNo("Medical condonation approved?");
        if (dao.setCondonation(roll, code, sem, approved)) {
            System.out.println("Condonation status updated.");
        } else {
            notEnrolled();
        }
    }
}