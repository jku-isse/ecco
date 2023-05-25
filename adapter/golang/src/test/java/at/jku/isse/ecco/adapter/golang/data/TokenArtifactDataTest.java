package at.jku.isse.ecco.adapter.golang.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

public class TokenArtifactDataTest {
    @Test
    public void isInstanceOfArtifactData() {
        assertInstanceOf(ArtifactData.class, new TokenArtifactData("", 0, 1, 2));
    }

    @Test
    public void hasImmutableTokenData() throws NoSuchFieldException {
        final String expectedToken = "func";

        TokenArtifactData data = new TokenArtifactData(expectedToken, 0, 1, 2);
        String actualToken = data.getToken();
        int expectedRow = 1;
        int expectedColumn = 2;

        assertEquals(expectedToken, actualToken);
        assertEquals(expectedRow, data.getRow());
        assertEquals(expectedColumn, data.getColumn());

        Field tokenField = TokenArtifactData.class.getDeclaredField("token");
        assertTrue(Modifier.isFinal(tokenField.getModifiers()));
        Field rowField = TokenArtifactData.class.getDeclaredField("row");
        assertTrue(Modifier.isFinal(rowField.getModifiers()));
        Field columnField = TokenArtifactData.class.getDeclaredField("column");
        assertTrue(Modifier.isFinal(columnField.getModifiers()));
    }
}
