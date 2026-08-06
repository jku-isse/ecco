package at.jku.isse.ecco.plugin.artifact.runtime;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import com.sun.jdi.request.ThreadStartRequest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

//import sun.jvm.hotspot.runtime.Thread;

/**
 * Created by thomas on 20.03.2016.
 */
public class VMObservation {
	VirtualMachine vm;
	AttachingConnector con;
	Map<String, Connector.Argument> args;
	EventRequestManager reqManager;
	List<StepRequest> listStr = new ArrayList<StepRequest>();
	ThreadStartRequest tsr = null;

	public VMObservation() {
		Iterator iter = Bootstrap.virtualMachineManager().allConnectors().iterator();
		while (iter.hasNext()) {
			Connector x = (Connector) iter.next();
			if (x.name().equals("com.sun.jdi.SocketAttach")) {
				con = (AttachingConnector) x;
				args = con.defaultArguments();
			}
		}
	}

	public boolean connect(String connectionString) {
		// retry several times
		for (int i = 0; i < 3; i++) {
			try {
				Map<String, Connector.Argument> arguments = con.defaultArguments();
				String port = connectionString.substring(connectionString.indexOf(":") + 1);
				arguments.get("port").setValue(port);
				String ip = connectionString.substring(0, connectionString.indexOf(":"));
				arguments.get("hostname").setValue(ip);

				vm = con.attach(arguments);
				reqManager = vm.eventRequestManager();
				return true;
			} catch (Exception e) {
			}
		}
		return false;
	}

	public void initializeObservation() {
		// just initialize ThreadStart Request - StepRequests are then automatically created and added
		tsr = reqManager.createThreadStartRequest();
		tsr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
	}

	public void enableEvents() {
		try {
			tsr.disable();
		} catch (Exception e) {
		}
		try {
			tsr.enable();
		} catch (Exception e) {
		}

		Iterator tempI = listStr.iterator();
		while (tempI.hasNext()) {
			StepRequest tempStr = (StepRequest) tempI.next();
			try {
				tempStr.disable();
			} catch (Exception e) {
				tempI.remove();
			}
			try {
				tempStr.enable();
			} catch (Exception e) {
				tempI.remove();
			}
		}
	}

	public List<RuntimeArtifactData> manageNextEvent() {
		List<RuntimeArtifactData> listRad = new ArrayList<RuntimeArtifactData>();
		RuntimeArtifactData rad;

		EventQueue q = vm.eventQueue();
		try {
			enableEvents();
			vm.resume();
			EventSet events = q.remove();
			while (events != null) {
				EventIterator iter = events.eventIterator();
				while (iter.hasNext()) {
					try {
						Event e = iter.next();
						if (e instanceof StepEvent) {
							try { // to avoid com.sun.jdi.AbsentInformationException
								rad = new RuntimeArtifactData(
										((StepEvent) e).location().sourceName(),
										((StepEvent) e).location().lineNumber());
								listRad.add(rad);
							} catch (Exception e1) {
							}
						} else if (e instanceof ThreadStartEvent) {
							try {
								StepRequest str = reqManager.createStepRequest(((ThreadStartEvent) e).thread(), StepRequest.STEP_LINE, StepRequest.STEP_INTO);
								str.setSuspendPolicy(EventRequest.SUSPEND_ALL);
								str.addClassExclusionFilter("java.*");
								str.addClassExclusionFilter("sun.*");
								str.addClassExclusionFilter("javax.*");
								str.addClassExclusionFilter("jdk.*");
								str.enable();
								listStr.add(str);
							} catch (Exception e1) {
							}
						}
					} catch (Exception eee) {
						eee.printStackTrace();
					}
				}

				//            enableEvents();
				events.resume();
				vm.resume();
				events = q.remove();
			}
		} catch (Exception ee) {
		}

		return listRad;
	}
}
