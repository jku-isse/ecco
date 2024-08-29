package at.jku.isse.ecco.experiment.result.persister;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.result.Result;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;


public class ResultDatabasePersister implements ResultPersister{
    private final String databaseURL;

    public ResultDatabasePersister(String databaseFolderPath){
        Path databaseFullPath = (Paths.get(databaseFolderPath)).resolve("results.db");
        this.databaseURL = "jdbc:sqlite:" + databaseFullPath;

        if (!this.databaseExists(databaseFullPath.toString())){
            this.createDatabase();
            this.createResultTable();
        }
    }

    private boolean databaseExists(String databasePath){
        File databaseFile = new File(databasePath);
        return databaseFile.exists() && databaseFile.isFile();
    }

    @Override
    public void persist(Result result, ExperimentRunConfiguration config) {
        String sql = "INSERT INTO results (repository, numberOfVariants, variantConfigurations, " +
                "numberOfSampledFeatures, sampledFeatures, featureTracePercentage, mistakePercentage, " +
                "evaluationStrategy, mistakeType, tp, fp, tn, fn, precision, recall, f1) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        String variantConfigurations = String.join("; ",
                config.getVariantConfigurations());

        try (Connection conn = DriverManager.getConnection(this.databaseURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)){

            pstmt.setString(1, config.getRepositoryName());
            pstmt.setInt(2, config.getVariantConfigurations().size());
            pstmt.setString(3, variantConfigurations);
            pstmt.setInt(4, config.getFeatures().size());
            pstmt.setString(5, String.join(", ", config.getFeatures()));
            pstmt.setInt(6, config.getFeatureTracePercentage());
            pstmt.setInt(7, config.getMistakePercentage());
            pstmt.setString(8, config.getEvaluationStrategy().getStrategyName());
            pstmt.setString(9, config.getMistakeStrategy());
            pstmt.setInt(10, result.getTp());
            pstmt.setInt(11, result.getFp());
            pstmt.setInt(12, result.getTn());
            pstmt.setInt(13, result.getFn());
            pstmt.setDouble(14, result.getPrecision());
            pstmt.setDouble(15, result.getRecall());
            pstmt.setDouble(16, result.getF1());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void createResultTable(){
        String sql = "CREATE TABLE IF NOT EXISTS results ("
                + "	id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "	repository TEXT NOT NULL,"
                + "	numberOfVariants INTEGER NOT NULL,"
                + "	variantConfigurations TEXT NOT NULL,"
                + "	numberOfSampledFeatures INTEGERS NOT NULL,"
                + "	sampledFeatures TEXT NOT NULL,"
                + "	featureTracePercentage INTEGER NOT NULL,"
                + "	mistakePercentage INTEGER NOT NULL,"
                + "	evaluationStrategy TEXT NOT NULL,"
                + "	mistakeType TEXT NOT NULL,"
                + "	tp INTEGER NOT NULL,"
                + "	fp INTEGER NOT NULL,"
                + "	tn INTEGER NOT NULL,"
                + " fn INTEGER NOT NULL,"
                + "	precision DOUBLE NOT NULL,"
                + "	recall DOUBLE NOT NULL,"
                + "	f1 DOUBLE NOT NULL"
                + ");";



        try (Connection conn = DriverManager.getConnection(this.databaseURL)){
            Statement stmt = conn.createStatement();
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
            // TODO: log the string in case there is a problem!
        }
    }

    private void createDatabase() {
        try (var conn = DriverManager.getConnection(this.databaseURL)) {
            if (conn != null) {
                var meta = conn.getMetaData();
                System.out.println("The driver name is " + meta.getDriverName());
                System.out.println("A new database has been created.");
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }
}
