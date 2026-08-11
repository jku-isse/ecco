package at.jku.cdl.ecco.adapter.java;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import at.jku.cdl.ecco.adapter.java.view.JavaCodeViewer;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.Set;

public class JavaASTModule extends AbstractModule {

	@Override
	protected void configure() {
		final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactReader<Path, Set<Node.Op>>>() {
				});
		readerMultibinder.addBinding().to(JavaASTReader.class);

		final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactWriter<Set<Node>, Path>>(){
		});
		writerMultibinder.addBinding().to(JavaASTWriter.class);

		if (!Boolean.getBoolean("ecco.headless")) {
			final Multibinder<AssociationInfoArtifactViewer> assInfoMultiBinder = Multibinder.newSetBinder(binder(),
					new TypeLiteral<>() {
					});
			assInfoMultiBinder.addBinding().to(JavaCodeViewer.class);
		}
	}

}
