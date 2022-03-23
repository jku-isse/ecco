package test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import at.jku.isse.ecco.importer.TraceImporter;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.core.MemAssociation;
import at.jku.isse.ecco.storage.mem.repository.MemRepository;

public class ConditionParsingTest {
	
	@Test
	public void setConditionTest() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String condition = "((A & !X & cd & Bxy) | (A & !y & cd & Bxy) | (A & !z & cd & Bxy) | (A & !X & Bxy & with) | (A & !y & Bxy & with) | (A & !z & Bxy & with))";
		String expected = "(A && cd && Bxy && !X) || (A && Bxy && with && !X) || (A && Bxy && with && !y) || (A && Bxy && with && !z) || (A && cd && Bxy && !y) || (A && cd && Bxy && !z)";
		Method method = TraceImporter.class.getDeclaredMethod("setCondition", String.class, Repository.Op.class, MemAssociation.class);
		method.setAccessible(true);
		MemAssociation result = new MemAssociation();
		Repository.Op rep = new MemRepository();
		method.invoke(null, condition, rep, result);
		Assert.assertEquals(expected, result.computeCondition().getPreprocessorConditionString());
	}
	
	@Test
	public void toDnfTest() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String expected = "((A & !X & cd & Bxy) | (A & !y & cd & Bxy) | (A & !z & cd & Bxy) | (A & !X & Bxy & with) | (A & !y & Bxy & with) | (A & !z & Bxy & with))";
		String condition = "(A &&    Bxy &&( cd||with&&(A||B))) && (!X || !(z && y))  ";
		Method method = TraceImporter.class.getDeclaredMethod("parseCondition", String.class);
		method.setAccessible(true);
		String result = (String) method.invoke(null, condition);
		Assert.assertEquals(expected, result);
	}
}
