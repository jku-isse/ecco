package at.jku.isse.ecco.adapter.rust.data;

import java.io.BufferedWriter;
import java.io.IOException;

public interface RustWritable {
    /*
     * Writes the content of this ArtifactData to the given BufferedWriter.
     *
     * @param bw the BufferedWriter to write to
     * @throws IOException if an I/O error occurs
     */
    public void write(BufferedWriter bw) throws IOException;
}
