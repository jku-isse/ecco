package at.jku.isse.ecco.extras;

import at.jku.isse.ecco.service.EccoService;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RepositoryPopulationUtil {

    private final EccoService service;

    public RepositoryPopulationUtil(EccoService service) {
        this.service = service;
    }

    /**
     * Populates an ECCO repository by committing all variant directories
     * from start (inclusive) to end (inclusive), formatted as zero-padded numbers.
     */
    public int populate(Path basePath, String repoDir, int start, int end) {

        Path repoPath = basePath.resolve(repoDir);

        service.setRepositoryDir(repoPath);
        service.init();

        int count = 0;

        try {
            for (int i = start; i <= end; i++) {
                String variant = String.format("%03d", i);
                service.setBaseDir(basePath.resolve(variant));
                service.commit(variant);

                count++;
            }

            return count;

        } finally {
            service.close();
            System.out.println("Repository closed.");
        }
    }

    public static void main(String[] args) {

        //Path basePath = Paths.get(args[0]);
        Path basePath = Paths.get("/Users/paul/Library/CloudStorage/Dropbox/UNI/Dokumente/Projekte/_ECCO/LilyECCODemos/nachtwachezwei_brahms");

        EccoService service = new EccoService();
        RepositoryPopulationUtil util = new RepositoryPopulationUtil(service);

        int committed = util.populate(basePath, ".ecco", 74, 100);

        System.out.println("Done. Variants committed: " + committed);
    }
}


