package test;

import org.junit.Assert;
import org.junit.Test;

public class ConditionParsingTest {
	
	@Test
	public void splitTest() {
		String[] expected = {"", "A", "Bxy", "", "cd", "with space"};
		String condition = "(A &&    Bxy &&( cd||with space)) ";
		String[] features = condition.split("\\s*(&&|\\|\\||\\(|\\))\\s*");
		Assert.assertArrayEquals(expected, features);
	}
}
