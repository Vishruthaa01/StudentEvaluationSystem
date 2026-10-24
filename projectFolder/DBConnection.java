import java.sql.Connection;
import java.sql.DriverManager; 
import java.sql.SQLException;

public class DBConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/kec_db";
    private static final String user = "root";
    private static final String passwd = "vr@2007";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, user, passwd);
    }

    public static void main(String[] args) {
        try(Connection con = getConnection()) {
            System.out.println("Connected to database");
        }catch(SQLException e){
            System.out.println("Failed: " + e.getMessage());
        }
    }
}