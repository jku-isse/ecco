package at.jku.isse.ecco.test;
import at.jku.isse.ecco.service.EccoService;
import com.google.common.io.RecursiveDeleteOption;
import org.testng.Assert;
import org.testng.annotations.Test;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.google.common.io.MoreFiles.deleteDirectoryContents;
public class RepositoryTest {
    Path basePath = Path.of("D:\\Eigene Daten\\Studium\\Studium\\LVAs\\6_Semester\\Bsc\\ecco\\examples\\image_variants"); //TODO change to relative path

    @Test(groups = {"integration", "gui"})
    public void populateSimpleVersionRepository() {

        // open repository
        EccoService service = new EccoService();

        //create Repo
        String repo = ".ecco";
        Path p = basePath.resolve(repo);
        try {
            deleteDirectoryContents(p, RecursiveDeleteOption.ALLOW_INSECURE);       //TBE ALLOW INSECURE
            Files.delete(p);        //TBE Works only if the dir is already empty. (done by  deleteDirectoryContents)
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
            deleteDirectoryContents(p, RecursiveDeleteOption.ALLOW_INSECURE);       //TBE ALLOW INSECURE
            Files.delete(p);        //TBE Works only if the dir is already empty. (done by  deleteDirectoryContents)
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
    }

    @Test(groups = {"integration", "gui"})
    public void setupReproForManualInspectionBigHistory() {     //TBE
        // open repository
        EccoService service = new EccoService();

        //create Repo
        String repo = ".ecco";

        basePath = basePath.getParent().resolve("BigHistory");
        Path p = basePath.resolve(repo);
        try {
            deleteDirectoryContents(p, RecursiveDeleteOption.ALLOW_INSECURE);       //TBE ALLOW INSECURE
            Files.delete(p);        //TBE Works only if the dir is already empty. (done by  deleteDirectoryContents)
        } catch (IOException e) {
            e.printStackTrace();
        }
        Assert.assertFalse(Files.exists(p));

        service.setRepositoryDir(p);
        service.init();

       //int commitNumber = 1;
        File[] files = basePath.toFile().listFiles();
        for(int commitNumber = 1; commitNumber < files.length; commitNumber++) {
            service.setBaseDir(basePath.resolve(files[commitNumber].getName()));
            System.out.println(files[commitNumber].getName());
            service.commit(commitNumber + ". Commit: " + files[commitNumber].getName());
            //commitNumber++;
        }
    }

}
