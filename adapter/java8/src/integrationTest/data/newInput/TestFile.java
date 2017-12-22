package at.jku.isse.ecco.adapter.java.jdtast;

import com.google.common.base.Function;
import com.sun.istack.internal.NotNull;

import javax.xml.bind.annotation.XmlRootElement;
import java.beans.Transient;
import java.util.Collection;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

@XmlRootElement
public abstract class TestFile implements AutoCloseable {


    private @NotNull
    String m = "";

    @Transient
    protected abstract int foo();

    private void test() {
        @NotNull final String s = "";
        super.field;
        super.method();
        this.foo();
        String m = this.m;
        final Function<Object, String> aNew = super::foo;
        final IntSupplier a = this::foo;
        Function<Collection<String>, Stream<String>> wow = Collection::stream;
    }
}
