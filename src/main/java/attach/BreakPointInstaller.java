package attach;

import java.util.List;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequestManager;

public class BreakPointInstaller {

	public static BreakPointInstaller instance = new BreakPointInstaller();

	private BreakPointInstaller() {

	}

	public boolean addBreakpoint(VirtualMachine vm, Method method) {
		return addBreakpoint(vm, method.location().declaringType().name(), method.name(), method.argumentTypeNames());
	}

	/**
	 * Add a breakpoint at a specified method on the given VM
	 * 
	 * @param vm         the VM
	 * @param className  the name of the class
	 * @param methodName the name of the method
	 * @return true iff the breakpoint is successfully added
	 */
	public boolean addBreakpoint(VirtualMachine vm, String className, String methodName) {
		return this.addBreakpoint(vm, className, methodName, null);
	}

	/**
	 * Add a breakpoint at a specified method on the given VM Precise the method argument type names in case there is multiple method having the same
	 * name
	 * 
	 * @param vm              the VM
	 * @param className       the name of the class where the method is situated
	 * @param methodName      the name of the method
	 * @param methodArguments name of all arguments type of the method in the declaration order
	 */
	public boolean addBreakpoint(VirtualMachine vm, String className, String methodName, List<String> methodArguments) {
		try {
			// Getting the EventRequestManager of the VirtualMachine
			EventRequestManager requestManager = vm.eventRequestManager();

			// Getting the method, adapting the research depending of the amount of information given
			Method method = findMethod(vm, className, methodName, methodArguments);

			// Getting the location of the method
			Location location = method.location();

			// Creating the breakpoint at the wanted location
			BreakpointRequest breakpointRequest = requestManager.createBreakpointRequest(location);
			breakpointRequest.enable(); // activate the breakpoint
			breakpointRequest.addCountFilter(1);

		} catch (Exception e) {
			return false;
		}
		return true;
	}

	/**
	 * Find a method matching the given characteristics in the Virtual Machine
	 * 
	 * @param vm              the Virtual Machine
	 * @param className       the name of the class where the method is situated
	 * @param methodName      name of the searched method
	 * @param methodArguments name of all arguments type of the method in the declaration order
	 * @return the method if one match
	 * @throws ClassNotLoadedException if no method matches the characteristics
	 */
	private Method findMethod(VirtualMachine vm, String className, String methodName, List<String> methodArguments) throws ClassNotLoadedException {
		// finding the class
		// TODO can only find classes in the JDK
		List<ReferenceType> classes = vm.classesByName(className);
		if (classes.isEmpty()) {
			throw new IllegalArgumentException("Class not found : " + className);
		}
		ClassType classType = (ClassType) classes.get(0);

		// getting all the methods with the searched name in the class
		List<Method> allMethods = classType.methodsByName(methodName);

		// if no method found throw an exception
		if (allMethods.isEmpty()) {
			throw new IllegalArgumentException("No method named " + methodName + " in class " + className);
		}

		// if no arguments given, either we only have one method found and that's fine
		// or we have multiple results, we don't make a random choice, we just throw an exception
		if (methodArguments == null) {
			if (allMethods.size() != 1) {
				throw new IllegalArgumentException("Multiple methods named " + methodName + " in class " + className);
			}
			return allMethods.get(0);
		}

		// if we have multiple methods and given arguments types, we search if one correspond
		for (Method m : allMethods) {
			List<Type> paramTypes = m.argumentTypes();
			// if not the same number of types just pass this method
			if (paramTypes.size() != methodArguments.size()) {
				continue;
			}
			// starting on the hypothesis that we found the method, and trying to invalidate it
			boolean matches = true;
			for (int i = 0; i < paramTypes.size(); i++) {
				if (!paramTypes.get(i).name().equals(methodArguments.get(i))) {
					matches = false;
					break;
				}
			}
			// if we can't invalidate the method, then we found it
			if (matches) {
				return m;
			}
		}
		// if we got here, then no method have been found
		throw new IllegalArgumentException("No method named " + methodName + " in class " + className + " with argument types: " + methodArguments);
	}

}
