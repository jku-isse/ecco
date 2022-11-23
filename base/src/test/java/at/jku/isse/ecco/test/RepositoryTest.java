package at.jku.isse.ecco.test;

import at.jku.isse.ecco.service.EccoService;
import com.google.common.io.RecursiveDeleteOption;
import org.testng.Assert;
import org.testng.annotations.Test;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.io.MoreFiles.deleteDirectoryContents;

public class RepositoryTest {
    Path basePath = Path.of("D:\\Eigene Daten\\Studium\\Studium\\LVAs\\6_Semester\\Bsc\\ecco\\examples\\image_variants"); //TODO adapt path

    @Test(groups = {"integration", "gui"})
    public void populateSimpleVersionRepository() {

        // open repository
        EccoService service = new EccoService();

        //create Repo
        String repo = ".ecco";
        Path p = basePath.resolve(repo);
        try {
            deleteDirectoryContents(p, RecursiveDeleteOption.ALLOW_INSECURE);       //ALLOW INSECURE
            Files.delete(p);        //Works only if the dir is already empty. (done by  deleteDirectoryContents)
        } catch (IOException e) {
            e.printStackTrace();
        }
        Assert.assertFalse(Files.exists(p));

        service.setRepositoryDir(p);
        service.init();

        int variantsCnt = 0;
        service.setBaseDir(basePath.resolve("V1_purpleshirt"));
        service.commit("V1");
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V2_stripedshirt"));
        service.commit("V2");
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V3_purpleshirt_jacket"));
        service.commit("V3");
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V4_purpleshirt_jacket_glasses"));
        service.commit("V4");
        variantsCnt++;

        System.out.printf("Committed %d variants\n", variantsCnt);


        // close repository
        service.close();
        System.out.println("Repository closed.");
    }

    @Test(groups = {"integration", "gui"})
    public void setupReproForManualInspection() {
        // open repository
        EccoService service = new EccoService();

        //create Repo
        String repo = ".ecco";
        Path p = basePath.resolve(repo);
        try {
            deleteDirectoryContents(p, RecursiveDeleteOption.ALLOW_INSECURE);       //ALLOW INSECURE
            Files.delete(p);        //Works only if the dir is already empty. (done by  deleteDirectoryContents)
        } catch (IOException e) {
            e.printStackTrace();
        }
        Assert.assertFalse(Files.exists(p));

        service.setRepositoryDir(p);
        service.init();

        int variantsCnt = 0;
        service.setBaseDir(basePath.resolve("V1_purpleshirt"));
        service.commit();
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V2_stripedshirt"));
        service.commit();
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V3_purpleshirt_jacket"));
        service.commit();
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V4_purpleshirt_jacket_glasses"));
        service.commit();
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V5_stripedshirt_jacket_glasses"));
        service.commit();
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V6_stripedshirt_glasses"));
        service.commit();
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V7_purpleshirt_glasses"));
        service.commit();
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V8_stripedshirt_jacket"));
        service.commit();
        variantsCnt++;

        service.setBaseDir(basePath.resolve("V9_stripedshirt_jacket_hat"));
        service.commit();
        variantsCnt++;
    }

    @Test(groups = {"integration", "gui"})
    public void setupReproForManualInspectionBigHistory() {

        long starttime = System.currentTimeMillis();
        // open repository
        EccoService service = new EccoService();

        //create Repo
        String repo = ".ecco";

        basePath = basePath.getParent().resolve("BigHistory");
        Path p = basePath.resolve(repo);
        try {
            deleteDirectoryContents(p, RecursiveDeleteOption.ALLOW_INSECURE);       //ALLOW INSECURE
            Files.delete(p);        //Works only if the dir is already empty. (done by  deleteDirectoryContents)
        } catch (IOException e) {
            e.printStackTrace();
        }
        Assert.assertFalse(Files.exists(p));

        service.setRepositoryDir(p);
        service.init();

        List<Long> time = new ArrayList<>();
        File[] files = basePath.toFile().listFiles();
        long prevTime = System.currentTimeMillis();
        for (int commitNumber = 1; commitNumber < files.length; commitNumber++) {
            service.setBaseDir(basePath.resolve(files[commitNumber].getName()));
            System.out.println(files[commitNumber].getName());
            service.commit(commitNumber + ". Commit: " + files[commitNumber].getName());
            time.add(System.currentTimeMillis() - prevTime);
            prevTime = System.currentTimeMillis();
        }
        System.out.println(System.currentTimeMillis() - starttime);
        System.out.println(time.toString());

        System.out.println("Nr of Ass." + service.getRepository().getAssociations().size());
    }

    @Test(groups = {"integration", "gui"})
    public void populatePythonTests() {

        // open repository
        EccoService service = new EccoService();

        Path eccoPath = Path.of(System.getProperty("user.dir")).getParent();
        Path repoPath = eccoPath.resolve("examples").resolve("python_variants");

        String repo = ".ecco";
        Path p = repoPath.resolve(repo);
        deleteDir(p);
        Assert.assertFalse(Files.exists(p));

        service.setRepositoryDir(p);
        service.init();

        // make commits
        String[] commits = new String[]{
                "V1_purpleshirt",
                "V2_stripedshirt",
                "V3_purpleshirt_jacket",
                "V4_purpleshirt_jacket_glasses",
                "V5_stripedshirt_jacket_glasses",
                "V6_stripedshirt_glasses",
                "V7_purpleshirt_glasses",
                "V8_stripedshirt_jacket",
                "V9_stripedshirt_jacket_hat"
        };

        for (int i = 0; i < commits.length; i++) {
            service.setBaseDir(repoPath.resolve(commits[i]));
            service.commit(commits[i]);

            System.out.printf("Commit %d successful\n", i + 1);
        }

        // checkout variants
        String[] checkouts = new String[]{
                "person.1, purpleshirt.1, glasses.1, hat.1",
                "person.1, purpleshirt.1, glasses.1"
        };

        for (int i = 0; i < checkouts.length; i++) {
            String name = "C" + i + "_" + checkouts[i].replaceAll("[.][0-9]+", "").replaceAll(", ", "_");
            Path compositionPath = repoPath.resolve(name);
            deleteDir(compositionPath);
            createDir(compositionPath);
            service.setBaseDir(compositionPath);
            service.checkout(checkouts[i]);

            System.out.printf("Checkout %d successful\n", i + 1);
        }

        service.close();
    }

    private void deleteDir(Path p) {
        try {
            deleteDirectoryContents(p, RecursiveDeleteOption.ALLOW_INSECURE);       //ALLOW INSECURE
            Files.delete(p);        //Works only if the dir is already empty. (done by  deleteDirectoryContents)
        } catch (NoSuchFileException e) {
            // ignore
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDir(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
