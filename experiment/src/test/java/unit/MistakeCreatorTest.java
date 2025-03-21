package unit;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.experiment.mistake.*;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.maintree.building.MainTreeBuildingStrategy;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.logicng.formulas.Formula;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MistakeCreatorTest {

    @Test
    public void formulaSyntaxTest(){
        Formula simpleFormula = LogicUtils.parseString("A");
        System.out.println(simpleFormula.toString());

        Formula conjunction = LogicUtils.parseString("A & B");
        System.out.println(conjunction.toString());

        Formula disjunction = LogicUtils.parseString("A | B");
        System.out.println(disjunction.toString());

        Formula negation = LogicUtils.parseString("~A");
        System.out.println(negation.toString());

        Formula tautology = LogicUtils.parseString("$true");
        System.out.println(tautology.toString());
    }

    @Test
    public void formulaConjunctionTest(){
        Formula disjunction = LogicUtils.parseString("A | B");
        Formula simpleFormula = LogicUtils.parseString("C");
        Formula conjunction = FormulaFactoryProvider.getFormulaFactory().and(disjunction, simpleFormula);
        System.out.println(conjunction);
    }


    @Test
    public void swappedConditionSwapsCondition(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        MockFeatureTrace trace2 = new MockFeatureTrace("A & B");
        MockFeatureTrace trace3 = new MockFeatureTrace("A | B");
        Collection<FeatureTrace> traceCollection = new LinkedList<>();
        traceCollection.add(trace1);
        traceCollection.add(trace2);
        traceCollection.add(trace3);
        MockRepository repo = new MockRepository(traceCollection);

        SwappedCondition swappedCondition = new SwappedCondition();
        swappedCondition.init(repo);

        swappedCondition.createMistake(trace1);
        assertNotEquals("A & B", trace1.getProactiveConditionString());
    }

    @Test
    public void swappedConditionCantSwapIfAllConditionsAreTheSame(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        MockFeatureTrace trace2 = new MockFeatureTrace("A & B");
        MockFeatureTrace trace3 = new MockFeatureTrace("A & B");
        Collection<FeatureTrace> traceCollection = new LinkedList<>();
        traceCollection.add(trace1);
        traceCollection.add(trace2);
        traceCollection.add(trace3);
        MockRepository repo = new MockRepository(traceCollection);

        SwappedCondition swappedCondition = new SwappedCondition();
        swappedCondition.init(repo);

        assertThrows(RuntimeException.class, () -> swappedCondition.createMistake(trace1));
    }

    @Test
    public void swappedConditionThrowsExceptionWhenTryingToCreateMistakeWithoutInit(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        SwappedCondition swappedCondition = new SwappedCondition();
        assertThrows(RuntimeException.class, () -> swappedCondition.createMistake(trace1));
    }


    @Test
    public void erroneousConjunctionCreatesBiggerConjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        String[] features = {"A", "B", "C"};
        ErroneousConjunction erroneousConjunction = new ErroneousConjunction(List.of(features));
        erroneousConjunction.init(null);
        erroneousConjunction.createMistake(trace1);
        assertEquals("A & B & C", trace1.getProactiveConditionString());
    }

    @Test
    public void erroneousConjunctionCreatesConjunctionFromDisjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A | B");
        String[] features = {"A", "B", "C"};
        ErroneousConjunction erroneousConjunction = new ErroneousConjunction(List.of(features));
        erroneousConjunction.init(null);
        erroneousConjunction.createMistake(trace1);
        assertEquals("(A | B) & C", trace1.getProactiveConditionString());
    }

    @Test
    public void erroneousConjunctionWorksWithoutInit(){
        MockFeatureTrace trace1 = new MockFeatureTrace("~A");
        String[] features = {"A", "B"};
        ErroneousConjunction erroneousConjunction = new ErroneousConjunction(List.of(features));
        erroneousConjunction.createMistake(trace1);
        assertEquals("~A & B", trace1.getProactiveConditionString());
    }

    @Test
    public void erroneousConjunctionThrowsExceptionIfThereAreNotEnoughFeatures(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A | B");
        String[] features = {"A", "B"};
        ErroneousConjunction erroneousConjunction = new ErroneousConjunction(List.of(features));
        erroneousConjunction.init(null);
        assertThrows(RuntimeException.class, () -> erroneousConjunction.createMistake(trace1));
    }

    @Test
    public void swappedFeatureSwapsFeature(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A");
        String[] features = {"A", "B"};
        SwappedFeature swappedFeature = new SwappedFeature(List.of(features));
        swappedFeature.init(null);
        swappedFeature.createMistake(trace1);
        assertEquals("B", trace1.getProactiveConditionString());
    }

    @Test
    public void swappedFeatureSwapsFeatureInNegation(){
        MockFeatureTrace trace1 = new MockFeatureTrace("~A");
        String[] features = {"A", "B"};
        SwappedFeature swappedFeature = new SwappedFeature(List.of(features));
        swappedFeature.init(null);
        swappedFeature.createMistake(trace1);
        assertEquals("~B", trace1.getProactiveConditionString());
    }

    @Test
    public void swappedFeatureWorksWithoutInit(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        String[] features = {"A", "B", "C"};
        SwappedFeature swappedFeature = new SwappedFeature(List.of(features));
        swappedFeature.createMistake(trace1);
        assertTrue(trace1.getProactiveConditionString().equals("C & B") || trace1.getProactiveConditionString().equals("A & C"));
    }

    @Test
    public void swappedFeatureThrowsExceptionIfThereAreNoOtherFeatures(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        String[] features = {"A", "B"};
        SwappedFeature swappedFeature = new SwappedFeature(List.of(features));
        assertThrows(RuntimeException.class, () -> swappedFeature.createMistake(trace1));
    }

    @Test
    public void swappedFeatureThrowsExceptionIfThereAreNoFeaturesInTheCondition(){
        MockFeatureTrace trace1 = new MockFeatureTrace("$true");
        String[] features = {"A", "B"};
        SwappedFeature swappedFeature = new SwappedFeature(List.of(features));
        assertThrows(RuntimeException.class, () -> swappedFeature.createMistake(trace1));
    }

    @Test
    public void swappedOperatorSwapsConjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        SwappedOperator swappedOperator = new SwappedOperator();
        swappedOperator.init(null);
        swappedOperator.createMistake(trace1);
        assertEquals("A | B", trace1.getProactiveConditionString());
    }

    @Test
    public void swappedOperatorSwapsDisjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A | B");
        SwappedOperator swappedOperator = new SwappedOperator();
        swappedOperator.init(null);
        swappedOperator.createMistake(trace1);
        assertEquals("A & B", trace1.getProactiveConditionString());
    }

    @Test
    public void swappedOperatorWorksWithoutInit(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A | B | C");
        SwappedOperator swappedOperator = new SwappedOperator();
        swappedOperator.createMistake(trace1);
        assertEquals("A & B | C", trace1.getProactiveConditionString());
    }

    @Test
    public void swappedOperatorThrowsExceptionWhenThereAreNoOperators(){
        MockFeatureTrace trace1 = new MockFeatureTrace("~A");
        SwappedOperator swappedOperator = new SwappedOperator();
        assertThrows(RuntimeException.class, () -> swappedOperator.createMistake(trace1));
    }

    @Test
    public void missingConjunctionRemovesConjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        MissingConjunction missingConjunction = new MissingConjunction();
        missingConjunction.init(null);
        missingConjunction.createMistake(trace1);
        assertTrue(trace1.getProactiveConditionString().equals("A & $true") || trace1.getProactiveConditionString().equals("$true & B"));
    }

    @Test
    public void missingConjunctionWorksWithoutInit(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        MissingConjunction missingConjunction = new MissingConjunction();
        missingConjunction.createMistake(trace1);
        assertTrue(trace1.getProactiveConditionString().equals("A & $true") || trace1.getProactiveConditionString().equals("$true & B"));
    }

    @Test
    public void missingConjunctionThrowsExceptionForNegation(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & ~B");
        MissingConjunction missingConjunction = new MissingConjunction();
        missingConjunction.init(null);
        assertThrows(RuntimeException.class, () -> missingConjunction.createMistake(trace1));
    }

    @Test
    public void missingConjunctionThrowsExceptionForDisjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B | C");
        MissingConjunction missingConjunction = new MissingConjunction();
        missingConjunction.init(null);
        assertThrows(RuntimeException.class, () -> missingConjunction.createMistake(trace1));
    }

    @Test
    public void missingConjunctionThrowsExceptionForMissingConjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A | C");
        MissingConjunction missingConjunction = new MissingConjunction();
        missingConjunction.init(null);
        assertThrows(RuntimeException.class, () -> missingConjunction.createMistake(trace1));
    }

    @Test
    public void mistakeCreatorCreatesCorrectPercentage() {
        MockFeatureTrace trace1 = new MockFeatureTrace("A");
        MockFeatureTrace trace2 = new MockFeatureTrace("A");
        MockFeatureTrace trace3 = new MockFeatureTrace("A");
        MockFeatureTrace trace4 = new MockFeatureTrace("A");
        MockFeatureTrace trace5 = new MockFeatureTrace("A");
        Collection<FeatureTrace> traceCollection = new LinkedList<>();
        traceCollection.add(trace1);
        traceCollection.add(trace2);
        traceCollection.add(trace3);
        traceCollection.add(trace4);
        traceCollection.add(trace5);
        MockRepository repo = new MockRepository(traceCollection);
        String[] features = {"A", "B"};
        SwappedFeature swappedFeature = new SwappedFeature(List.of(features));
        MistakeCreator mistakeCreator = new MistakeCreator(swappedFeature);
        mistakeCreator.createMistakePercentage(repo, repo.getFeatureTraces(), 40);

        Collection<String> conditions = repo.getFeatureTraces().stream().map(FeatureTrace::getProactiveConditionString).toList();
        int unchanged = (int) conditions.stream().filter(s -> s.equals("A")).count();
        int changed = (int) conditions.stream().filter(s -> s.equals("B")).count();
        assertEquals(3, unchanged);
        assertEquals(2, changed);
    }

    @Test
    public void mistakeCreatorRoundsDown() {
        MockFeatureTrace trace1 = new MockFeatureTrace("A");
        MockFeatureTrace trace2 = new MockFeatureTrace("A");
        MockFeatureTrace trace3 = new MockFeatureTrace("A");
        MockFeatureTrace trace4 = new MockFeatureTrace("A");
        MockFeatureTrace trace5 = new MockFeatureTrace("A");
        Collection<FeatureTrace> traceCollection = new LinkedList<>();
        traceCollection.add(trace1);
        traceCollection.add(trace2);
        traceCollection.add(trace3);
        traceCollection.add(trace4);
        traceCollection.add(trace5);
        MockRepository repo = new MockRepository(traceCollection);
        String[] features = {"A", "B"};
        SwappedFeature swappedFeature = new SwappedFeature(List.of(features));
        MistakeCreator mistakeCreator = new MistakeCreator(swappedFeature);
        mistakeCreator.createMistakePercentage(repo, repo.getFeatureTraces(), 70);

        Collection<String> conditions = repo.getFeatureTraces().stream().map(FeatureTrace::getProactiveConditionString).toList();
        int unchanged = (int) conditions.stream().filter(s -> s.equals("A")).count();
        int changed = (int) conditions.stream().filter(s -> s.equals("B")).count();
        assertEquals(2, unchanged);
        assertEquals(3, changed);
    }

    @Test
    public void mistakeCreatorThrowsExceptionIfPercentageIsNotPossible() {
        MockFeatureTrace trace1 = new MockFeatureTrace("A");
        MockFeatureTrace trace2 = new MockFeatureTrace("A");
        MockFeatureTrace trace3 = new MockFeatureTrace("A");
        MockFeatureTrace trace4 = new MockFeatureTrace("A");
        MockFeatureTrace trace5 = new MockFeatureTrace("A");
        Collection<FeatureTrace> traceCollection = new LinkedList<>();
        traceCollection.add(trace1);
        traceCollection.add(trace2);
        traceCollection.add(trace3);
        traceCollection.add(trace4);
        traceCollection.add(trace5);
        MockRepository repo = new MockRepository(traceCollection);
        String[] features = {"A"};
        SwappedFeature swappedFeature = new SwappedFeature(List.of(features));
        MistakeCreator mistakeCreator = new MistakeCreator(swappedFeature);
        assertThrows(MistakeException.class, () -> mistakeCreator.createMistakePercentage(repo, repo.getFeatureTraces(), 20));
    }

    @Test
    public void mistakeCreatorThrowsExceptionIfPercentageSmallerZero() {
        MockFeatureTrace trace1 = new MockFeatureTrace("A");
        MockFeatureTrace trace2 = new MockFeatureTrace("A");
        MockFeatureTrace trace3 = new MockFeatureTrace("A");
        MockFeatureTrace trace4 = new MockFeatureTrace("A");
        MockFeatureTrace trace5 = new MockFeatureTrace("A");
        Collection<FeatureTrace> traceCollection = new LinkedList<>();
        traceCollection.add(trace1);
        traceCollection.add(trace2);
        traceCollection.add(trace3);
        traceCollection.add(trace4);
        traceCollection.add(trace5);
        MockRepository repo = new MockRepository(traceCollection);
        String[] features = {"A"};
        SwappedFeature swappedFeature = new SwappedFeature(List.of(features));
        MistakeCreator mistakeCreator = new MistakeCreator(swappedFeature);
        assertThrows(RuntimeException.class, () -> mistakeCreator.createMistakePercentage(repo, repo.getFeatureTraces(), -20));
    }

    @Test
    public void mistakeCreatorThrowsExceptionIfPercentageBiggerHundred() {
        MockFeatureTrace trace1 = new MockFeatureTrace("A");
        MockFeatureTrace trace2 = new MockFeatureTrace("A");
        MockFeatureTrace trace3 = new MockFeatureTrace("A");
        MockFeatureTrace trace4 = new MockFeatureTrace("A");
        MockFeatureTrace trace5 = new MockFeatureTrace("A");
        Collection<FeatureTrace> traceCollection = new LinkedList<>();
        traceCollection.add(trace1);
        traceCollection.add(trace2);
        traceCollection.add(trace3);
        traceCollection.add(trace4);
        traceCollection.add(trace5);
        MockRepository repo = new MockRepository(traceCollection);
        String[] features = {"A"};
        SwappedFeature swappedFeature = new SwappedFeature(List.of(features));
        MistakeCreator mistakeCreator = new MistakeCreator(swappedFeature);
        assertThrows(RuntimeException.class, () -> mistakeCreator.createMistakePercentage(repo, repo.getFeatureTraces(), 150));
    }

    static class MockFeatureTrace implements FeatureTrace {
        private String proactiveCondition;
        public MockFeatureTrace(String proactiveCondition){
            this.proactiveCondition = proactiveCondition;
        }

        @Override
        public boolean holds(Configuration configuration, EvaluationStrategy evaluationStrategy) {
            return false;
        }

        @Override
        public Node getNode() {
            return null;
        }

        @Override
        public void setNode(Node node) {

        }

        @Override
        public boolean containsProactiveCondition() {
            return false;
        }

        @Override
        public void setRetroactiveCondition(String retroactiveConditionString) {

        }

        @Override
        public void setProactiveCondition(String proactiveConditionString) {
            this.proactiveCondition = proactiveConditionString;
        }

        @Override
        public void addProactiveCondition(String proactiveCondition) {

        }

        @Override
        public void removeProactiveCondition() {

        }

        @Override
        public void addRetroactiveCondition(String retroactiveCondition) {

        }

        @Override
        public void buildProactiveConditionConjunction(String newCondition) {

        }

        @Override
        public String getProactiveConditionString() {
            return this.proactiveCondition;
        }

        @Override
        public String getRetroactiveConditionString() {
            return null;
        }

        @Override
        public void fuseFeatureTrace(FeatureTrace featureTrace) {

        }

        @Override
        public String getOverallConditionString(EvaluationStrategy evaluationStrategy) {
            return null;
        }
    }

    static class MockRepository implements Repository.Op {

        Collection<FeatureTrace> traces;

        public MockRepository(Collection<FeatureTrace> traces){
            this.traces = traces;
        }

        @Override
        public ArrayList<Variant> getVariants() {
            return null;
        }

        @Override
        public Variant getVariant(Configuration configuration) {
            return null;
        }

        @Override
        public Variant getVariant(String id) {
            return null;
        }

        @Override
        public Association getAssociation(String id) {
            return null;
        }

        @Override
        public ArrayList<Feature> getFeature() {
            return null;
        }

        @Override
        public void updateVariant(Variant variant, Configuration configuration, String name) {

        }

        @Override
        public Collection<Commit> getCommits() {
            return null;
        }

        @Override
        public void setCommits(Collection<Commit> commits) {

        }

        @Override
        public void addCommit(Commit commit) {

        }

        @Override
        public Collection<FeatureTrace> getFeatureTraces() {
            return this.traces;
        }

        @Override
        public void setMaintreeBuildingStrategy(MainTreeBuildingStrategy mainTreeBuildingStrategy) {

        }

        @Override
        public MainTreeBuildingStrategy getMainTreeBuildingStrategy() {
            return null;
        }

        @Override
        public void setEvaluationStrategy(EvaluationStrategy evaluationStrategy) {

        }

        @Override
        public EvaluationStrategy getEvaluationStrategy() {
            return null;
        }

        @Override
        public Collection<? extends Feature> getFeatures() {
            return null;
        }

        @Override
        public Collection<? extends Association.Op> getAssociations() {
            return null;
        }

        @Override
        public Collection<? extends Module> getModules(int order) {
            return null;
        }

        @Override
        public Feature getFeature(String id) {
            return null;
        }

        @Override
        public Feature getOrphanedFeature(String id, String name) {
            return null;
        }

        @Override
        public Feature addFeature(String id, String name) {
            return null;
        }

        @Override
        public void addVariant(Variant variant) {

        }

        @Override
        public void addAssociation(Association.Op association) {

        }

        @Override
        public void removeVariant(Variant variant) {

        }

        @Override
        public void removeAssociation(Association.Op association) {

        }

        @Override
        public Module getOrphanedModule(Feature[] posFeatures, Feature[] neg) {
            return null;
        }

        @Override
        public int getMaxOrder() {
            return 0;
        }

        @Override
        public void setMaxOrder(int maxOrder) {

        }

        @Override
        public EntityFactory getEntityFactory() {
            return null;
        }

        @Override
        public void buildMainTree() {

        }

        @Override
        public Node.Op getMainTree() {
            return null;
        }

        @Override
        public Module getModule(Feature[] pos, Feature[] neg) {
            return null;
        }

        @Override
        public Module addModule(Feature[] pos, Feature[] neg) {
            return null;
        }
    }
}
