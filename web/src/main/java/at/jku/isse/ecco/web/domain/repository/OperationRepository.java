package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.web.domain.model.CloseOperationResponse;
import at.jku.isse.ecco.web.domain.model.OpenOperationResponse;
import at.jku.isse.ecco.web.domain.model.OperationResponse;
import at.jku.isse.ecco.web.domain.model.ReducedArtifactPlugin;
import at.jku.isse.ecco.web.rest.EccoApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collection;

public class OperationRepository extends AbstractRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationRepository.class);

    private EccoApplication eccoApplication;

    public OperationRepository() {
    }

    public OperationRepository(EccoApplication eccoApplication) {
        this.eccoApplication = eccoApplication;
    }

    /**
     * Ein Repository kann entweder an der repositoryDirectory initialisiert oder geööfnet werden, insofern sich
     * ein bereits erstelltes Repo in diesem Verzechnis befindet.
     * Es soll bei einem nicht-vorhandensein eines Repo in einem Verzeichnis dieses nicht erstellt werden, sondern es soll
     * ein Fehler geworfen werden, der besagt, dass das Repo unter diesem Verzechnis nicht existiert
     *
     * Umformung der Plugins im Bezug auf die Namen und der Beschreibungen ist nötig, da Jackson nicht weiß, wie er aus der Klasse ein JSON Object machen soll...
     * Error aus einem Test-Request:No serializer found for class at.jku.isse.ecco.adapter.file.FileModule and no properties discovered to create BeanSerializer (to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: at.jku.isse.ecco.web.domain.model.OpenOperationResponse["artifactPlugins"]->java.util.ArrayList[0]->at.jku.isse.ecco.adapter.file.FilePlugin["module"])
     *
     *
     * @param repositoryDirectory Das Verzeichnis, dass zum Ecco-Repo zeigt oder zeigen soll,
     *                            bspw. "Path/to/Repo/.ecco"
     * @param repositoryOperation Die Operation, die auf die repositoryDirectory ausgeführt werden soll,
     *                            bspw. Open oder Initialize
     * @return boolean
     */
    public OperationResponse doOpenCloseOperationOnRepository(String repositoryDirectory, String repositoryOperation) {
        switch (repositoryOperation) {
            case "OPEN":
                try {
                    this.eccoApplication.open(repositoryDirectory);
                    OpenOperationResponse openOperationResponse = new OpenOperationResponse();
                    openOperationResponse.setEccoServiceIsInitialized(this.eccoApplication.getEccoService().isInitialized());
                    Collection<ArtifactPlugin> artifactPluginCollection = this.eccoApplication.getEccoService().getArtifactPlugins();
                    ArrayList<ReducedArtifactPlugin> plugins = new ArrayList<>();
                    for (ArtifactPlugin plugin: artifactPluginCollection) {
                        plugins.add(new ReducedArtifactPlugin(plugin.getPluginId(), plugin.getName(), plugin.getDescription()));
                    }
                    openOperationResponse.setArtifactPlugins(plugins.toArray(new ReducedArtifactPlugin[0]));
                    return openOperationResponse;
                } catch (EccoException e) {
                    LOGGER.info(e.getMessage());
                    e.printStackTrace();
                    throw new NotFoundException();
                }
            case "CLOSE":
                try {
                    this.eccoApplication.close();
                    CloseOperationResponse closeOperationResponse = new CloseOperationResponse();
                    closeOperationResponse.setEccoServiceIsInitialized(this.eccoApplication.getEccoService().isInitialized());
                    return closeOperationResponse;
                } catch (EccoException e) {
                    LOGGER.info(e.getMessage());
                    e.printStackTrace();
                    throw new BadRequestException();
                }
            default:
                throw new NotAllowedException("Allowed repository operations are OPEN or INIT!");
        }
    }
}
