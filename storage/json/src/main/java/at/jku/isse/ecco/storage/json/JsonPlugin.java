package at.jku.isse.ecco.storage.json;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.StoragePlugin;
import at.jku.isse.ecco.storage.json.impl.entities.JsonArtifact;
import at.jku.isse.ecco.storage.json.impl.entities.JsonConfiguration;
import at.jku.isse.ecco.storage.json.impl.entities.JsonFeature;
import at.jku.isse.ecco.storage.json.impl.entities.JsonFeatureRevision;
import at.jku.isse.ecco.storage.mem.MemModule;
import at.jku.isse.ecco.storage.mem.core.MemAssociation;
import at.jku.isse.ecco.storage.mem.core.MemCommit;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import at.jku.isse.ecco.storage.mem.core.MemVariant;
import at.jku.isse.ecco.storage.mem.tree.MemNode;
import at.jku.isse.ecco.storage.mem.tree.MemRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import com.google.inject.Module;
import com.jsoniter.JsonIterator;
import com.jsoniter.output.EncodingMode;
import com.jsoniter.output.JsonStream;
import com.jsoniter.spi.DecodingMode;
import com.jsoniter.spi.JsoniterSpi;
import com.jsoniter.spi.TypeLiteral;

import java.util.ArrayList;
import java.util.Collection;

import static com.jsoniter.spi.JsoniterSpi.registerTypeDecoder;
import static com.jsoniter.spi.JsoniterSpi.registerTypeImplementation;

public class JsonPlugin extends StoragePlugin {

    public static String PLUGIN_ID = "at.jku.isse.ecco.storage.json";
    private JsonModule module = new JsonModule();

   public JsonPlugin() {
        JsonIterator.setMode(DecodingMode.DYNAMIC_MODE_AND_MATCH_FIELD_WITH_HASH);
        JsonStream.setMode(EncodingMode.DYNAMIC_MODE);
        //Register all possible interfaces!
        registerTypeImplementation(Configuration.class, JsonConfiguration.class);
        registerTypeImplementation(Remote.class, MemRemote.class);
        registerTypeImplementation(Variant.class, MemVariant.class);
        registerTypeImplementation(Artifact.Op.class, JsonArtifact.class);
        registerTypeImplementation(Commit.class, MemCommit.class);
        registerTypeImplementation(Association.Op.class, MemAssociation.Op.class);
        registerTypeImplementation(Feature.class, JsonFeature.class);
        registerTypeImplementation(at.jku.isse.ecco.module.Module.class, MemModule.class);
        registerTypeImplementation(RootNode.Op.class, MemRootNode.class);
        registerTypeImplementation(Node.Op.class, MemNode.class);
        registerTypeImplementation(Collection.class, ArrayList.class);
        registerTypeImplementation(FeatureRevision.class, JsonFeatureRevision.class);


        registerTypeDecoder(new TypeLiteral<Collection<Artifact.Op>>() {
        }, iter -> iter.read(ArrayList.class));


        // http://jsoniter.com/java-features.html#wrapper--unwrapper <-- Needed for ArtifactData
        //TODO Add performance: http://jsoniter.com/java-features.html#performance-is-optional
        //TODO Maybe create a own artifact factory
    }



    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public Module getModule() {
        return module;
    }

    @Override
    public String getName() {
        return JsonPlugin.class.getSimpleName();
    }

    @Override
    public String getDescription() {
        return "This is a JSON backend for ECCO.";
    }

}
