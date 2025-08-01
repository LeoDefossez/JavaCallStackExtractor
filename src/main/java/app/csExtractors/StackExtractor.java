package app.csExtractors;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import com.fasterxml.jackson.databind.JsonNode;
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

import app.logging.ILoggerFormat;
import app.logging.LoggerJson;
import app.logging.LoggerText;

import com.sun.jdi.ReferenceType;

/**
 * This class extract all the information of a given stack frame to a text file
 */
public class StackExtractor {

	public static Map<String, BiFunction<String, String, ILoggerFormat>> loggerChoice = registerAllLoggers();

	/**
	 * The logger used to collect extracted informations
	 */
	private ILoggerFormat logger;

	/**
	 * represent the maximum recursion algorithm to study object's fields and array's value can make
	 */
	private int maxDepth;

	/**
	 * Used to indicates which Object has already been visited, to not visit again.
	 */
	private Set<ObjectReference> visited = new HashSet<>();

	/**
	 * Constructor of StackExtractor
	 * 
	 * @param loggerInfos informations to instantiate the logger
	 */
	public StackExtractor(JsonNode loggerInfos, int depth) {
		 if (loggerInfos == null || !loggerInfos.has("format") || !loggerInfos.has("outputName") || !loggerInfos.has("extension")) {
		        throw new IllegalArgumentException("Missing required fields in loggerInfos: 'format', 'outputName', or 'extension'");
		    }
		
		// logger creation
		String format = loggerInfos.get("format").textValue();
		String outputName = loggerInfos.get("outputName").textValue();
		String extension = loggerInfos.get("extension").textValue();
		
		if (!loggerChoice.containsKey(format)) {
	        throw new IllegalArgumentException("Logger format not recognized: " + format);
	    }

		logger = loggerChoice.get(format).apply(outputName, extension);
		
		// max depth setting
		maxDepth = depth;
	}
	
	public static Map<String, BiFunction<String, String, ILoggerFormat>> registerAllLoggers() {
		Map<String, BiFunction<String, String, ILoggerFormat>> res = new HashMap<>();
		// json format
		res.put("json", (name, extension) -> new LoggerJson(name, extension));
		// txt format
		res.put("txt", (name, extension) -> new LoggerText(name, extension));
		return res;
	}

	/**
	 * Returns the used logger
	 * 
	 * @return the used logger
	 */
	public ILoggerFormat getLogger() {
		return logger;
	}

	/**
	 * Properly close the logger
	 */
	public void closeLogger() {
		try {
			logger.closeWriter();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * extract a frame, by extracting the method signature, its arguments, and its receiver
	 * 
	 * @param frame the frame to extract
	 */
	public void extract(StackFrame frame) {
		extractMethod(frame);
		extractArguments(frame);
		extractReceiver(frame);
	}

	/**
	 * Extracting the method signature used in the given frame
	 * 
	 * @param frame the frame to extract
	 */
	public void extractMethod(StackFrame frame) {
		Method method = frame.location().method();
		logger.methodSignature(method);
	}

	/**
	 * Extracting all accessible arguments given in the method in this frame
	 * 
	 * @param frame the frame to extract
	 */
	public void extractArguments(StackFrame frame) {
		logger.methodArgumentStart();

		// arguments can sometimes not be accessible, if that's the case, stop here
		Iterator<Value> argumentsValueIterator;
		try {
			argumentsValueIterator = frame.getArgumentValues().iterator();

			// doing the first iteration separately because the logging potentially need
			// to know if we are at the first element or not to join with a special character
			if (argumentsValueIterator.hasNext()) {
				extractAnArgument(argumentsValueIterator);
			}

			while (argumentsValueIterator.hasNext()) {
				logger.joinElementListing();
				extractAnArgument(argumentsValueIterator);
			}
		} catch (InternalException e) {
			// Happens for native calls, and can't be obtained
			logger.unaccessibleField(0);
		}

		logger.methodArgumentEnd();
	}

	/**
	 * 
	 * @param argumentsValueIterator the iterator on the arguments
	 */
	private void extractAnArgument(Iterator<Value> argumentsValueIterator) {
		// Here we suppose that method.argumentTypeNames() and frame.getArgumentValues() have the same numbers of items
		// With this supposition being always true, we can just check if one have next and iterate in both
		extractValueRecursive(argumentsValueIterator.next(), 0);
	}

	/**
	 * Extracting the receiver of this frame
	 * 
	 * @param frame the frame to extract
	 */
	public void extractReceiver(StackFrame frame) {
		logger.methodReceiverStart();
		extractValueRecursive(frame.thisObject(), 0);
		logger.methodReceiverEnd();
	}

	/**
	 * extract the given value recursively to make sure no information are lost in the process
	 * 
	 * @param value the value to extract
	 */
	private void extractValueRecursive(Value value, int depth) {
		if (maxDepth != 0 & depth > maxDepth) {
			logger.maxDepth(depth);
		} else if (value == null) {
			logger.nullValue(depth);
		} else if (value instanceof PrimitiveValue) {
			extractPrimitiveValue((PrimitiveValue) value, depth);
		} else if (value instanceof ObjectReference) {
			extractObjectReference((ObjectReference) value, depth);
		} else if (value instanceof VoidValue) {
			// TODO
			// implements this if needed
			throw new IllegalStateException("VoidValue encountered, extracting not yet implemented");
		} else {
			// in case there would be another type
			throw new IllegalStateException("Unknown Value Type: " + value.type().name() + ", parsing not yet implemented for this type");
		}

	}

	/**
	 * extract given the primitive value
	 * 
	 * @param value the primitiveValue to extract
	 */
	private void extractPrimitiveValue(PrimitiveValue value, int depth) {
		logger.primitiveValue(value, depth);
	}

	/**
	 * extract the given ObjectReference
	 * 
	 * @param value the ObjectReference to extract
	 */
	private void extractObjectReference(ObjectReference value, int depth) {
		logger.objectReferenceStart(value, depth);

		if (visited.contains(value)) {
			logger.objectReferenceAlreadyFound(value, depth);
		} else {
			visited.add(value);

			if (value instanceof StringReference) {
				logger.stringReference((StringReference) value, depth);
			} else if (value instanceof ArrayReference) {

				logger.arrayReferenceStart();
				// Parsing every value of the array
				List<Value> arrayValues = ((ArrayReference) value).getValues();
				if (arrayValues.isEmpty()) {
					logger.emptyArray(depth);
				} else if (maxDepth != 0 & depth + 1 > maxDepth) {
					// in case the max depth will be attained stop here to not make an array full of maxDepth messages
					logger.maxDepth(depth);
				} else {
					// doing the first iteration separately because the logging potentially need
					// to know if we are at the first element or not to join with a special character
					extractArrayValue(depth, arrayValues, 0);

					for (int i = 1; i < arrayValues.size(); i++) {
						logger.joinElementListing();
						extractArrayValue(depth, arrayValues, i);
					}

				}
				logger.arrayReferenceEnd();
			} else if (value instanceof ClassObjectReference) {
				// using reflectedType because it is said to be more precise than referenceType
				extractAllFields(value, ((ClassObjectReference) value).reflectedType(), depth);

			} else {
				extractAllFields(value, value.referenceType(), depth);
			}
		}
		logger.objectReferenceEnd();
	}

	/**
	 * Extract one value of the array
	 * 
	 * @param depth       the current depth of the recursion
	 * @param arrayValues the values of the array
	 * @param index       the index of the value to extract
	 */
	private void extractArrayValue(int depth, List<Value> arrayValues, int index) {
		logger.arrayValueStart(index, depth);
		extractValueRecursive(arrayValues.get(index), depth + 1);
		logger.arrayValueEnd();
	}

	/**
	 * extract all the fields of an ObjectReference
	 * 
	 * @param ref  the ObjectReference having the fields to extract
	 * @param type the reference type of the ObjectReference
	 */
	private void extractAllFields(ObjectReference ref, ReferenceType type, int depth) {
		logger.fieldsStart();

		// Check if the class is prepared, if not trying to get any field will throw an exception
		// TODO maybe there is a way to force load the class, is that useful ? maybe the fact that it didn't load mean it's not useful
		if (!type.isPrepared()) {
			// Preparation involves creating the static fields for a class or interface and
			// initializing such fields to their default values

			logger.classNotPrepared(depth);
		} else {
			Iterator<Field> iterator = type.allFields().iterator();
			// doing the first iteration separately because the logging potentially need
			// to know if we are at the first element or not to join with a special character
			if (iterator.hasNext()) {
				extractField(ref, depth, iterator.next());
			}

			while (iterator.hasNext()) {
				logger.joinElementListing();
				extractField(ref, depth, iterator.next());

			}
		}

		logger.fieldsEnd();
	}

	/**
	 * Extract one field of an Object reference
	 * 
	 * @param ref   the ObjectReference where the field is
	 * @param depth the depth of the current recursion
	 * @param field the field to extract
	 */
	private void extractField(ObjectReference ref, int depth, Field field) {
		try {
			// TODO
			// We actually extract the static and final fields, should we?
			// it's potential information but could also be noise
			Value fieldValue = ref.getValue(field);
			logger.fieldNameStart(field.name(), depth);

			extractValueRecursive(fieldValue, depth + 1);

			logger.fieldNameEnd();

		} catch (IllegalArgumentException e) {
			logger.unaccessibleField(depth);
		}
	}

}
