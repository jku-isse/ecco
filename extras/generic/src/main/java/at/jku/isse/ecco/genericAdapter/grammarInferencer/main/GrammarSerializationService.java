package at.jku.isse.ecco.genericAdapter.grammarInferencer.main;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminalFactory;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Symbol;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.ParameterSettings;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.Node;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data.NonTerminalNode;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Michael Jahn
 */
public class GrammarSerializationService {


    private static final String CLASS_META_KEY = "CLASS_META_KEY";

    private final Gson baseStructureGson = new GsonBuilder()
            .registerTypeAdapter(Node.class, new PropertyBasedInterfaceMarshal())
            .create();

    private final Gson nonTerminalsGson;
    private final Gson parsedSamplesGson = new GsonBuilder().create();

    public GrammarSerializationService() {
        GsonBuilder nonTerminalGsonBuilder = new GsonBuilder()
                .setExclusionStrategies(new NonTerminalExclustion())
                .registerTypeAdapter(Symbol.class, new PropertyBasedInterfaceMarshal());

        nonTerminalsGson = nonTerminalGsonBuilder.create();
    }

    public String serializeBaseStructures(List<NonTerminalNode> baseStructure) {
        return baseStructureGson.toJson(baseStructure);
    }

    public List<NonTerminalNode> deserializeBaseStructures(String json) {
        return baseStructureGson.fromJson(json, new TypeToken<List<NonTerminalNode>>(){}.getType());
    }

    public String serializeNonTerminalMap(Map<String, NonTerminal> nonTerminalMap) {

        Map<String, String> origValuesMap = new HashMap<>();

        NonTerminalFactory.clearSnapshotGrammar();

        Map<String, NonTerminal> nonTerminalMapCopy = new HashMap<>();
        for (Map.Entry<String, NonTerminal> nonTerminalEntry : nonTerminalMap.entrySet()) {
            origValuesMap.put(nonTerminalEntry.getKey(), nonTerminalEntry.getValue().subTreeToString());
            nonTerminalMapCopy.put(nonTerminalEntry.getKey(), (NonTerminal) nonTerminalEntry.getValue().getDeepCopyForSnapshot());
        }

        Map<String, Set<NonTerminal>> nonTerminalMapList = new HashMap<>();
        for (Map.Entry<String, NonTerminal> nonTerminalEntry : nonTerminalMapCopy.entrySet()) {
            nonTerminalMapList.put(nonTerminalEntry.getKey(), nonTerminalEntry.getValue().getAllNonTerminalsRecursive());
        }

        for (Map.Entry<String, Set<NonTerminal>> nonTerminalSetEntry : nonTerminalMapList.entrySet()) {
            for (NonTerminal nonTerminal : nonTerminalSetEntry.getValue()) {
                for (Rule rule : nonTerminal.getRules()) {
                    rule.transformNonTerminalForSerialization();
                }
            }
        }

        for (Map.Entry<String, String> origEntry : origValuesMap.entrySet()) {
            if(!nonTerminalMap.get(origEntry.getKey()).subTreeToString().equals(origEntry.getValue())) {
                System.err.println("INTERNAL ERROR: serialization illegaly manipulated original nonTerminal values for " + origEntry.getKey() + "!");
                if(ParameterSettings.DEBUG_OUTPUT) {
                    System.err.println("Orig: " + origEntry.getValue());
                    System.err.println("Changed: " + nonTerminalMap.get(origEntry.getKey()).subTreeToString());
                }
            }
        }


        return nonTerminalsGson.toJson(nonTerminalMapList);
    }

    public Map<String, NonTerminal> deserializeNonTerminalMap(String json) {
        Map<String, Set<NonTerminal>> nonTerminalMapList = nonTerminalsGson.fromJson(json, new TypeToken<Map<String, Set<NonTerminal>>>(){}.getType());
        Map<String, NonTerminal> nonTerminalMap = new HashMap<>();

        // reset parent non terminal references
        for (Map.Entry<String, Set<NonTerminal>> nonTerminalSetEntry : nonTerminalMapList.entrySet()) {
            NonTerminal rootNonTerminal = null;
            Map<String, NonTerminal> allValidNonTerminals = new HashMap<>();
            nonTerminalSetEntry.getValue().stream().forEach(it -> allValidNonTerminals.put(it.getName(), it));
            for (NonTerminal nonTerminal : nonTerminalSetEntry.getValue()) {
                if(rootNonTerminal == null) {
                    rootNonTerminal = nonTerminal;
                }

                for (Rule rule : nonTerminal.getRules()) {
                    rule.setParentNonTerminal(nonTerminal);
                    rule.reTransformNonTerminalForSerialization(allValidNonTerminals);

                }
            }
            nonTerminalMap.put(nonTerminalSetEntry.getKey(), rootNonTerminal);
        }


        return nonTerminalMap;
    }

    public String serializeParsedSamples( Map<String, Set<String>> alreadyProcessedSamplesPerLabel) {
        return parsedSamplesGson.toJson(alreadyProcessedSamplesPerLabel);
    }

    public  Map<String, Set<String>> deserializeParsedSamples(String jsonText) {
        return parsedSamplesGson.fromJson(jsonText, new TypeToken<Map<String, Set<String>>>(){}.getType());
    }

    private class PropertyBasedInterfaceMarshal implements JsonSerializer<Object>, JsonDeserializer<Object> {


        @Override
        public Object deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                JsonObject jsonObj = json.getAsJsonObject();
                String className = jsonObj.get(CLASS_META_KEY).getAsString();
                try {
                    Class<?> clz = Class.forName(className);
                    return context.deserialize(json, clz);
                } catch (ClassNotFoundException e) {
                    throw new JsonParseException(e);
                }
        }

        @Override
        public JsonElement serialize(Object src, Type typeOfSrc, JsonSerializationContext context) {
                JsonElement jsonEle = context.serialize(src, src.getClass());
                jsonEle.getAsJsonObject().addProperty(CLASS_META_KEY,
                        src.getClass().getCanonicalName());
                return jsonEle;
        }
    }

    private class NonTerminalExclustion implements ExclusionStrategy {

        /**
         * @param f the field object that is under test
         * @return true if the field should be ignored; otherwise false
         */
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            if(f.getDeclaringClass().equals(Rule.class)) {
                if(f.getName().equals("parentNonTerminal")) return true;
            }
            return false;
        }

        /**
         * @param clazz the class object that is under test
         * @return true if the class should be ignored; otherwise false
         */
        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    }




}
