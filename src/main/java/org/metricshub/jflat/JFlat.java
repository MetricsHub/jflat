package org.metricshub.jflat;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * JFlat Utility
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 MetricsHub
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.json.stream.JsonParsingException;

/**
 * Provides tools to convert a JSON-formated content to a flat structure, exported as a String.
 * <p>
 * In streaming mode (see {@link #JFlat(String, boolean)}), {@link #toCSV(String, String[], String)} reads the
 * document again and flattens the value of the first element of the entry key one array element at a time,
 * instead of keeping a map of the whole document: the memory used is bounded by the size of one element. The
 * result is the same as in the default mode; the documents that cannot be streamed exactly (see
 * {@link #toCSV(String, String[], String)}) are processed as in the default mode.
 * @author Bertrand Martin
 *
 */
public class JFlat {

	private TreeMap<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // IMPORTANT: The map is case iNsEnSiTiVe!
	private ArrayList<String> arrayPaths = new ArrayList<>();
	private ArrayList<Integer> arrayLengths = new ArrayList<>();
	private Reader inputReader;
	private boolean parsed = false;

	// Streaming mode: the source is kept to be read again, and the map of the whole document is only built when
	// needed (getFlatTree(), or a document that cannot be streamed)
	private boolean streaming;
	private String source;
	private boolean removeNodes;
	private boolean fullMap;

	/**
	 * Create a new JFlat instance
	 *
	 * @param pJsonReader A Reader object (can be StringReader, FileReader, etc.)
	 */
	public JFlat(Reader pJsonReader) {
		this(pJsonReader, false);
	}

	/**
	 * Create a new JFlat instance
	 *
	 * @param pJsonReader A Reader object (can be StringReader, FileReader, etc.), read entirely by parse() in
	 *                    streaming mode
	 * @param pStreaming Whether toCSV() streams the document instead of keeping a map of all its nodes
	 */
	public JFlat(Reader pJsonReader, boolean pStreaming) {
		inputReader = pJsonReader;
		streaming = pStreaming;
	}

	/**
	 * @param pJsonSource JSON source to be parsed
	 */
	public JFlat(String pJsonSource) {
		this(pJsonSource, false);
	}

	/**
	 * @param pJsonSource JSON source to be parsed
	 * @param pStreaming Whether toCSV() streams the document instead of keeping a map of all its nodes
	 */
	public JFlat(String pJsonSource, boolean pStreaming) {
		this(pJsonSource == null ? new StringReader("") : new StringReader(pJsonSource), pStreaming);
		source = pJsonSource == null ? "" : pJsonSource;
	}

	/**
	 * Container of the map of one unit of a streamed document
	 */
	private JFlat() {
		this((Reader) null, false);
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
	 * This call is mandatory before doing any other operation. In streaming mode, the document is only checked:
	 * nothing is kept but the source.
	 *
	 * @param removeNodes Whether to remove "artificial" nodes without values ({object} and {array})
	 *
	 * @throws ParseException when any error occurs during the parsing
	 * @throws IOException when the stream cannot be read
	 * @throws IllegalStateException when... actually never in a single-thread context
	 */
	public void parse(boolean removeNodes) throws ParseException, IOException, IllegalStateException {
		if (streaming) {
			this.removeNodes = removeNodes;
			if (source == null) {
				source = readAll(inputReader);
			}
			if (isStreamable(source)) {
				parsed = true;
				return;
			}

			// Not a document the streaming mode can read: parse it as usual, which throws the same exceptions
			inputReader = new StringReader(source);
			fullMap = true;
		}

		// Read the JSON source
		JsonReader reader = Json.createReader(inputReader);
		JsonStructure root;

		try {
			root = reader.read();
		} catch (JsonParsingException e) {
			JsonLocation location = e.getLocation();
			throw new ParseException(
				"JSON syntax error in the specified source at line " +
				location.getLineNumber() +
				", column " +
				location.getColumnNumber(),
				(int) location.getStreamOffset()
			);
		} catch (JsonException e) {
			throw new IOException(e.getCause());
		} finally {
			// In any case, close the reader
			reader.close();
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
	 * Read the whole content of a reader, and close it (even when it cannot be read)
	 *
	 * @param reader The reader
	 * @return The content
	 * @throws IOException when the reader cannot be read
	 */
	private static String readAll(Reader reader) throws IOException {
		try (Reader input = reader) {
			StringBuilder content = new StringBuilder();
			char[] buffer = new char[8192];
			int length;
			while ((length = input.read(buffer)) != -1) {
				content.append(buffer, 0, length);
			}
			return content.toString();
		}
	}

	/**
	 * Check, with the streaming parser, that the document is an object or an array that the default mode can read
	 * (what follows the root value is ignored, as by the default mode)
	 *
	 * @param json The JSON document
	 * @return true when the document can be streamed
	 */
	private static boolean isStreamable(String json) {
		JsonParser parser = Json.createParser(new StringReader(json));
		try {
			Event event = parser.next();
			if (event != Event.START_OBJECT && event != Event.START_ARRAY) {
				return false;
			}
			int depth = 1;
			while (depth > 0) {
				event = parser.next();
				if (event == Event.START_OBJECT || event == Event.START_ARRAY) {
					depth++;
				} else if (event == Event.END_OBJECT || event == Event.END_ARRAY) {
					depth--;
				} else if (event == Event.VALUE_NUMBER) {
					// The default mode converts every number, which fails on an out-of-range exponent
					parser.getValue();
				}
			}
			return true;
		} catch (RuntimeException e) {
			return false;
		} finally {
			parser.close();
		}
	}

	/**
	 * Build the map of the whole document of a streaming instance
	 */
	private void buildFullMap() {
		streaming = false;
		inputReader = new StringReader(source);
		try {
			parse(removeNodes);
		} catch (ParseException | IOException e) {
			// The document was checked by parse()
			throw new IllegalStateException("JSON document cannot be parsed again", e);
		}
		fullMap = true;
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
		if (path == null) {
			path = "";
		}

		// Depending on the type of the value where we are...
		switch (tree.getValueType()) {
			case OBJECT:
				// We have an object, we will parse all of its attributes
				JsonObject object = (JsonObject) tree;

				// Add it to the map as an object (if it wasn't asked to remove it)
				if (!removeNodes) {
					map.put(path, "{object}");
				}

				// Go through each property of the object
				for (Entry<String, JsonValue> entry : object.entrySet()) {
					// The syntax of the path is object.propertyA
					navigateTree(entry.getValue(), path + "/" + entry.getKey(), removeNodes);
				}
				break;
			case ARRAY:
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
			case STRING:
				// We got a string
				JsonString st = (JsonString) tree;

				// If so, add it to the map
				map.put(path, st.getString());

				break;
			case NUMBER:
				JsonNumber num = (JsonNumber) tree;
				map.put(path, num.toString());
				break;
			case TRUE:
			case FALSE:
			case NULL:
				map.put(path, tree.getValueType().toString());
				break;
			default:
				break;
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

		// The flat tree is the map of the whole document
		if (streaming && !fullMap) {
			buildFullMap();
		}

		// Use a StringBuilder to hold the result
		StringBuilder result = new StringBuilder();

		// Dump the tree
		for (Entry<String, String> entry : map.entrySet()) {
			result.append(entry.getKey()).append(valueSeparator);
			// If we need to replace end of lines
			if (replaceEndOfLines != null) {
				result.append(entry.getValue().replace("\n", replaceEndOfLines)).append("\n");
			} else {
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
	 * <p>
	 * In streaming mode, the document is read again and the value of the first element of the entry key
	 * (<code>items</code> in <code>/items/status/conditions</code>) is flattened one array element at a time (or
	 * as a single unit when it is not a non-empty array), skipping the nodes that neither the entry key nor the
	 * properties can reach. The document is processed as in the default mode when it cannot be streamed exactly:
	 * root array, entry key <code>/</code>, first element of the entry key that is a wildcard or contains an index,
	 * first element of the entry key present more than once in the root object (whatever the case), root key that
	 * contains <code>/</code> or <code>[</code>, property that refers to a node above the unit
	 * (<code>../kind</code> from <code>/items</code>), or a duplicate key in an object of a unit.
	 *
	 * @param csvEntryKey The key in the JSON data that will be shown as a new entry in the resulting CSV (i.e. a new line)
	 * @param csvProperties Array of strings specifying the properties of the entry key to be added to the CSV as new fields
	 * @param separator The separator between fields in the resulting CSV (";" will be used if null)
	 * @return The CSV string
	 * @throws IllegalArgumentException when any of the specified arguments is null (or an entry in the csvProperties array is null)
	 * @throws IllegalStateException when the JSON document has not been parsed yet (call parse() first!)
	 */
	public StringBuilder toCSV(String csvEntryKey, String[] csvProperties, String separator)
		throws IllegalStateException, IllegalArgumentException {
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
		for (int i = 0; i < csvProperties.length; i++) {
			if (csvProperties[i].startsWith("./")) {
				csvProperties[i] = csvProperties[i].substring(2);
			}
			while (csvProperties[i].startsWith("/")) {
				csvProperties[i] = csvProperties[i].substring(1);
			}
		}

		// Default separator is ";"
		if (separator == null) {
			separator = ";";
		}

		// Streaming mode: process the document one unit at a time, unless it cannot be streamed exactly
		if (streaming && !fullMap) {
			try {
				return streamToCSV(csvEntryKey, csvProperties, separator);
			} catch (NotStreamableException e) {
				buildFullMap();
			}
		}

		// Initialize the StringBuilder to hold the result
		StringBuilder csvResult = new StringBuilder();

		// Empty TreeMap?
		if (map == null || map.size() == 0) {
			return csvResult;
		}

		// Add a "/" at the beginning of the entry key, if necessary
		if (csvEntryKey.isEmpty()) {
			csvEntryKey = "/";
		}
		if (!csvEntryKey.startsWith("/")) {
			csvEntryKey = "/" + csvEntryKey;
		}

		// Build the list of entries that will constitutes CSV records (new lines)
		ArrayList<String> entries = new ArrayList<>();

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
		for (int i = 0; i < arrayPaths.size(); i++) {
			if ("".equals(arrayPaths.get(i))) {
				arrayLength = arrayLengths.get(i);
				break;
			}
		}
		if (arrayLength > 0) {
			// Start with [0], [1], etc.
			for (int i = 0; i < arrayLength; i++) {
				entries.add("[" + i + "]");
			}
		} else {
			// Start with "/"
			entries.add("/");
		}

		// Now, process each element, as described above
		entries = expandEntries(entries, pathElementArray, 0);

		// And now, build the CSV
		try {
			appendRows(entries, csvProperties, separator, null, csvResult);
		} catch (NotStreamableException e) {
			// Only raised for a unit of a streamed document
			throw new IllegalStateException(e);
		}

		// Return
		return csvResult;
	}

	/**
	 * Expand the entries with the elements of the entry key path
	 *
	 * @param entries The entries at the start
	 * @param pathElementArray The elements of the entry key path
	 * @param start The index of the first element to process
	 * @return The expanded entries
	 */
	private ArrayList<String> expandEntries(ArrayList<String> entries, String[] pathElementArray, int start) {
		int arrayLength;

		// Now, process each element, as described above
		for (int e = start; e < pathElementArray.length; e++) {
			String pathElement = pathElementArray[e];

			// Empty pathElement? Skip.
			if (pathElement == null || pathElement.isEmpty()) {
				continue;
			}

			// Wildcard handling:
			// "*" in the path means "expand all direct children of the current object".
			// This is useful for JSON structures where objects are keyed by dynamic names
			// (e.g. UIDs, hashes) rather than stored in arrays.
			// Example: /members/* expands to /members/key1, /members/key2, etc.
			//
			// Escaping: if a JSON property is literally named "*", use "\*" in the path
			// to refer to it without triggering wildcard expansion.
			boolean isWildcard = "*".equals(pathElement);
			if ("\\*".equals(pathElement)) {
				// Strip the escape backslash and treat "*" as a literal property name
				pathElement = "*";
				isWildcard = false;
			}

			// Temporary list where we will store the new entries
			ArrayList<String> newEntries = new ArrayList<>();
			// Set of lower-cased paths already added, used for O(1) case-insensitive dedup
			Set<String> seenLowercasePaths = new HashSet<>();

			for (String existingEntry : entries) {
				if (isWildcard) {
					// Case 1: Check if existingEntry is itself an array path.
					// If so, expand its indices just like the non-wildcard array logic.
					int entryArrayLength = 0;
					for (int i = 0; i < arrayPaths.size(); i++) {
						if (existingEntry.equalsIgnoreCase(arrayPaths.get(i))) {
							entryArrayLength = arrayLengths.get(i);
							break;
						}
					}

					if (entryArrayLength > 0) {
						// existingEntry is an array, expand its indices
						for (int i = 0; i < entryArrayLength; i++) {
							newEntries.add(existingEntry + "[" + i + "]");
						}
					} else {
						// Case 2: Check if this entry was produced by a previous array expansion
						// (i.e. it ends with [n] and the parent path is in arrayPaths).
						// In that case, the wildcard is redundant — just pass through.
						boolean fromArrayExpansion = false;
						int lastBracket = existingEntry.lastIndexOf('[');
						if (lastBracket >= 0) {
							String parentPath = existingEntry.substring(0, lastBracket);
							for (int i = 0; i < arrayPaths.size(); i++) {
								if (parentPath.equalsIgnoreCase(arrayPaths.get(i))) {
									fromArrayExpansion = true;
									break;
								}
							}
						}

						if (fromArrayExpansion) {
							// Already expanded from an array, pass through unchanged
							newEntries.add(existingEntry);
						} else {
							// Case 3: Expand all direct object children.
							// We iterate over keys in the map that are at or after the current entry's
							// prefix (e.g. "/members/") and stop once keys no longer match that prefix.
							// From each matching key, we extract the immediate child name by looking for
							// the next "/" or "[" delimiter, which marks a deeper level or an array index.
							// Duplicates are skipped (case-insensitive) to ensure each child appears once.
							String prefix = existingEntry.equals("/") ? "/" : existingEntry + "/";
							for (String key : map.tailMap(prefix, true).keySet()) {
								// Stop once keys are no longer under the prefix
								if (!key.regionMatches(true, 0, prefix, 0, prefix.length())) {
									break;
								}
								// Only consider keys that are strictly under the prefix
								if (key.length() > prefix.length()) {
									// Extract the portion after the prefix, e.g. "abc123/name" from "/members/abc123/name"
									String remainder = key.substring(prefix.length());

									// Find the boundary of the immediate child name:
									// - "/" indicates a deeper nested property
									// - "[" indicates an array index
									// The child name is everything before the first such delimiter.
									int slashPos = remainder.indexOf('/');
									int bracketPos = remainder.indexOf('[');
									String childName;
									if (slashPos == -1 && bracketPos == -1) {
										// No delimiter: the remainder itself is the child name (leaf key)
										childName = remainder;
									} else if (slashPos == -1) {
										// Only "[" found: child has array children (e.g. "args[0]")
										childName = remainder.substring(0, bracketPos);
									} else if (bracketPos == -1) {
										// Only "/" found: child has nested properties (e.g. "abc123/name")
										childName = remainder.substring(0, slashPos);
									} else {
										// Both found: take the earlier delimiter
										childName = remainder.substring(0, Math.min(slashPos, bracketPos));
									}

									// Add the child path if it's valid and not already seen (case-insensitive)
									if (!childName.isEmpty()) {
										String childPath = prefix + childName;
										if (seenLowercasePaths.add(childPath.toLowerCase(Locale.ROOT))) {
											newEntries.add(childPath);
										}
									}
								}
							}
						}
					}
				} else {
					String path;
					if (existingEntry.equals("/")) {
						path = "/" + pathElement;
					} else {
						path = existingEntry + "/" + pathElement;
					}

					// Check whether path is listed in arrayPaths
					arrayLength = 0;
					for (int i = 0; i < arrayPaths.size(); i++) {
						if (path.equalsIgnoreCase(arrayPaths.get(i))) {
							arrayLength = arrayLengths.get(i);
							break;
						}
					}

					if (arrayLength > 0) {
						// So, path is an array
						// Then add each entry of the array to the newEntries list
						for (int i = 0; i < arrayLength; i++) {
							newEntries.add(path + "[" + i + "]");
						}
					} else {
						// This is not an array, simply add path to the newEntries list
						newEntries.add(path);
					}
				}
			}

			// Now, transfer newEntries to entries
			entries = newEntries;
		}

		return entries;
	}

	/**
	 * Append the CSV rows of the entries
	 *
	 * @param entries The entries
	 * @param csvProperties The properties of each entry
	 * @param separator The separator between fields
	 * @param unitPath The path of the streamed unit that holds the entries, null for the map of the whole document
	 * @param csvResult Where the rows are appended
	 * @throws NotStreamableException when a property refers to a node above the unit
	 */
	private void appendRows(
		List<String> entries,
		String[] csvProperties,
		String separator,
		String unitPath,
		StringBuilder csvResult
	) throws NotStreamableException {
		// And now, build the CSV
		for (String entry : entries) {
			// Check that the entry actually exists (in case, the user has put an invalid entryKey)
			if (!map.containsKey(entry)) {
				continue;
			}

			// First, add the "ID" of the entry
			csvResult.append(map.floorKey(entry)).append(separator);

			// If it's the root ("/"), replace it with "", so that future concatenation with the property name will work properly
			if (entry.equals("/")) {
				entry = "";
			}

			// Then add the value of each column (empty string for null)
			for (String property : csvProperties) {
				// Path of the property to get
				// If property is just ".", then it's the entryKey itself that we want,
				// like when the entry key is just a simple array of integers or strings
				String path;
				if (property.equals(".")) {
					path = entry;
				} else {
					path = entry + "/" + property;
				}

				// Process ../ (reference to the parent)
				while (path.contains("/../")) {
					int pos2 = path.indexOf("/../");
					int pos1 = path.lastIndexOf("/", pos2 - 1);
					// A streamed unit only holds its own nodes
					if (unitPath != null && pos1 < unitPath.length()) {
						throw new NotStreamableException();
					}
					path = path.substring(0, pos1) + path.substring(pos2 + 3);
				}

				// Get the value
				String value = map.get(path);
				if (value == null) {
					value = "";
				}

				// Append to the result
				csvResult.append(value).append(separator);
			}

			// End of line, new record!
			csvResult.append("\n");
		}
	}

	/**
	 * Raised when a document cannot be streamed exactly
	 */
	private static final class NotStreamableException extends Exception {

		private static final long serialVersionUID = 1L;

		NotStreamableException() {
			super(null, null, false, false);
		}
	}

	/**
	 * Translates the source into a CSV string in streaming mode
	 *
	 * @param csvEntryKey The entry key (as specified)
	 * @param csvProperties The cleaned properties
	 * @param separator The separator between fields
	 * @return The CSV string
	 * @throws NotStreamableException when the document cannot be streamed exactly
	 */
	private StringBuilder streamToCSV(String csvEntryKey, String[] csvProperties, String separator)
		throws NotStreamableException {
		// Same entry key as the default mode
		String entryKey = csvEntryKey.isEmpty() ? "/" : csvEntryKey;
		if (!entryKey.startsWith("/")) {
			entryKey = "/" + entryKey;
		}
		String[] pathElementArray = entryKey.split("/");

		// The first element selects the value of the root object that is streamed
		int first = 0;
		while (first < pathElementArray.length && pathElementArray[first].isEmpty()) {
			first++;
		}
		if (first == pathElementArray.length) {
			throw new NotStreamableException();
		}
		String firstElement = pathElementArray[first];
		if ("*".equals(firstElement) || firstElement.indexOf('[') >= 0 || firstElement.indexOf(']') >= 0) {
			throw new NotStreamableException();
		}
		if ("\\*".equals(firstElement)) {
			firstElement = "*";
		}

		// What the units must keep, null to keep everything
		PathNode needed = neededPaths(pathElementArray, first + 1, csvProperties);

		StringBuilder csvResult = new StringBuilder();
		JsonParser parser = Json.createParser(new StringReader(source));
		try {
			if (parser.next() != Event.START_OBJECT) {
				throw new NotStreamableException();
			}

			boolean found = false;
			Event event;
			while ((event = parser.next()) != Event.END_OBJECT) {
				String key = parser.getString();
				if (key.indexOf('/') >= 0 || key.indexOf('[') >= 0) {
					throw new NotStreamableException();
				}
				event = parser.next();
				if (!key.equalsIgnoreCase(firstElement)) {
					skipValue(parser, event);
					continue;
				}

				// The default mode merges the keys that differ only by case and keeps the last duplicate
				if (found) {
					throw new NotStreamableException();
				}
				found = true;

				String documentPath = "/" + key;
				String entryPath = "/" + firstElement;
				if (event == Event.START_ARRAY) {
					event = parser.next();
					if (event == Event.END_ARRAY) {
						// An empty array is its own unit, as in the default mode
						JFlat unit = new JFlat();
						if (!removeNodes) {
							unit.map.put(documentPath, "{array}");
						}
						unit.arrayPaths.add(documentPath);
						unit.arrayLengths.add(0);
						unit.appendUnitRows(entryPath, entryPath, pathElementArray, first, csvProperties, separator, csvResult);
						continue;
					}

					// One unit per element of the array
					int index = 0;
					while (event != Event.END_ARRAY) {
						JFlat unit = new JFlat();
						unit.removeNodes = removeNodes;
						unit.flatten(parser, event, documentPath + "[" + index + "]", needed);
						// The parent array, for the wildcard that follows an array expansion
						unit.arrayPaths.add(documentPath);
						unit.arrayLengths.add(0);
						String unitEntry = entryPath + "[" + index + "]";
						unit.appendUnitRows(unitEntry, unitEntry, pathElementArray, first, csvProperties, separator, csvResult);
						index++;
						event = parser.next();
					}
				} else {
					// Any other value is a single unit
					JFlat unit = new JFlat();
					unit.removeNodes = removeNodes;
					unit.flatten(parser, event, documentPath, needed);
					unit.appendUnitRows(entryPath, entryPath, pathElementArray, first, csvProperties, separator, csvResult);
				}
			}
		} finally {
			parser.close();
		}

		return csvResult;
	}

	/**
	 * Append the rows of a unit
	 *
	 * @param unitEntry The entry of the unit, as built from the entry key
	 * @param unitPath The path of the unit
	 * @param pathElementArray The elements of the entry key path
	 * @param first The index of the element that selected the unit
	 * @param csvProperties The cleaned properties
	 * @param separator The separator between fields
	 * @param csvResult Where the rows are appended
	 * @throws NotStreamableException when a property refers to a node above the unit
	 */
	private void appendUnitRows(
		String unitEntry,
		String unitPath,
		String[] pathElementArray,
		int first,
		String[] csvProperties,
		String separator,
		StringBuilder csvResult
	) throws NotStreamableException {
		ArrayList<String> entries = new ArrayList<>();
		entries.add(unitEntry);
		entries = expandEntries(entries, pathElementArray, first + 1);
		appendRows(entries, csvProperties, separator, unitPath, csvResult);
	}

	/**
	 * Skip the value that starts with the specified event
	 *
	 * @param parser The parser
	 * @param event The first event of the value
	 */
	private static void skipValue(JsonParser parser, Event event) {
		if (event == Event.START_OBJECT) {
			parser.skipObject();
		} else if (event == Event.START_ARRAY) {
			parser.skipArray();
		}
	}

	/**
	 * Node of the tree of the paths that a unit must keep, relative to the unit, case-insensitive, without array
	 * indexes: a node of the unit is kept when its path is a prefix of a needed path
	 */
	private static final class PathNode {

		private final Map<String, PathNode> children = new HashMap<>();
	}

	/**
	 * Build the tree of the paths that the units must keep for the entry key and the properties
	 *
	 * @param pathElementArray The elements of the entry key path
	 * @param start The index of the first element below the unit
	 * @param csvProperties The cleaned properties
	 * @return The root of the tree, or null when every node must be kept
	 */
	private static PathNode neededPaths(String[] pathElementArray, int start, String[] csvProperties) {
		StringBuilder entry = new StringBuilder();
		for (int e = start; e < pathElementArray.length; e++) {
			String pathElement = pathElementArray[e];
			if (pathElement.isEmpty()) {
				continue;
			}
			// A wildcard expands the children of the map: keep everything
			if ("*".equals(pathElement)) {
				return null;
			}
			entry.append('/').append("\\*".equals(pathElement) ? "*" : pathElement);
		}

		PathNode root = new PathNode();
		if (!addNeededPath(root, entry.toString())) {
			return null;
		}
		for (String property : csvProperties) {
			String path = property.equals(".") ? entry.toString() : entry + "/" + property;
			// Same resolution of ../ as for the rows; a reference above the unit is detected by appendRows()
			while (path.contains("/../")) {
				int pos2 = path.indexOf("/../");
				int pos1 = path.lastIndexOf("/", pos2 - 1);
				if (pos1 < 0) {
					return null;
				}
				path = path.substring(0, pos1) + path.substring(pos2 + 3);
			}
			if (!addNeededPath(root, path)) {
				return null;
			}
		}
		return root;
	}

	/**
	 * Add a path, relative to the unit, to the tree of the needed paths
	 *
	 * @param root The root of the tree
	 * @param path The path, starting with "/" (or empty for the unit itself)
	 * @return false when the path cannot be compared safely (non-ASCII characters)
	 */
	private static boolean addNeededPath(PathNode root, String path) {
		PathNode node = root;
		String[] segments = path.split("/", -1);
		// The first segment is the empty string before the leading "/"
		for (int s = 1; s < segments.length; s++) {
			String name = indexFree(segments[s]);
			if (name == null) {
				return false;
			}
			PathNode child = node.children.get(name);
			if (child == null) {
				child = new PathNode();
				node.children.put(name, child);
			}
			node = child;
		}
		return true;
	}

	/**
	 * Lower-cased path segment without its trailing array indexes
	 *
	 * @param segment The segment
	 * @return The normalized segment, null when it contains non-ASCII characters
	 */
	private static String indexFree(String segment) {
		for (int i = 0; i < segment.length(); i++) {
			if (segment.charAt(i) > 127) {
				return null;
			}
		}
		String name = segment;
		while (name.endsWith("]") && name.lastIndexOf('[') >= 0) {
			String index = name.substring(name.lastIndexOf('[') + 1, name.length() - 1);
			if (index.isEmpty() || !index.chars().allMatch(Character::isDigit)) {
				break;
			}
			name = name.substring(0, name.lastIndexOf('['));
		}
		return name.toLowerCase(Locale.ROOT);
	}

	/**
	 * Flatten the value that starts with the specified event into the map of this unit, as navigateTree() does
	 * for a value of a parsed document
	 *
	 * @param parser The parser, positioned on the first event of the value
	 * @param event The first event of the value
	 * @param path The path of the value
	 * @param needed The node of the needed paths that matches the value, null to keep everything
	 * @throws NotStreamableException when an object has a duplicate key
	 */
	private void flatten(JsonParser parser, Event event, String path, PathNode needed) throws NotStreamableException {
		switch (event) {
			case START_OBJECT:
				if (!removeNodes) {
					map.put(path, "{object}");
				}
				Set<String> keys = new HashSet<>();
				Event child;
				while ((child = parser.next()) != Event.END_OBJECT) {
					String key = parser.getString();
					// The default mode keeps the last value of a duplicate key
					if (!keys.add(key)) {
						throw new NotStreamableException();
					}
					child = parser.next();
					PathNode childNeeded = null;
					if (needed != null) {
						// A key with a "/" or an index, or with non-ASCII characters, is kept entirely
						String name = key.indexOf('/') >= 0 || key.indexOf('[') >= 0 || key.indexOf(']') >= 0
							? null
							: indexFree(key);
						if (name != null) {
							childNeeded = needed.children.get(name);
							if (childNeeded == null) {
								skipValue(parser, child);
								continue;
							}
						}
					}
					flatten(parser, child, path + "/" + key, childNeeded);
				}
				break;
			case START_ARRAY:
				if (!removeNodes) {
					map.put(path, "{array}");
				}
				int i = 0;
				Event element;
				while ((element = parser.next()) != Event.END_ARRAY) {
					// The elements of an array have the path of the array, without index
					flatten(parser, element, path + "[" + i + "]", needed);
					i++;
				}
				arrayPaths.add(path);
				arrayLengths.add(i);
				break;
			case VALUE_STRING:
				map.put(path, parser.getString());
				break;
			case VALUE_NUMBER:
				// Same JsonNumber, hence the same string, as in the parsed document
				map.put(path, parser.getValue().toString());
				break;
			case VALUE_TRUE:
				map.put(path, JsonValue.ValueType.TRUE.toString());
				break;
			case VALUE_FALSE:
				map.put(path, JsonValue.ValueType.FALSE.toString());
				break;
			case VALUE_NULL:
				map.put(path, JsonValue.ValueType.NULL.toString());
				break;
			default:
				break;
		}
	}
}
