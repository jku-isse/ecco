package at.jku.isse.ecco.adapter.image;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Set;

public class ImageModule extends AbstractModule {

	@Override
	protected void configure() {
		final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactReader<Path, Set<Node.Op>>>() {
				});
		readerMultibinder.addBinding().to(ImageReader.class);

		final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactWriter<Set<Node>, Path>>() {
				});
		writerMultibinder.addBinding().to(ImageFileWriter.class);

		final Multibinder<ArtifactViewer> viewerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactViewer>() {
				});
		viewerMultibinder.addBinding().to(ImageViewer.class);

		// separate multibinder set, same class - see ImageViewer's own javadoc: without this,
		// KnowledgeGraphView's hover/detached association preview (which looks specifically in this
		// set, not the plain ArtifactViewer one above) silently fell back to a bare label for every
		// image-backed association, unlike every code-file adapter's viewer.
		final Multibinder<AssociationInfoArtifactViewer> assInfoViewerMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<AssociationInfoArtifactViewer>() {
				});
		assInfoViewerMultibinder.addBinding().to(ImageViewer.class);


		final Multibinder<ArtifactWriter<Set<Node>, BufferedImage>> awtImageWriterMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactWriter<Set<Node>, BufferedImage>>() {
				});
		awtImageWriterMultibinder.addBinding().to(AwtImageWriter.class);

		final Multibinder<ArtifactWriter<Set<Node>, Image>> fxImageWriterMultibinder = Multibinder.newSetBinder(binder(),
				new TypeLiteral<ArtifactWriter<Set<Node>, Image>>() {
				});
		fxImageWriterMultibinder.addBinding().to(FxImageWriter.class);
	}

}
