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
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MistakeCreatorTest {

    @Test
    public void formulaSyntaxTest(){
        FormulaFactory formulaFactory = new FormulaFactory();

        Formula simpleFormula = LogicUtils.parseString(formulaFactory, "A");
        System.out.println(simpleFormula.toString());

        Formula conjunction = LogicUtils.parseString(formulaFactory, "A & B");
        System.out.println(conjunction.toString());

        Formula disjunction = LogicUtils.parseString(formulaFactory, "A | B");
        System.out.println(disjunction.toString());

        Formula negation = LogicUtils.parseString(formulaFactory, "~A");
        System.out.println(negation.toString());

        Formula tautology = LogicUtils.parseString(formulaFactory, "$true");
        System.out.println(tautology.toString());
    }

    @Test
    public void formulaConjunctionTest(){
        FormulaFactory formulaFactory = new FormulaFactory();
        Formula disjunction = LogicUtils.parseString(formulaFactory, "A | B");
        Formula simpleFormula = LogicUtils.parseString(formulaFactory, "C");
        Formula conjunction = formulaFactory.and(disjunction, simpleFormula);
        System.out.println(conjunction);
    }


    @Test
    public void conditionSwapperSwapsCondition(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        MockFeatureTrace trace2 = new MockFeatureTrace("A & B");
        MockFeatureTrace trace3 = new MockFeatureTrace("A | B");
        Collection<FeatureTrace> traceCollection = new LinkedList<>();
        traceCollection.add(trace1);
        traceCollection.add(trace2);
        traceCollection.add(trace3);
        MockRepository repo = new MockRepository(traceCollection);

        ConditionSwapper conditionSwapper = new ConditionSwapper();
        conditionSwapper.init(repo);

        conditionSwapper.createMistake(trace1);
        assertNotEquals("A & B", trace1.getUserConditionString());
    }

    @Test
    public void conditionSwapperCantSwapIfAllConditionsAreTheSame(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        MockFeatureTrace trace2 = new MockFeatureTrace("A & B");
        MockFeatureTrace trace3 = new MockFeatureTrace("A & B");
        Collection<FeatureTrace> traceCollection = new LinkedList<>();
        traceCollection.add(trace1);
        traceCollection.add(trace2);
        traceCollection.add(trace3);
        MockRepository repo = new MockRepository(traceCollection);

        ConditionSwapper conditionSwapper = new ConditionSwapper();
        conditionSwapper.init(repo);

        assertThrows(RuntimeException.class, () -> conditionSwapper.createMistake(trace1));
    }

    @Test
    public void conditionSwapperThrowsExceptionWhenTryingToCreateMistakeWithoutInit(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        ConditionSwapper conditionSwapper = new ConditionSwapper();
        assertThrows(RuntimeException.class, () -> conditionSwapper.createMistake(trace1));
    }


    @Test
    public void conjugatorCreatesBiggerConjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        String[] features = {"A", "B", "C"};
        Conjugator conjugator = new Conjugator(List.of(features));
        conjugator.init(null);
        conjugator.createMistake(trace1);
        assertEquals("A & B & C", trace1.getUserConditionString());
    }

    @Test
    public void conjugatorCreatesConjunctionFromDisjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A | B");
        String[] features = {"A", "B", "C"};
        Conjugator conjugator = new Conjugator(List.of(features));
        conjugator.init(null);
        conjugator.createMistake(trace1);
        assertEquals("(A | B) & C", trace1.getUserConditionString());
    }

    @Test
    public void conjugatorWorksWithoutInit(){
        MockFeatureTrace trace1 = new MockFeatureTrace("~A");
        String[] features = {"A", "B"};
        Conjugator conjugator = new Conjugator(List.of(features));
        conjugator.createMistake(trace1);
        assertEquals("~A & B", trace1.getUserConditionString());
    }

    @Test
    public void conjugatorThrowsExceptionIfThereAreNotEnoughFeatures(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A | B");
        String[] features = {"A", "B"};
        Conjugator conjugator = new Conjugator(List.of(features));
        conjugator.init(null);
        assertThrows(RuntimeException.class, () -> conjugator.createMistake(trace1));
    }

    @Test
    public void featureSwitcherSwitchesFeature(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A");
        String[] features = {"A", "B"};
        FeatureSwitcher featureSwitcher = new FeatureSwitcher(List.of(features));
        featureSwitcher.init(null);
        featureSwitcher.createMistake(trace1);
        assertEquals("B", trace1.getUserConditionString());
    }

    @Test
    public void featureSwitcherSwitchesFeatureInNegation(){
        MockFeatureTrace trace1 = new MockFeatureTrace("~A");
        String[] features = {"A", "B"};
        FeatureSwitcher featureSwitcher = new FeatureSwitcher(List.of(features));
        featureSwitcher.init(null);
        featureSwitcher.createMistake(trace1);
        assertEquals("~B", trace1.getUserConditionString());
    }

    @Test
    public void featureSwitcherWorksWithoutInit(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        String[] features = {"A", "B", "C"};
        FeatureSwitcher featureSwitcher = new FeatureSwitcher(List.of(features));
        featureSwitcher.createMistake(trace1);
        assertTrue(trace1.getUserConditionString().equals("C & B") || trace1.getUserConditionString().equals("A & C"));
    }

    @Test
    public void featureSwitcherThrowsExceptionIfThereAreNoOtherFeatures(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        String[] features = {"A", "B"};
        FeatureSwitcher featureSwitcher = new FeatureSwitcher(List.of(features));
        assertThrows(RuntimeException.class, () -> featureSwitcher.createMistake(trace1));
    }

    @Test
    public void featureSwitcherThrowsExceptionIfThereAreNoFeaturesInTheCondition(){
        MockFeatureTrace trace1 = new MockFeatureTrace("$true");
        String[] features = {"A", "B"};
        FeatureSwitcher featureSwitcher = new FeatureSwitcher(List.of(features));
        assertThrows(RuntimeException.class, () -> featureSwitcher.createMistake(trace1));
    }

    @Test
    public void operatorSwapperSwapsConjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        OperatorSwapper operatorSwapper = new OperatorSwapper();
        operatorSwapper.init(null);
        operatorSwapper.createMistake(trace1);
        assertEquals("A | B", trace1.getUserConditionString());
    }

    @Test
    public void operatorSwapperSwapsDisjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A | B");
        OperatorSwapper operatorSwapper = new OperatorSwapper();
        operatorSwapper.init(null);
        operatorSwapper.createMistake(trace1);
        assertEquals("A & B", trace1.getUserConditionString());
    }

    @Test
    public void operatorSwapperWorksWithoutInit(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A | B | C");
        OperatorSwapper operatorSwapper = new OperatorSwapper();
        operatorSwapper.createMistake(trace1);
        assertEquals("A & B | C", trace1.getUserConditionString());
    }

    @Test
    public void operatorSwapperThrowsExceptionWhenThereAreNoOperators(){
        MockFeatureTrace trace1 = new MockFeatureTrace("~A");
        OperatorSwapper operatorSwapper = new OperatorSwapper();
        assertThrows(RuntimeException.class, () -> operatorSwapper.createMistake(trace1));
    }

    @Test
    public void unconjugatorRemovesConjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        Unconjugator unconjugator = new Unconjugator();
        unconjugator.init(null);
        unconjugator.createMistake(trace1);
        assertTrue(trace1.getUserConditionString().equals("A & $true") || trace1.getUserConditionString().equals("$true & B"));
    }

    @Test
    public void unconjugatorWorksWithoutInit(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B");
        Unconjugator unconjugator = new Unconjugator();
        unconjugator.createMistake(trace1);
        assertTrue(trace1.getUserConditionString().equals("A & $true") || trace1.getUserConditionString().equals("$true & B"));
    }

    @Test
    public void unconjugatorThrowsExceptionForNegation(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & ~B");
        Unconjugator unconjugator = new Unconjugator();
        unconjugator.init(null);
        assertThrows(RuntimeException.class, () -> unconjugator.createMistake(trace1));
    }

    @Test
    public void unconjugatorThrowsExceptionForDisjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A & B | C");
        Unconjugator unconjugator = new Unconjugator();
        unconjugator.init(null);
        assertThrows(RuntimeException.class, () -> unconjugator.createMistake(trace1));
    }

    @Test
    public void unconjugatorThrowsExceptionForMissingConjunction(){
        MockFeatureTrace trace1 = new MockFeatureTrace("A | C");
        Unconjugator unconjugator = new Unconjugator();
        unconjugator.init(null);
        assertThrows(RuntimeException.class, () -> unconjugator.createMistake(trace1));
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
        FeatureSwitcher featureSwitcher = new FeatureSwitcher(List.of(features));
        MistakeCreator mistakeCreator = new MistakeCreator(featureSwitcher);
        mistakeCreator.createMistakePercentage(repo, 40);

        Collection<String> conditions = repo.getFeatureTraces().stream().map(FeatureTrace::getUserConditionString).toList();
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
        FeatureSwitcher featureSwitcher = new FeatureSwitcher(List.of(features));
        MistakeCreator mistakeCreator = new MistakeCreator(featureSwitcher);
        mistakeCreator.createMistakePercentage(repo, 70);

        Collection<String> conditions = repo.getFeatureTraces().stream().map(FeatureTrace::getUserConditionString).toList();
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
        FeatureSwitcher featureSwitcher = new FeatureSwitcher(List.of(features));
        MistakeCreator mistakeCreator = new MistakeCreator(featureSwitcher);
        assertThrows(RuntimeException.class, () -> mistakeCreator.createMistakePercentage(repo, 20));
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
        FeatureSwitcher featureSwitcher = new FeatureSwitcher(List.of(features));
        MistakeCreator mistakeCreator = new MistakeCreator(featureSwitcher);
        assertThrows(RuntimeException.class, () -> mistakeCreator.createMistakePercentage(repo, -20));
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
        FeatureSwitcher featureSwitcher = new FeatureSwitcher(List.of(features));
        MistakeCreator mistakeCreator = new MistakeCreator(featureSwitcher);
        assertThrows(RuntimeException.class, () -> mistakeCreator.createMistakePercentage(repo, 150));
    }

    static class MockFeatureTrace implements FeatureTrace {
        private String userCondition;
        public MockFeatureTrace(String userCondition){
            this.userCondition = userCondition;
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
        public boolean containsUserCondition() {
            return false;
        }

        @Override
        public void setDiffCondition(String diffConditionString) {

        }

        @Override
        public void setUserCondition(String userConditionString) {
            this.userCondition = userConditionString;
        }

        @Override
        public void addUserCondition(String userCondition) {

        }

        @Override
        public void addDiffCondition(String diffCondition) {

        }

        @Override
        public void buildUserConditionConjunction(String newCondition) {

        }

        @Override
        public String getUserConditionString() {
            return this.userCondition;
        }

        @Override
        public String getDiffConditionString() {
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
        public void mergeFeatureTraceTree(Node.Op root) {

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
        public Node.Op fuseAssociationsWithFeatureTraces() {
            return null;
        }

        @Override
        public void removeFeatureTracePercentage(int percentage) {

        }

        @Override
        public Module getModule(Feature[] pos, Feature[] neg) {
            return null;
        }

        @Override
        public Module addModule(Feature[] pos, Feature[] neg) {
            return null;
        }

        @Override
        public Node.Op getFeatureTree() {
            return null;
        }
    }
}
