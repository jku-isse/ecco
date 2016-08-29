package at.jku.isse.ecco.core;

public interface Remote {

	public enum Type {
		LOCAL, REMOTE;
	}


	public String getName();

	public void setName(String name);


	public String getAddress();

	public void setAddress(String address);


	public Type getType();

	public void setType(Type type);

}
