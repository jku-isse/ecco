package at.jku.isse.ecco.test;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.service.EccoService;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static at.jku.isse.ecco.test.AdapterTestUtil.*;

public class PythonAdapterTest {


    @Test(groups = {"unit", "integration"})
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

        Collection<ArtifactPlugin> coll = service.getArtifactPlugins();
        boolean pythonPluginLoaded = service.getArtifactPlugins().stream().anyMatch(pl -> pl.getName().equals("PythonArtifactPlugin"));
        if (!pythonPluginLoaded) {
            System.out.println("Python Plugin not loaded ... skipping tests...");
            return;
        }

        try {
            List<String> directories =
                    Files.walk(repoPath, 1)
                            .filter(Files::isDirectory)
                            .map(f -> f.getFileName().toString())
                            .filter(s -> s.startsWith("C"))
                            .toList();
        } catch (IOException e) {
            // process exception
        }

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
                "person.1",
                "purpleshirt.1",
                "jacket.1",
                "stripedshirt.1",
                "glasses.1",
                "hat.1"
        };

        for (int i = 0; i < checkouts.length; i++) {
            String name = "C" + i + "_" + checkouts[i].replaceAll("[.][0-9]+", "").replaceAll(", ", "_");
            Path compositionPath = repoPath.resolve(name);

            recreateDir(compositionPath);

            service.setBaseDir(compositionPath);
            service.checkout(checkouts[i]);

            System.out.printf("Checkout %d successful\n", i + 1);
        }

        service.close();
    }

    @Test(groups = {"integration"})
    public void populateJupyterTests() {

        // open repository
        EccoService service = new EccoService();

        Path eccoPath = Path.of(System.getProperty("user.dir")).getParent();
        Path repoPath = eccoPath.resolve("examples").resolve("jupyter_variants");

        String repo = ".ecco";
        Path p = repoPath.resolve(repo);

        deleteDir(p);
        Assert.assertFalse(Files.exists(p));

        service.setRepositoryDir(p);
        service.init();

        Collection<ArtifactPlugin> coll = service.getArtifactPlugins();
        boolean pythonPluginLoaded = service.getArtifactPlugins().stream().anyMatch(pl -> pl.getName().equals("PythonArtifactPlugin"));
        if (!pythonPluginLoaded) {
            System.out.println("Python Plugin not loaded ... skipping tests...");
            return;
        }

        // make commits
        String[] commits = new String[]{
                "C1_initial_commit",
                "C2_new_dataset",
                "C3_adjusted_resolution",
                "C4_switched_to_smaller_model",
                "C5_added_tensorboard_logging",
                "C6_new_hyperparameter_set",
                "C7_added_email_notification"
        };

        for (int i = 0; i < commits.length; i++) {
            service.setBaseDir(repoPath.resolve(commits[i]));
            service.commit(commits[i]);

            System.out.printf("Commit %d successful\n", i + 1);
        }

        //AdapterTestUtil

        // intentional correctness - reproduce commits
        int k = 1;
        for (Commit c : service.getCommits()) {
            System.out.println(c.getConfiguration().toString());

            Path compositionPath = repoPath.resolve("extensional_correctness_check/Commit" + k);

            recreateDir(compositionPath);

            service.setBaseDir(compositionPath);

            service.checkout(c.getConfiguration().toString());
            System.out.printf("Checkout of Commit %d successful\n", k);
            Assert.assertTrue(
                    compareJupyterFiles(compositionPath.resolve("Object_Detection.ipynb"), repoPath.resolve(commits[k - 1]).resolve("Object_Detection.ipynb")), "Commit " + k + " intentional Correctness test failed!");
            k++;
        }

        // checkout valid variants
        String[] invalidCheckouts = new String[]{
                "train.1, resolution.1",
                "dataset.2, export.1, log.1"
        };

        for (int i = 0; i < invalidCheckouts.length; i++) {
            String name = "IV" + i + "_" + invalidCheckouts[i].replaceAll("[.][0-9]+", "").replaceAll(", ", "_");
            Path compositionPath = repoPath.resolve(name);

            recreateDir(compositionPath);

            service.setBaseDir(compositionPath);
            service.checkout(invalidCheckouts[i]);

            System.out.printf("Invalid Checkout %d successful\n", i + 1);
        }

        // checkout valid variants
        String[] validCheckouts = new String[]{
                "train.1, resolution.1, parameters.1, weights.1, dataset.1, export.1, notify.1, log.1",
                "train.1, resolution.2, parameters.2, weights.2, dataset.2, export.1",
                "train.1, resolution.2, parameters.2, weights.2, dataset.1, export.1, log.1, notify.1",
        };

        for (int i = 0; i < validCheckouts.length; i++) {
            String name = "VV" + i + "_" + validCheckouts[i].replaceAll("[.][0-9]+", "").replaceAll(", ", "_");
            Path compositionPath = repoPath.resolve(name);

            recreateDir(compositionPath);

            service.setBaseDir(compositionPath);
            service.checkout(validCheckouts[i]);

            System.out.printf("Valid Checkout %d successful\n", i + 1);
        }

        service.close();
    }

    @Test(groups = {"integration"})
    public void populateJupyterCrossTests() {

        // open repository
        EccoService service = new EccoService();

        Path eccoPath = Path.of(System.getProperty("user.dir")).getParent();
        Path repoPath = eccoPath.resolve("examples").resolve("jupyter_variants_cross");

        String repo = ".ecco";
        Path p = repoPath.resolve(repo);

        deleteDir(p);
        Assert.assertFalse(Files.exists(p));

        service.setRepositoryDir(p);
        service.init();

        Collection<ArtifactPlugin> coll = service.getArtifactPlugins();
        boolean pythonPluginLoaded = service.getArtifactPlugins().stream().anyMatch(pl -> pl.getName().equals("PythonArtifactPlugin"));
        if (!pythonPluginLoaded) {
            System.out.println("Python Plugin not loaded ... skipping tests...");
            return;
        }

        // make commits
        String[] commits = new String[]{
                "C1_initial_commit",
                "C2_new_dataset",
                "C3_adjusted_resolution",
                "C4_switched_to_smaller_model",
                "C5_added_tensorboard_logging",
                "C6_new_hyperparameter_set",
                "C7_removed_email_notification",
        };

        for (int i = 0; i < commits.length; i++) {
            service.setBaseDir(repoPath.resolve(commits[i]));
            service.commit(commits[i]);

            System.out.printf("Commit %d successful\n", i + 1);
        }

        //AdapterTestUtil

        // extensional correctness - reproduce commits
        int k = 1;
        for (Commit c : service.getCommits()) {
            System.out.println(c.getConfiguration().toString());

            Path compositionPath = repoPath.resolve("extensional_correctness_check/Commit" + k);

            recreateDir(compositionPath);

            service.setBaseDir(compositionPath);

            service.checkout(c.getConfiguration().toString());
            System.out.printf("Checkout of Commit %d successful\n", k);
            Assert.assertTrue(
                    compareJupyterFiles(compositionPath.resolve("Object_Detection.ipynb"), repoPath.resolve(commits[k - 1]).resolve("Object_Detection.ipynb")), "Commit " + k + " intentional Correctness test failed!");
            k++;
        }

        // checkout valid variants
        String[] invalidCheckouts = new String[]{
                "train.1, resolution.1",
                "dataset.2, export.1, log.1"
        };

        for (int i = 0; i < invalidCheckouts.length; i++) {
            String name = "IV" + i + "_" + invalidCheckouts[i].replaceAll("[.][0-9]+", "").replaceAll(", ", "_");
            Path compositionPath = repoPath.resolve(name);

            recreateDir(compositionPath);

            service.setBaseDir(compositionPath);
            service.checkout(invalidCheckouts[i]);

            System.out.printf("Invalid Checkout %d successful\n", i + 1);
        }

        // checkout valid variants
        String[] validCheckouts = new String[]{
                "train.1, resolution.1, parameters.1, weights.1, dataset.1, export.1, notify.1, log.1",
                "train.1, resolution.2, parameters.2, weights.2, dataset.2, export.1",
                "train.1, resolution.2, parameters.2, weights.2, dataset.1, export.1, log.1, notify.1",
        };

        for (int i = 0; i < validCheckouts.length; i++) {
            String name = "VV" + i + "_" + validCheckouts[i].replaceAll("[.][0-9]+", "").replaceAll(", ", "_");
            Path compositionPath = repoPath.resolve(name);

            recreateDir(compositionPath);

            service.setBaseDir(compositionPath);
            service.checkout(validCheckouts[i]);

            System.out.printf("Valid Checkout %d successful\n", i + 1);
        }

        service.close();
    }

}
