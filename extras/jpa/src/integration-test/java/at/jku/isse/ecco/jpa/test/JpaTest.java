package at.jku.isse.ecco.jpa.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.JpaFeature;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JpaTest {

	@Test(groups = {"integration", "jpa"})
	public void Jpa_Test() throws EccoException {
		Map<String, String> persistenceMap = new HashMap<>();
		persistenceMap.put("javax.persistence.jdbc.url", "jdbc:derby:/home/user/Desktop/jpa_test/simpleDb;create=true");
		persistenceMap.put("javax.persistence.jdbc.user", "username");
		persistenceMap.put("javax.persistence.jdbc.password", "password");
		persistenceMap.put("javax.persistence.jdbc.driver", "org.apache.derby.jdbc.EmbeddedDriver");

		EntityManagerFactory managerFactory = Persistence.createEntityManagerFactory("ecco", persistenceMap);
		EntityManager entityManager = managerFactory.createEntityManager();

		//Persistence.generateSchema("ecco", persistenceMap);






		entityManager.getTransaction().begin();
		Feature temp = new JpaFeature("AAA");
		entityManager.getTransaction().commit();



		Feature feature = new JpaFeature("f9");
		entityManager.getTransaction().begin();
		feature = entityManager.merge(feature);
		entityManager.getTransaction().commit();


		{
			//entityManager.getTransaction().begin();
			//List<Feature> features = entityManager.createQuery("from Feature").getResultList();
			CriteriaBuilder cb = entityManager.getCriteriaBuilder();
			CriteriaQuery<JpaFeature> cq = cb.createQuery(JpaFeature.class);
			Root<JpaFeature> rootEntry = cq.from(JpaFeature.class);
			CriteriaQuery<JpaFeature> all = cq.select(rootEntry);
			TypedQuery<JpaFeature> allQuery = entityManager.createQuery(all);
			List<JpaFeature> features = allQuery.getResultList();

			for (Iterator<JpaFeature> iterator = features.iterator(); iterator.hasNext(); ) {
				Feature tempFeature = iterator.next();
				System.out.println(tempFeature + ", " + tempFeature.getDescription());
			}
			//entityManager.getTransaction().commit();
		}

		entityManager.getTransaction().begin();
		feature.setDescription("blubb");
		entityManager.getTransaction().commit();


		{
			//entityManager.getTransaction().begin();
			//List<Feature> features = entityManager.createQuery("from Feature").getResultList();
			CriteriaBuilder cb = entityManager.getCriteriaBuilder();
			CriteriaQuery<JpaFeature> cq = cb.createQuery(JpaFeature.class);
			Root<JpaFeature> rootEntry = cq.from(JpaFeature.class);
			CriteriaQuery<JpaFeature> all = cq.select(rootEntry);
			TypedQuery<JpaFeature> allQuery = entityManager.createQuery(all);
			List<JpaFeature> features = allQuery.getResultList();

			for (Iterator<JpaFeature> iterator = features.iterator(); iterator.hasNext(); ) {
				Feature tempFeature = iterator.next();
				System.out.println(tempFeature + ", " + tempFeature.getDescription());
			}
		}






		//feature.setDescription("CCCC");
		entityManager.getTransaction().begin();
		feature.setDescription("CCCC");
		entityManager.getTransaction().rollback();

//		entityManager.getTransaction().begin();
//		entityManager.refresh(feature);
//		entityManager.getTransaction().commit();

		System.out.println("AAA: " + feature.getDescription());

	}


	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
	}

}
