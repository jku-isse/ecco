package at.jku.isse.ecco.dao;

public interface SettingsDao {

	public int loadMaxOrder();

	public void storeMaxOrder(int maxOrder);

	public String loadCommitter();

	public void storeCommitter(String committer);

}
