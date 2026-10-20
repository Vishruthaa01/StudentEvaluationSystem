import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AcademicDAO {

    private static final double CIA_TEST_SHARE = 0.60;
    private static final double CIA_ASSIGNMENT_SHARE = 0.40;

    private static final String BASE_SELECT =
        "SELECT s.roll_no, s.name, s.dept, e.course_code, c.title, c.credits, c.course_type, "
      + "e.semester, e.total_classes, e.attended_classes, e.condonation_approved, "
      + "m.cia1, m.cia2, m.assignment, m.ese, m.ese_absent "
      + "FROM enrollments e "
      + "JOIN students s ON e.roll_no = s.roll_no "
      + "JOIN courses c ON e.course_code = c.course_code "
      + "LEFT JOIN assessment_marks m ON e.enroll_id = m.enroll_id ";

    private static class CourseResult {
        String rollNo;
        String name;
        String dept;
        String courseCode;
        String title;
        String type;
        int credits;
        int semester;
        int totalClasses;
        int attendedClasses;
        boolean condonation;
        boolean absent;
        Double cia1;
        Double cia2;
        Double assignment;
        Double ese;

        double attendancePct;
        Double ciaWeighted;
        Double eseWeighted;
        Integer total;
        String grade = "--";
        int gradePoint = 0;
        String status = "PENDING";
        String remark = "";

        void evaluate() {
            attendancePct = totalClasses == 0 ? 0.0 : attendedClasses * 100.0 / totalClasses;

            int ciaPct;
            int esePct;
            int eseMinPct;
            if (type.equals("THEORY")) {
                ciaPct = 40; esePct = 60; eseMinPct = 45;
            } else if (type.equals("PRACTICAL")) {
                ciaPct = 60; esePct = 40; eseMinPct = 50;
            } else {
                ciaPct = 50; esePct = 50; eseMinPct = 45;
            }

            if (cia1 != null && cia2 != null && assignment != null) {
                double cia100 = (cia1 + cia2) / 2.0 * CIA_TEST_SHARE + assignment * CIA_ASSIGNMENT_SHARE;
                ciaWeighted = cia100 * ciaPct / 100.0;
            }
            if (ese != null && !absent) {
                eseWeighted = ese * esePct / 100.0;
            }

            if (totalClasses == 0) {
                setOutcome("--", "Attendance not entered");
                return;
            }
            if (attendancePct < 65.0) {
                setOutcome("SA", "Shortage of attendance");
                return;
            }
            if (attendancePct < 75.0 && !condonation) {
                setOutcome("SA", "Condonation not approved");
                return;
            }
            if (absent) {
                setOutcome("AB", "Absent in ESE");
                return;
            }
            if (ciaWeighted == null || eseWeighted == null) {
                setOutcome("--", "Marks pending");
                return;
            }

            total = (int) Math.round(ciaWeighted + eseWeighted);

            if (ese < eseMinPct) {
                setOutcome("RA", "ESE below minimum");
            } else if (total < 50) {
                setOutcome("RA", "Total below 50");
            } else if (total >= 91) {
                setOutcome("O", "");
            } else if (total >= 81) {
                setOutcome("A+", "");
            } else if (total >= 71) {
                setOutcome("A", "");
            } else if (total >= 61) {
                setOutcome("B+", "");
            } else {
                setOutcome("B", "");
            }
        }

        void setOutcome(String g, String r) {
            grade = g;
            remark = r;
            switch (g) {
                case "O":  gradePoint = 10; status = "PASS"; break;
                case "A+": gradePoint = 9;  status = "PASS"; break;
                case "A":  gradePoint = 8;  status = "PASS"; break;
                case "B+": gradePoint = 7;  status = "PASS"; break;
                case "B":  gradePoint = 6;  status = "PASS"; break;
                case "--": gradePoint = 0;  status = "PENDING"; break;
                default:   gradePoint = 0;  status = "FAIL";
            }
        }
    }

    private static class Sgpa {
        double value;
        int completed;
        int pending;
        int failed;
    }

    public void addStudent(String rollNo, String name, String dept, int semester) throws SQLException {
        String sql = "INSERT INTO students (roll_no, name, dept, semester) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rollNo);
            ps.setString(2, name);
            ps.setString(3, dept);
            ps.setInt(4, semester);
            ps.executeUpdate();
        }
    }

    public void addCourse(String courseCode, String title, int credits, String courseType,
                          int semester) throws SQLException {
        String sql = "INSERT INTO courses (course_code, title, credits, course_type, semester) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            ps.setString(2, title);
            ps.setInt(3, credits);
            ps.setString(4, courseType);
            ps.setInt(5, semester);
            ps.executeUpdate();
        }
    }

    public int countCoursesInSemester(int semester) throws SQLException {
        String sql = "SELECT COUNT(*) FROM courses WHERE semester = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, semester);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<String> enrollSemester(String rollNo, int semester) throws SQLException {
        String studentSql = "SELECT 1 FROM students WHERE roll_no = ?";
        String findSql = "SELECT c.course_code FROM courses c WHERE c.semester = ? "
                       + "AND NOT EXISTS (SELECT 1 FROM enrollments e "
                       + "WHERE e.roll_no = ? AND e.course_code = c.course_code AND e.semester = ?) "
                       + "ORDER BY c.course_code";
        String enrollSql = "INSERT INTO enrollments (roll_no, course_code, semester) VALUES (?, ?, ?)";
        String marksSql = "INSERT INTO assessment_marks (enroll_id) VALUES (?)";

        try (Connection con = DBConnection.getConnection()) {

            try (PreparedStatement ps = con.prepareStatement(studentSql)) {
                ps.setString(1, rollNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                }
            }

            List<String> toEnroll = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(findSql)) {
                ps.setInt(1, semester);
                ps.setString(2, rollNo);
                ps.setInt(3, semester);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        toEnroll.add(rs.getString(1));
                    }
                }
            }

            if (toEnroll.isEmpty()) {
                return toEnroll;
            }

            con.setAutoCommit(false);
            try (PreparedStatement ps1 = con.prepareStatement(enrollSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement ps2 = con.prepareStatement(marksSql)) {

                for (String code : toEnroll) {
                    ps1.setString(1, rollNo);
                    ps1.setString(2, code);
                    ps1.setInt(3, semester);
                    ps1.executeUpdate();

                    int enrollId;
                    try (ResultSet keys = ps1.getGeneratedKeys()) {
                        keys.next();
                        enrollId = keys.getInt(1);
                    }

                    ps2.setInt(1, enrollId);
                    ps2.executeUpdate();
                }

                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
            return toEnroll;
        }
    }

    public boolean updateAttendance(String rollNo, String courseCode, int semester,
                                    int totalClasses, int attendedClasses) throws SQLException {
        String sql = "UPDATE enrollments SET total_classes = ?, attended_classes = ? "
                   + "WHERE roll_no = ? AND course_code = ? AND semester = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, totalClasses);
            ps.setInt(2, attendedClasses);
            ps.setString(3, rollNo);
            ps.setString(4, courseCode);
            ps.setInt(5, semester);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean setCondonation(String rollNo, String courseCode, int semester,
                                  boolean approved) throws SQLException {
        String sql = "UPDATE enrollments SET condonation_approved = ? "
                   + "WHERE roll_no = ? AND course_code = ? AND semester = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, approved);
            ps.setString(2, rollNo);
            ps.setString(3, courseCode);
            ps.setInt(4, semester);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean enterCIA(String rollNo, String courseCode, int semester,
                            double cia1, double cia2) throws SQLException {
        String sql = "UPDATE assessment_marks m JOIN enrollments e ON m.enroll_id = e.enroll_id "
                   + "SET m.cia1 = ?, m.cia2 = ? "
                   + "WHERE e.roll_no = ? AND e.course_code = ? AND e.semester = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, cia1);
            ps.setDouble(2, cia2);
            ps.setString(3, rollNo);
            ps.setString(4, courseCode);
            ps.setInt(5, semester);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean enterAssignment(String rollNo, String courseCode, int semester,
                                   double assignment) throws SQLException {
        String sql = "UPDATE assessment_marks m JOIN enrollments e ON m.enroll_id = e.enroll_id "
                   + "SET m.assignment = ? "
                   + "WHERE e.roll_no = ? AND e.course_code = ? AND e.semester = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, assignment);
            ps.setString(2, rollNo);
            ps.setString(3, courseCode);
            ps.setInt(4, semester);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean enterESE(String rollNo, String courseCode, int semester,
                            double ese) throws SQLException {
        String sql = "UPDATE assessment_marks m JOIN enrollments e ON m.enroll_id = e.enroll_id "
                   + "SET m.ese = ?, m.ese_absent = FALSE "
                   + "WHERE e.roll_no = ? AND e.course_code = ? AND e.semester = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, ese);
            ps.setString(2, rollNo);
            ps.setString(3, courseCode);
            ps.setInt(4, semester);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean markAbsent(String rollNo, String courseCode, int semester) throws SQLException {
        String sql = "UPDATE assessment_marks m JOIN enrollments e ON m.enroll_id = e.enroll_id "
                   + "SET m.ese = NULL, m.ese_absent = TRUE "
                   + "WHERE e.roll_no = ? AND e.course_code = ? AND e.semester = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rollNo);
            ps.setString(2, courseCode);
            ps.setInt(3, semester);
            return ps.executeUpdate() > 0;
        }
    }

    public void printMarkSheet(String rollNo, int semester) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {

            String name;
            String dept;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT name, dept FROM students WHERE roll_no = ?")) {
                ps.setString(1, rollNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("No student found with roll number " + rollNo + ".");
                        return;
                    }
                    name = rs.getString("name");
                    dept = rs.getString("dept");
                }
            }

            List<CourseResult> results;
            try (PreparedStatement ps = con.prepareStatement(
                    BASE_SELECT + "WHERE e.roll_no = ? AND e.semester = ? ORDER BY e.course_code")) {
                ps.setString(1, rollNo);
                ps.setInt(2, semester);
                try (ResultSet rs = ps.executeQuery()) {
                    results = readResults(rs);
                }
            }

            if (results.isEmpty()) {
                System.out.println("No enrollments found for " + rollNo + " in semester " + semester + ".");
                return;
            }

            String line = "=".repeat(122);
            System.out.println();
            System.out.println(line);
            System.out.println("                      KONGU ENGINEERING COLLEGE (AUTONOMOUS) - R2024");
            System.out.println("                              SEMESTER GRADE SHEET");
            System.out.println(line);
            System.out.println("Roll No  : " + rollNo);
            System.out.println("Name     : " + name);
            System.out.println("Dept     : " + dept + "          Semester : " + semester);
            System.out.println(line);
            System.out.printf("%-9s %-24s %2s %-9s %5s %8s %8s %5s %-5s %2s %-7s %s%n",
                    "Code", "Course Title", "Cr", "Type", "Att%", "CIA", "ESE", "Total",
                    "Grade", "GP", "Result", "Remark");
            System.out.println("-".repeat(122));

            for (CourseResult r : results) {
                r.evaluate();
                String cia = r.ciaWeighted == null ? "-" : String.format("%.2f", r.ciaWeighted);
                String ese = r.eseWeighted == null ? "-" : String.format("%.2f", r.eseWeighted);
                String total = r.total == null ? "-" : String.valueOf(r.total);
                System.out.printf("%-9s %-24s %2d %-9s %5.1f %8s %8s %5s %-5s %2d %-7s %s%n",
                        r.courseCode, cut(r.title, 24), r.credits, r.type, r.attendancePct,
                        cia, ese, total, r.grade, r.gradePoint, r.status, r.remark);
            }
            System.out.println("-".repeat(122));

            Sgpa sg = computeSgpa(results);
            if (sg.completed == 0) {
                System.out.println("SGPA       : N/A (no course has a final result yet)");
            } else {
                System.out.printf("SGPA       : %.2f%n", sg.value);
                System.out.printf("Percentage : %.2f%%%n", sg.value * 10);
            }

            String overall;
            if (sg.completed == 0) {
                overall = "NOT AVAILABLE";
            } else if (sg.pending > 0) {
                overall = "INCOMPLETE (" + sg.pending + " course(s) pending, SGPA is provisional)";
            } else if (sg.failed > 0) {
                overall = "FAIL (" + sg.failed + " course(s) to re-appear)";
            } else {
                overall = "PASS";
            }
            System.out.println("Overall    : " + overall);
            System.out.println(line);
        }
    }

    public void printDefaulterReport() throws SQLException {
        String sql = "SELECT s.roll_no, s.name, s.dept, e.course_code, e.semester, "
                   + "e.total_classes, e.attended_classes "
                   + "FROM enrollments e JOIN students s ON e.roll_no = s.roll_no "
                   + "WHERE e.total_classes > 0 AND e.attended_classes * 100.0 / e.total_classes < 65 "
                   + "ORDER BY s.dept, s.roll_no, e.course_code";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            String line = "=".repeat(96);
            System.out.println();
            System.out.println(line);
            System.out.println("            ATTENDANCE DEFAULTER REPORT (below 65% - Grade SA)");
            System.out.println(line);
            System.out.printf("%-10s %-20s %-6s %-9s %3s %9s %7s %-5s%n",
                    "Roll No", "Name", "Dept", "Course", "Sem", "Attended", "Att%", "Grade");
            System.out.println("-".repeat(96));

            int count = 0;
            while (rs.next()) {
                int total = rs.getInt("total_classes");
                int attended = rs.getInt("attended_classes");
                System.out.printf("%-10s %-20s %-6s %-9s %3d %9s %7.1f %-5s%n",
                        rs.getString("roll_no"), cut(rs.getString("name"), 20), rs.getString("dept"),
                        rs.getString("course_code"), rs.getInt("semester"),
                        attended + "/" + total, attended * 100.0 / total, "SA");
                count++;
            }
            System.out.println("-".repeat(96));
            if (count == 0) {
                System.out.println("No defaulters found.");
            } else {
                System.out.println("Total defaulter records: " + count);
            }
            System.out.println(line);
        }
    }

    public void printClassSummary(int semester) throws SQLException {
        Map<String, Map<String, List<CourseResult>>> byDept = new TreeMap<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     BASE_SELECT + "WHERE e.semester = ? ORDER BY s.dept, s.roll_no")) {
            ps.setInt(1, semester);
            try (ResultSet rs = ps.executeQuery()) {
                for (CourseResult r : readResults(rs)) {
                    r.evaluate();
                    byDept.computeIfAbsent(r.dept, k -> new TreeMap<>())
                          .computeIfAbsent(r.rollNo, k -> new ArrayList<>())
                          .add(r);
                }
            }
        }

        String line = "=".repeat(60);
        System.out.println();
        System.out.println(line);
        System.out.println("    CLASS PERFORMANCE & RESULT SUMMARY - SEMESTER " + semester);
        System.out.println(line);

        if (byDept.isEmpty()) {
            System.out.println("No enrollments found for this semester.");
            System.out.println(line);
            return;
        }

        System.out.printf("%-8s %8s %6s %6s %8s %10s%n",
                "Dept", "Students", "Pass", "Fail", "Pending", "Avg SGPA");
        System.out.println("-".repeat(60));

        int allStudents = 0;
        int allPass = 0;
        int allFail = 0;
        int allPending = 0;
        double allSgpaSum = 0;
        int allSgpaCount = 0;

        for (Map.Entry<String, Map<String, List<CourseResult>>> d : byDept.entrySet()) {
            int students = 0;
            int pass = 0;
            int fail = 0;
            int pending = 0;
            double sgpaSum = 0;
            int sgpaCount = 0;

            for (List<CourseResult> courses : d.getValue().values()) {
                students++;
                Sgpa sg = computeSgpa(courses);
                if (sg.pending > 0) {
                    pending++;
                } else {
                    sgpaSum += sg.value;
                    sgpaCount++;
                    if (sg.failed > 0) {
                        fail++;
                    } else {
                        pass++;
                    }
                }
            }

            String avg = sgpaCount == 0 ? "N/A" : String.format("%.2f", sgpaSum / sgpaCount);
            System.out.printf("%-8s %8d %6d %6d %8d %10s%n", d.getKey(), students, pass, fail, pending, avg);

            allStudents += students;
            allPass += pass;
            allFail += fail;
            allPending += pending;
            allSgpaSum += sgpaSum;
            allSgpaCount += sgpaCount;
        }

        System.out.println("-".repeat(60));
        String allAvg = allSgpaCount == 0 ? "N/A" : String.format("%.2f", allSgpaSum / allSgpaCount);
        System.out.printf("%-8s %8d %6d %6d %8d %10s%n", "ALL", allStudents, allPass, allFail, allPending, allAvg);
        System.out.println(line);
        System.out.println("Pass/Fail counts and Avg SGPA cover students whose results are complete.");
    }

    private List<CourseResult> readResults(ResultSet rs) throws SQLException {
        List<CourseResult> list = new ArrayList<>();
        while (rs.next()) {
            CourseResult r = new CourseResult();
            r.rollNo = rs.getString("roll_no");
            r.name = rs.getString("name");
            r.dept = rs.getString("dept");
            r.courseCode = rs.getString("course_code");
            r.title = rs.getString("title");
            r.credits = rs.getInt("credits");
            r.type = rs.getString("course_type");
            r.semester = rs.getInt("semester");
            r.totalClasses = rs.getInt("total_classes");
            r.attendedClasses = rs.getInt("attended_classes");
            r.condonation = rs.getBoolean("condonation_approved");
            r.cia1 = nullableDouble(rs, "cia1");
            r.cia2 = nullableDouble(rs, "cia2");
            r.assignment = nullableDouble(rs, "assignment");
            r.ese = nullableDouble(rs, "ese");
            r.absent = rs.getBoolean("ese_absent");
            list.add(r);
        }
        return list;
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    private Sgpa computeSgpa(List<CourseResult> courses) {
        Sgpa sg = new Sgpa();
        int credits = 0;
        int points = 0;
        for (CourseResult r : courses) {
            if (r.status.equals("PENDING")) {
                sg.pending++;
                continue;
            }
            credits += r.credits;
            points += r.credits * r.gradePoint;
            sg.completed++;
            if (r.status.equals("FAIL")) {
                sg.failed++;
            }
        }
        sg.value = credits == 0 ? 0.0 : (double) points / credits;
        return sg;
    }

    private String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }
}