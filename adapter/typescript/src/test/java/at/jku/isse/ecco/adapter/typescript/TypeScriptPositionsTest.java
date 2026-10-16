package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.SourcePosition;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeScriptPositionsTest {

    @Test
    @Timeout(120)
    public void classesFunctionsAndEnumsWithTheirLines(@TempDir Path base) throws Exception {
        Files.writeString(base.resolve("main.ts"), """
                import { a } from './a'
                enum Color { Red, Green }
                export class Point {
                  norm(n: number): number {
                    return n
                  }
                }
                export function cssPostPlugin(config: string): string {
                  return config
                }
                const size = (s: string) => s.length
                """);
        TypeScriptReader reader = new TypeScriptReader(new SerEntityFactory());
        Node file = reader.read(base, new Path[]{Path.of("main.ts")}).iterator().next();

        assertEquals(List.of(
                        new SourcePosition("enum", "Color", 2, 2),
                        new SourcePosition("class", "Point", 3, 7),
                        new SourcePosition("function", "Point.norm", 4, 6),
                        new SourcePosition("function", "cssPostPlugin", 8, 10),
                        new SourcePosition("function", "size", 11, 11)),
                reader.positions(file).stream().sorted(Comparator.comparingInt(SourcePosition::startLine)).toList());
    }
}
