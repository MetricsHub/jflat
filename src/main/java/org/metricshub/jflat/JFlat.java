package org.metricshub.jflat;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * JFlat Utility
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 Metricshb
 * ჻჻჻჻჻჻
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonStructure;
import javax.json.JsonString;
import javax.json.JsonNumber;
import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParsingException;

/**
 * Provides tools to convert a JSON-formated content to a flat structure, exported as a String.
 * @author Bertrand Martin
 *
 */
public class JFlat {

	private TreeMap<String, String> map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);  // IMPORTANT: The map is case iNsEnSiTiVe!
	private ArrayList<String> arrayPaths = new ArrayList<String>();
	private ArrayList<Integer> arrayLengths = new ArrayList<Integer>();
	private Reader inputReader;
	private boolean parsed = false;

	/**
	 * Create a new JFlat instance
	 *
	 * @param pJsonReader A Reader object (can be StringReader, FileReader, etc.)
	 */
	public JFlat(Reader pJsonReader) {
		inputReader = pJsonReader;
	}

	/**
	 * @param pJsonSource JSON source to be parsed
	 */
	public JFlat(String pJsonSource) {
		this(pJsonSource == null ? new StringReader("") : new StringReader(pJsonSource));
	}

	/**
	 * Parse the JSON document
	 * <p>
	 * This call is mandatory before doing any other operation.
	 *
	 * @throws ParseException when any error occurs during the parsing
	 * @throws IOException when the stream cannot be read
	 * @throws IllegalStateException when... actually never in a single-thread context
	 */
	public void parse() throws ParseException, IOException, IllegalStateException {
		parse(false);
	}

	/**
	 * Parse the JSON document
	 * <p>
	 * This call is mandatory before doing any other operation.
	 *
	 * @param removeNodes Whether to remove "artificial" nodes without values ({object} and {array})
	 *
	 * @throws ParseException when any error occurs during the parsing
	 * @throws IOException when the stream cannot be read
	 * @throws IllegalStateException when... actually never in a single-thread context
	 */
	public void parse(boolean removeNodes) throws ParseException, IOException, IllegalStateException {

		// Read the JSON source
		JsonReader reader = Json.createReader(inputReader);
		JsonStructure root;

		try {
			root = reader.read();
		}
		catch (JsonParsingException e) {
			JsonLocation location = e.getLocation();
			throw new ParseException("JSON syntax error in the specified source at line " + location.getLineNumber() + ", column " + location.getColumnNumber(), (int) location.getStreamOffset());
		}
		catch (JsonException e) {
			throw new IOException(e.getCause());
		}
		finally {
			// In any case, close the reader
			if (reader != null) reader.close();
		}

		// Parse it and build the hash map
		navigateTree(root, "", removeNodes);

		// Some adjustments for the root value:
		// at this stage it is represented as the "" key, but it should be "/"
		if (map.containsKey("")) {
			map.put("/", map.get(""));
			map.remove("");
		}

		// Remember that the parsing has been done
		parsed = true;
	}


	/**
	 * Navigate the JSON tree and populate the hash map that will contain pairs of keys/value in the form below:
	 * obj1.propA = value
	 * obj1.propB[0] = value
	 * obj1.propB[1] = value
	 * obj1.propC.arr[0].id = value
	 * obj1.propC.arr[0].name = value
	 * obj2.propC.arr[1].id = value
	 * etc.
	 * The method is recursive.
	 * @param tree Root of the JSON object to be parsed (can be an object or an array)
	 * @param path Current path of the object (when originally called, the path is "" (root). Recursive call will specify where we are in the tree.
	 * @param removeNodes Whether to remove "artificial" nodes without values ({object} and {array})
	 */
	private void navigateTree(JsonValue tree, String path, boolean removeNodes) {

		// Sanity check
		if (tree == null) {
			return;
		}
		if (path == null)
			path = "";

		// Depending on the type of the value where we are...
		switch (tree.getValueType()) {

			case OBJECT: {
				// We have an object, we will parse all of its attributes
				JsonObject object = (JsonObject) tree;

				// Add it to the map as an object (if it wasn't asked to remove it)
				if (!removeNodes) {
					map.put(path, "{object}");
				}

				// Go through each property of the object
				for (String name : object.keySet()) {
					// The syntax of the path is object.propertyA
					navigateTree(object.get(name), path + "/" + name, removeNodes);
				}
				break;
			}

			case ARRAY: {
				// We have an array, that's the interesting case
				JsonArray array = (JsonArray) tree;

				// Add it to the map as an array (if it wasn't asked to remove it)
				if (!removeNodes) {
					map.put(path, "{array}");
				}

				// Go through each entry in the array
				int i = 0;
				for (JsonValue val : array) {
					// Go through
					navigateTree(val, path + "[" + i + "]", removeNodes);
					i++;
				}

				// Remember its path and length so we properly (and efficiently) parse it later
				arrayPaths.add(path);
				arrayLengths.add(i);

				break;
			}

			case STRING: {
				// We got a string
				JsonString st = (JsonString) tree;

				// If so, add it to the map
				map.put(path, st.getString());

				break;
			}

			case NUMBER: {
				JsonNumber num = (JsonNumber) tree;
				map.put(path, num.toString());
				break;
			}

			case TRUE:
			case FALSE:
			case NULL: {
				map.put(path, tree.getValueType().toString());
				break;
			}
			default: break;
		}
	}

	/**
	 * Dump the JSON tree as a String in the form: <br>
	 * /object/array[0]/id=<i>0</i> <br>
	 * /object/array[0]/name=<i>some value</i> <br>
	 * /object/array[1]/id=... <br>
	 * ... <br>
	 *
	 * @param valueSeparator String to be placed between each pair of key and value
	 *
	 * @return The String described above.
	 * @throws IllegalStateException when the document has not been parsed first (call parse() first!)
	 */
	public StringBuilder getFlatTree(String valueSeparator, String replaceEndOfLines) throws IllegalStateException {

		// Did we parse the thing yet?
		if (!parsed) {
			throw new IllegalStateException("JSON document has not been parsed");
		}

		// Use a StringBuilder to hold the result
		StringBuilder result = new StringBuilder();

		// Dump the tree
		for (Entry<String, String> entry : map.entrySet()) {
			result.append(entry.getKey()).append(valueSeparator);
			// If we need to replace end of lines
			if (replaceEndOfLines != null) {
				result.append(entry.getValue().replace("\n", replaceEndOfLines)).append("\n");
			}
			else {
				result.append(entry.getValue()).append("\n");
			}
		}

		// Return
		return result;
	}

	/**
	 * Dump the JSON tree as a String in the form: <br>
	 * /object/array[0]/id=<i>0</i> <br>
	 * /object/array[0]/name=<i>some value</i> <br>
	 * /object/array[1]/id=... <br>
	 * ... <br>
	 * @param valueSeparator String to be placed between each pair of key and value
	 * @return The String described above.
	 */
	public StringBuilder getFlatTree(String valueSeparator) {
		return getFlatTree(valueSeparator, null);
	}

	/**
	 * Dump the JSON tree as a String in the form: <br>
	 * /object/array[0]/id=<i>0</i> <br>
	 * /object/array[0]/name=<i>some value</i> <br>
	 * /object/array[1]/id=... <br>
	 * ... <br>
	 * @return The String described above.
	 */
	public StringBuilder getFlatTree() {
		return getFlatTree("=", null);
	}

	/**
	 * Translates (flattens) a JSON structure into a CSV string
	 *
	 * @param csvEntryKey The key in the JSON data that will be shown as a new entry in the resulting CSV (i.e. a new line)
	 * @param csvProperties Array of strings specifying the properties of the entry key to be added to the CSV as new fields
	 * @param separator The separator between fields in the resulting CSV (";" will be used if null)
	 * @return The CSV string
	 * @throws IllegalArgumentException when any of the specified arguments is null (or an entry in the csvProperties array is null)
	 * @throws IllegalStateException when the JSON document has not been parsed yet (call parse() first!)
	 */
	public StringBuilder toCSV(String csvEntryKey, String [] csvProperties, String separator) throws IllegalStateException, IllegalArgumentException {

		// Did we parse the thing yet?
		if (!parsed) {
			throw new IllegalStateException("JSON document has not been parsed");
		}

		// Sanity check: If anything is null, throw an IllegalArgument exception (avoid null, which will surely trigger a NullPointerException somewhere)
		if (csvEntryKey == null) {
			throw new IllegalArgumentException("Cannot convert JSON to CSV without a proper entry key (non-null)");
		}
		// Replace a null array with an empty array
		if (csvProperties == null) {
			csvProperties = new String[] {};
		}
		// Check for nullness in the array
		for (String property : csvProperties) {
			if (property == null) {
				throw new IllegalArgumentException("Cannot convert JSON to CSV without a proper list of properties (non-null)");
			}
		}

		// Clean the properties
		for (int i = 0 ; i < csvProperties.length ; i++) {
			if (csvProperties[i].startsWith("./")) { csvProperties[i] = csvProperties[i].substring(2); }
			while (csvProperties[i].startsWith("/")) { csvProperties[i] = csvProperties[i].substring(1); }
		}

		// Default separator is ";"
		if (separator == null) { separator = ";"; }

		// Initialize the StringBuilder to hold the result
		StringBuilder csvResult = new StringBuilder();

		// Empty TreeMap?
		if (map == null) return csvResult;
		if (map.size() == 0) return csvResult;

		// Add a "/" at the beginning of the entry key, if necessary
		if (csvEntryKey.isEmpty()) {
			csvEntryKey = "/";
		}
		if (!csvEntryKey.startsWith("/")) {
			csvEntryKey = "/" + csvEntryKey;
		}

		// Build the list of entries that will constitutes CSV records (new lines)
		ArrayList<String> entries = new ArrayList<String>();

		// csvEntryKey is specified as a path (e.g. /objectA/array1/subobject)
		// We will deconstruct the specified path and check whether each "subfolder" is an array or not
		// If it's an array we will add all of the array entries to the list
		// So, we will start with the "root" object.
		// If that object is an array, we will add each entry of the array to the entries list.
		// Then, we will take each entry in the entries list and add the next "subfolder"
		// For each of these that are arrays, we will add each entries of that array
		// and so on and so on.
		// Note that the initial parsing of the JSON source already built the list of paths
		// that are arrays

		// Retrieve each element in the specified path
		String[] pathElementArray = csvEntryKey.split("/");

		// In case the JSON doc is an array, we will add its root entries
		// Note: this means that "" (empty string) is in the list of arrays found in the doc
		int arrayLength = 0;
		for (int i = 0 ; i < arrayPaths.size() ; i++) {
			if ("".equals(arrayPaths.get(i))) {
				arrayLength = arrayLengths.get(i);
				break;
			}
		}
		if (arrayLength > 0) {
			// Start with [0], [1], etc.
			for (int i = 0 ; i < arrayLength ; i++) {
				entries.add("[" + i + "]");
			}
		} else {
			// Start with "/"
			entries.add("/");
		}

		// Now, process each element, as described above
		for (String pathElement : pathElementArray) {

			// Empty pathElement? Skip.
			if (pathElement == null) continue;
			if (pathElement.isEmpty()) continue;

			// Temporary list where we will store the new entries
			ArrayList<String> newEntries = new ArrayList<String>();

			// For each existing entry, check whether entry/pathElement is an array
			for (String existingEntry : entries) {
				String path;
				if (existingEntry.equals("/")) {
					path = "/" + pathElement;
				}
				else {
					path = existingEntry + "/" + pathElement;
				}

				// Check whether path is listed in arrayPaths
				arrayLength = 0;
				for (int i = 0 ; i < arrayPaths.size() ; i++) {
					if (path.equalsIgnoreCase(arrayPaths.get(i))) {
						arrayLength = arrayLengths.get(i);
						break;
					}
				}

				if (arrayLength > 0) {
					// So, path is an array
					// Then add each entry of the array to the newEntries list
					for (int i = 0 ; i < arrayLength ; i++) {
						newEntries.add(path + "[" + i + "]");
					}
				}
				else {
					// This is not an array, simply add path to the newEntries list
					newEntries.add(path);
				}
			}

			// Now, transfer newEntries to entries
			entries = newEntries;
		}


		// And now, build the CSV
		for (String entry : entries) {

			// Check that the entry actually exists (in case, the user has put an invalid entryKey)
			if (!map.containsKey(entry)) continue;

			// First, add the "ID" of the entry
			csvResult.append(map.floorKey(entry)).append(separator);

			// If it's the root ("/"), replace it with "", so that future concatenation with the property name will work properly
			if (entry.equals("/")) { entry = ""; }

			// Then add the value of each column (empty string for null)
			for (String property : csvProperties) {

				// Path of the property to get
				// If property is just ".", then it's the entryKey itself that we want,
				// like when the entry key is just a simple array of integers or strings
				String path;
				if (property.equals(".")) {
					path = entry;
				}
				else {
					path = entry + "/" + property;
				}

				// Process ../ (reference to the parent)
				while (path.contains("/../")) {
					int pos2 = path.indexOf("/../");
					int pos1 = path.lastIndexOf("/", pos2 - 1);
					path = path.substring(0, pos1) + path.substring(pos2 + 3);
				}

				// Get the value
				String value = map.get(path);
				if (value == null) value = "";

				// Append to the result
				csvResult.append(value).append(separator);
			}

			// End of line, new record!
			csvResult.append("\n");
		}

		// Return
		return csvResult;

	}

}
