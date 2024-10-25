package utils;

import at.jku.isse.ecco.experiment.utils.ResourceUtils;

import java.io.File;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseResultUtils {

    private static String getDatabaseURL(){
        Path databaseFolderPath = ResourceUtils.getResourceFolderPath("database");
        Path databasePath = databaseFolderPath.resolve("results.db");
        return "jdbc:sqlite:" + databasePath;
    }

    public static boolean databaseExists(){
        Path databaseFolderPath = ResourceUtils.getResourceFolderPath("database");
        Path databasePath = databaseFolderPath.resolve("results.db");
        File databaseFile = new File(databasePath.toString());
        return databaseFile.exists() && databaseFile.isFile();
    }

    public static boolean checkNumberOfResults(int results){
        String url = getDatabaseURL();
        var sql = "SELECT COUNT(*) AS total_rows FROM results";

        try (var conn = DriverManager.getConnection(url);
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int rowCount = rs.getInt("total_rows");
                return rowCount == results;
            } else {
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static boolean checkF1OfSingleResult(double f1){
        String url = getDatabaseURL();
        var sql = "SELECT f1 FROM results";

        try (var conn = DriverManager.getConnection(url);
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                double resultF1 = rs.getDouble("f1");
                return resultF1 == f1;
            } else {
                throw new RuntimeException("No result in table!");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
