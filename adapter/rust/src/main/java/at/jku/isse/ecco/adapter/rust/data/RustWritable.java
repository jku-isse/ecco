package at.jku.isse.ecco.adapter.rust.data;

import java.io.BufferedWriter;
import java.io.IOException;

public interface RustWritable {
    public void write(BufferedWriter bw) throws IOException;
}
