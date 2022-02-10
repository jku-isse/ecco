package at.jku.isse.ecco.web.domain.model;

public class ModulesPerOrder {

    private int moduleOrder;
    private int numberOfModules;

    public ModulesPerOrder(int moduleOrder, int numberOfModules) {
        this.moduleOrder = moduleOrder;
        this.numberOfModules = numberOfModules;
    }

    public ModulesPerOrder() {

    }

    public int getNumberOfModules() {
        return numberOfModules;
    }

    public void setNumberOfModules(int numberOfModules) {
        this.numberOfModules = numberOfModules;
    }

    public int getModuleOrder() {
        return moduleOrder;
    }

    public void setModuleOrder(int moduleOrder) {
        this.moduleOrder = moduleOrder;
    }
}
