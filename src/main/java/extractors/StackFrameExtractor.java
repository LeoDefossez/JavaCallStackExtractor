package extractors;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassObjectReference;
import com.sun.jdi.Field;
import com.sun.jdi.InternalException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import com.sun.jdi.VoidValue;
import com.sun.jdi.ReferenceType;

//TODO reduce this class duplications
public class StackFrameExtractor {

	/**
	 * Used to indicates which Object has already been visited, to not visit again.
	 * By using its unique ID it should be possible to make a link to the one where
	 * the parsing system has developed the search.
	 */
	private Set<ObjectReference> visited;
	
	public StackFrameExtractor() {
		this.visited = new HashSet<ObjectReference>();
	}

	/**
	 * extract a frame, by parsing the method signature, its arguments, and its
	 * receiver
	 * 
	 * @param frame the frame to extract
	 */
	public void extract(StackFrame frame) {
		this.extractMethod(frame);
		this.extractArguments(frame);
		this.extractReceiver(frame);
	}

	/**
	 * Parsing the method signature used in the given frame
	 * 
	 * @param frame the frame to extract
	 */
	public void extractMethod(StackFrame frame) {
		Method method = frame.location().method();
		System.out.println("Method signature: " + method.name() + "(" + String.join(",", method.argumentTypeNames()) + ")");
	}

	/**
	 * Parsing all accessible arguments given in the method in this frame
	 * 
	 * @param frame the frame to extract
	 */
	public void extractArguments(StackFrame frame) {
		System.out.println("Method arguments values : ");

		// getting the method associated to this frame
		Method method = frame.location().method();

		// arguments can sometimes not be accessible, if that's the case, stop here
		Iterator<Value> argumentsValueIterator;
		try {
			argumentsValueIterator = frame.getArgumentValues().iterator();
		} catch (InternalException e) {
			System.out.println("[Not Accessible]");
			return;
		}
		Iterator<String> namesIterator = method.argumentTypeNames().iterator();

		while (namesIterator.hasNext()) {
			// Here we suppose that method.argumentTypeNames() and frame.getArgumentValues()
			// have the same numbers of items
			// With this supposition being always true, we can just check if one have next
			// and iterate in both
			System.out.print(namesIterator.next() + " = ");
			this.extractValueRecursive(argumentsValueIterator.next(), "");
		}

	}

	/**
	 * Parsing the receiver of this frame
	 * 
	 * @param frame the frame to extract
	 */
	public void extractReceiver(StackFrame frame) {
		System.out.println("Method receiver : ");
		this.extractValueRecursive(frame.thisObject(), "");
	}

	/**
	 * extract the given value recursively to make sure no information are lost in the process
	 * @param value the value to extract
	 * @param indent the indent to add to make human able to understand what happen //TODO should be removed after
	 */ 
	private void extractValueRecursive(Value value, String indent) {
		if (value == null) {
			System.out.println(indent + "null");
		} else if (value instanceof PrimitiveValue) {
			this.extractPrimitiveValue((PrimitiveValue) value, indent);
		} else if (value instanceof ObjectReference) {
			this.extractObjectReference((ObjectReference) value, indent);
		} else if (value instanceof VoidValue) {
			// TODO
			// implements this if needed
			throw new IllegalStateException("VoidValue encountered, parsing not yet implemented");
		} else {
			//in case there would be another type
			throw new IllegalStateException(
					"Unknown Value Type: " + value.type().name() + ", parsing not yet implemented for this type");
		}
	}
	
	/**
	 * extract given the primitive value
	 * @param value the primitiveValue to extract
	 * @param indent the indent to add to make human able to understand what happen //TODO should be removed after
	 */
	private void extractPrimitiveValue(PrimitiveValue value, String indent) {
		System.out.println(indent + value.type().name() + " = " + value.toString());
	}

	/**
	 * extract the given ObjectReference
	 * @param value the ObjectReference to extract
	 * @param indent the indent to add to make human able to understand what happen //TODO should be removed after
	 */
	private void extractObjectReference(ObjectReference value, String indent) {
		//TODO maybe we can add these object to visited ?
		if (value instanceof StringReference) {
			System.out.println(
					indent + "\"" + ((StringReference) value).value() + "\"" + "[ObjId:" + value.uniqueID() + "]");

		} else if (value instanceof ArrayReference) {
			ReferenceType type = value.referenceType();
			System.out.println(indent + type.name() + " [ObjId:" + value.uniqueID() + "] = ");
			
			//Parsing every value of the array
			List<Value> arrayValues = ((ArrayReference) value).getValues();
			if(arrayValues.size() == 0) {
				System.out.println("[Empty Array]");
			}
			for (int i = 0; i < arrayValues.size(); i++) {
				System.out.println(indent + "at: " + i + " = ");
				this.extractValueRecursive(arrayValues.get(i), indent + "  ");
			}

		} else if (value instanceof ClassObjectReference) {
			// using reflectedType because it is said to be more precise than referenceType
			this.extractAllFields(value, indent, ((ClassObjectReference) value).reflectedType());

		} else {
			this.extractAllFields(value, indent, value.referenceType());
		}

	}
	
	/**
	 * extract all the fields of an ObjectReference
	 * @param ref the ObjectReference having the fields to extract
	 * @param indent the indent to add to make human able to understand what happen //TODO should be removed after
	 * @param type the reference type of the ObjectReference
	 */
	private void extractAllFields(ObjectReference ref, String indent, ReferenceType type) {
		if (this.visited.contains(ref)) {
			System.out.println(indent + type.name() + "[ObjId:" + ref.uniqueID() + "]");
			return;
		}
		this.visited.add(ref);

		System.out.println(indent + type.name() + " [ObjId:" + ref.uniqueID() + "] = ");

		// Check if the class is prepared, if not trying to get any field will throw an
		// exception
		// TODO maybe there is a way to still get some information out of it, for the
		// non static fields ?
		if (!type.isPrepared()) {
			// Preparation involves creating the static fields for a class or interface and
			// initializing such fields to their default values
			return;
		}

		for (Field field : type.visibleFields()) {
			// TODO check if there is any difference using allFields instead of
			// visibleFields
			try {
				// TODO
				// We actually extract the static and final fields, should we?
				// it's potential information but could also be noise
				Value fieldValue = ref.getValue(field);
				System.out.println(indent + field.name() + " = ");
				this.extractValueRecursive(fieldValue, indent + "  ");

			} catch (IllegalArgumentException e) {
				// TODO Some fields are not valid, how is that possible?
			}
		}
	}

}
