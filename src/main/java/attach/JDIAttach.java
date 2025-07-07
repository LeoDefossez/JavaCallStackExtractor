package attach;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;

import extractors.StackFrameExtractor;

import java.io.IOException;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JDIAttach {

	public static void main(String[] args) throws Exception {
		// setting the variable that could become argument of the program
		String host = "localhost";
		String port1 = "5005";
		String port2 = "5006";

		String threadName = "main"; // name of the method creating the callStack

		// all informations of the method where a breakpoint should be added
		String className = "java.lang.Runtime";
		String methodName = "exec";
		List<String> methodArguments = Arrays.asList("java.lang.String"); // can be null if there's no name repetition

		VirtualMachine vm1 = attachToJVM(host, port1);
		VirtualMachine vm2 = attachToJVM(host, port2);

		// finding all the lines on the call stack to make an analysis on every line
		// this permit to know changes on ObjectReferences between the call stack's lines
		List<Method> allFramesMethods = getAllFramesMethods(vm1, className, methodName, methodArguments, threadName);
		
		Iterator<Method> it = allFramesMethods.iterator();
		
		addNextValidBreakpoint(vm2, it);
		//extract the call stack and give the breakpoints to add
		extractCallStack(vm2, threadName, it);
	}

	private static List<Method> getAllFramesMethods(VirtualMachine vm, String className, String methodName, List<String> methodArguments,
			String threadName) throws IllegalConnectorArgumentsException, IOException, IncompatibleThreadStateException {
		List<Method> allFramesMethods = new ArrayList<>();

		// adding the breakpoint
		BreakPointInstaller.instance.addBreakpoint(vm, className, methodName, methodArguments);

		// Searching for the wanted thread
		ThreadReference main = collectThread(threadName, vm);

		// waiting for the thread to either finish or stop at a breakpoint
		if (!encounterABreakpoint(main)) {
			throw new IllegalStateException("Thread has not encounter a breakpoint");
		}

		for (StackFrame frame : main.frames()) {
			allFramesMethods.add(frame.location().method());
		}

		return allFramesMethods;
	}

	/**
	 * Extract the call stack on the searched VM starting form the given thread and stopping at the method described
	 * 
	 * @param host            name of the host
	 * @param port            address of the VM
	 * @param className       the name of the class where the method is situated
	 * @param methodName      name of the searched method
	 * @param methodArguments name of all arguments of the method in the declaration order
	 * @param threadName      name of the thread to study
	 * @param iterator 
	 * @throws IOException                        when unable to attach to a VM
	 * @throws IllegalConnectorArgumentsException if no connector socket can be used to attach to the VM
	 * @throws IncompatibleThreadStateException
	 */
	private static void extractCallStack(VirtualMachine vm, String threadName, Iterator<Method> iterator)
			throws IOException, IllegalConnectorArgumentsException, IncompatibleThreadStateException {

		// Searching for the wanted thread
		ThreadReference main = collectThread(threadName, vm);

		int i = 0;
		while (encounterABreakpoint(main)) {
			// extract the frame
			while (i < main.frameCount()) {

				System.out.println("---- Line " + i + " of the call stack ----");
				//(new StackFrameExtractor()).extract(main.frames().get(i));
				
				i++;
			}
			//try adding the next possible breakpoint
			addNextValidBreakpoint(vm, iterator);
		}

		vm.dispose(); // properly disconnecting
	}

	private static void addNextValidBreakpoint(VirtualMachine vm, Iterator<Method> iterator) {
		while(iterator.hasNext() & BreakPointInstaller.instance.addBreakpoint(vm,iterator.next())) {
			//TODO try removing this loop and only adding possible breakpoints
		}
	}

	private static boolean encounterABreakpoint(ThreadReference main) {

		// resuming the process of the main
		main.resume();

		while (!(main.status() == ThreadReference.THREAD_STATUS_ZOMBIE || main.isAtBreakpoint())) {
			try {
				TimeUnit.MILLISECONDS.sleep(5);
			} catch (InterruptedException e) {
				// No need to take note of this exception
			}
		}
		return main.isAtBreakpoint();
	}

	private static ThreadReference collectThread(String threadName, VirtualMachine vm) {
		for (ThreadReference thread : vm.allThreads()) {
			if (thread.name().equals(threadName)) {
				return thread;
			}
		}
		// should not come here if the thread exists
		throw new IllegalStateException("No thread nammed " + threadName + "was found");

	}

	/**
	 * Find the Virtual Machine to attach this program to
	 * 
	 * @param host name of the host
	 * @param port address of the VM
	 * @return the Virtual Machine if one found
	 * @throws IOException                        when unable to attach.
	 * @throws IllegalConnectorArgumentsException if no connector socket can be used.
	 */
	public static VirtualMachine attachToJVM(String host, String port) throws IllegalConnectorArgumentsException, IOException {
		VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
		AttachingConnector connector = null;

		// Searching for the connector socket
		for (AttachingConnector ac : vmm.attachingConnectors()) {
			if (ac.name().equals("com.sun.jdi.SocketAttach")) {
				connector = ac;
				break;
			}
		}
		if (connector == null) {
			throw new IllegalStateException("No connector socket found");
		}

		// Configure the arguments
		Map<String, Connector.Argument> arguments = connector.defaultArguments();
		arguments.get("hostname").setValue(host);
		arguments.get("port").setValue(port); // need to correspond to the JVM address

		// Connect to the JVM
		try {
			return connector.attach(arguments);
		} catch (ConnectException e) {
			throw new IllegalStateException("Connection to the JVM refused, maybe check that the adresses are corresponding");
		}

	}

}
