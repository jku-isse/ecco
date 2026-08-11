package at.jku.cdl.ecco.adapter.java;

import com.google.inject.Module;

import at.jku.isse.ecco.adapter.ArtifactPlugin;

public class JavaASTPlugin extends ArtifactPlugin {
	
	private JavaASTModule module = new JavaASTModule();

	@Override
	public String getPluginId() {
		return JavaASTPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return module;
	}

	@Override
	public String getName() {
		return "JavaASTPlugin";
	}

	@Override
	public String getDescription() {
		return "Plugin for Java AST using JavaParser";
	}

}
