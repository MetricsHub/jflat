package org.metricshub.jflat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class JFlatTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void flatMap(boolean streaming) throws IllegalStateException, ParseException, IOException {
		JFlat jFlat;

		jFlat = new JFlat(getResourceAsString("/simple.json"), streaming);
		jFlat.parse();
		assertEquals(getResourceAsString("/simple-flatMap.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/simple.json"), streaming);
		jFlat.parse(true);
		assertEquals(getResourceAsString("/simple-flatMap-removeNodes.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/complex.json"), streaming);
		jFlat.parse();
		assertEquals(getResourceAsString("/complex-flatMap.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/large.json"), streaming);
		jFlat.parse();
		assertEquals(getResourceAsString("/large-flatMap.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/object-keys.json"), streaming);
		jFlat.parse();
		assertEquals(getResourceAsString("/object-keys-flatMap.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(new StringReader(getResourceAsString("/object-keys.json")), streaming);
		jFlat.parse();
		assertEquals(getResourceAsString("/object-keys-flatMap.txt"), jFlat.getFlatTree().toString());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void edgeCases(boolean streaming) throws IllegalStateException, ParseException, IOException {
		// parse() not done
		JFlat simple = new JFlat(getResourceAsString("/simple.json"), streaming);
		assertThrows(
			IllegalStateException.class,
			() -> simple.getFlatTree(),
			"Non-parsed JSON document should throw an IllegalStateException"
		);
		assertThrows(
			IllegalStateException.class,
			() -> simple.toCSV("/", null, null),
			"Non-parsed JSON document should throw an IllegalStateException"
		);

		// empty JSON
		JFlat empty = new JFlat("", streaming);
		assertThrows(
			ParseException.class,
			() -> empty.parse(),
			"Empty JSON document should trigger a ParseException error"
		);

		// syntax error
		JFlat wrong = new JFlat("{ this: is a wrong JSON document", streaming);
		assertThrows(
			ParseException.class,
			() -> wrong.parse(),
			"JSON document with syntax error should trigger a ParseException error"
		);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void csv(boolean streaming) throws IllegalStateException, ParseException, IOException {
		JFlat simple = new JFlat(getResourceAsString("/simple.json"), streaming);
		simple.parse();
		assertEquals("[0];\n[1];\n", simple.toCSV("/", null, null).toString());
		assertEquals("[0]/attribute1;\n[1]/attribute1;\n", simple.toCSV("/attribute1", null, null).toString());
		assertEquals("[0]/attribute1;\n[1]/attribute1;\n", simple.toCSV("/*/attribute1", null, null).toString());
		assertEquals(
			"[0]/arrayA[0];\n[0]/arrayA[1];\n[0]/arrayA[2];\n[1]/arrayA[0];\n[1]/arrayA[1];\n[1]/arrayA[2];\n",
			simple.toCSV("/arrayA", null, null).toString()
		);
		assertEquals(
			"[0]/arrayB[0]/id;\n[0]/arrayB[1]/id;\n[0]/arrayB[2]/id;\n[1]/arrayB[0]/id;\n[1]/arrayB[1]/id;\n[1]/arrayB[2]/id;\n",
			simple.toCSV("/arrayB/id", null, null).toString()
		);
		assertEquals(
			"[0]/arrayB[0]/id;\n[0]/arrayB[1]/id;\n[0]/arrayB[2]/id;\n[1]/arrayB[0]/id;\n[1]/arrayB[1]/id;\n[1]/arrayB[2]/id;\n",
			simple.toCSV("/arrayB/*/id", null, null).toString()
		);
		assertEquals("", simple.toCSV("/nonexistent", null, null).toString());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void csvProperties(boolean streaming) throws IllegalStateException, ParseException, IOException {
		JFlat simple = new JFlat(getResourceAsString("/simple.json"), streaming);
		simple.parse();
		assertEquals("[0];{object};\n[1];{object};\n", simple.toCSV("/", new String[] { "." }, null).toString());
		assertEquals("[0];{array};\n[1];{array};\n", simple.toCSV("/", new String[] { "arrayA" }, null).toString());
		assertEquals(
			"[0]/attribute1;1;\n[1]/attribute1;2;\n",
			simple.toCSV("/attribute1", new String[] { "." }, null).toString()
		);
		assertEquals(
			"[0]/attribute1;1;;\n[1]/attribute1;2;;\n",
			simple.toCSV("/attribute1", new String[] { ".", "non-existent" }, null).toString()
		);
		assertEquals(
			"[0]/arrayA[0];value1;\n[0]/arrayA[1];value2;\n[0]/arrayA[2];value3;\n[1]/arrayA[0];value1;\n[1]/arrayA[1];value2;\n[1]/arrayA[2];value3;\n",
			simple.toCSV("/arrayA", new String[] { "." }, null).toString()
		);
		assertEquals(
			"[0]/arrayB[0];1;1;\n[0]/arrayB[1];2;1;\n[0]/arrayB[2];3;1;\n[1]/arrayB[0];1;2;\n[1]/arrayB[1];2;2;\n[1]/arrayB[2];3;2;\n",
			simple.toCSV("/arrayB", new String[] { "id", "../attribute1" }, null).toString()
		);

		assertEquals("[0] {object} \n[1] {object} \n", simple.toCSV("/", new String[] { "." }, " ").toString());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void csvWildcard(boolean streaming) throws IllegalStateException, ParseException, IOException {
		JFlat nodeDrives = new JFlat(getResourceAsString("/object-keys.json"), streaming);
		nodeDrives.parse();

		// Wildcard to list all drive entries
		assertEquals(
			"/members/2415********************************;\n" +
			"/members/4959********************************;\n" +
			"/members/93a3********************************;\n" +
			"/members/9f02********************************;\n",
			nodeDrives.toCSV("/members/*", null, ";").toString()
		);

		// Wildcard with id and name properties
		assertEquals(
			"/members/2415********************************;1;Internal Drive 1;\n" +
			"/members/4959********************************;0;Internal Drive 0;\n" +
			"/members/93a3********************************;0;Internal Drive 0;\n" +
			"/members/9f02********************************;1;Internal Drive 1;\n",
			nodeDrives.toCSV("/members/*", new String[] { "id", "name" }, ";").toString()
		);

		// Wildcard with nested paths
		assertEquals(
			"/members/2415********************************;Internal Drive 1;416Y******91;NVMe;\n" +
			"/members/4959********************************;Internal Drive 0;416Y******91;NVMe;\n" +
			"/members/93a3********************************;Internal Drive 0;214F******S1;NVMe;\n" +
			"/members/9f02********************************;Internal Drive 1;214F******S1;NVMe;\n",
			nodeDrives
				.toCSV("/members/*", new String[] { "name", "manufacturing/serialNumber", "type/default" }, ";")
				.toString()
		);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void csvWildcardEscape(boolean streaming) throws IllegalStateException, ParseException, IOException {
		JFlat jFlat = new JFlat(getResourceAsString("/wildcard-key.json"), streaming);
		jFlat.parse();

		// Wildcard "*" expands all children of "items"
		assertEquals(
			"/items/*;1;star;\n/items/alpha;2;alpha;\n/items/beta;3;beta;\n",
			jFlat.toCSV("/items/*", new String[] { "id", "name" }, ";").toString()
		);

		// Escaped "\*" targets only the literal "*" property
		assertEquals("/items/*;1;star;\n", jFlat.toCSV("/items/\\*", new String[] { "id", "name" }, ";").toString());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void csvList(boolean streaming) throws IllegalStateException, ParseException, IOException {
		String list =
			"{\"kind\":\"PodList\",\"items\":[" +
			"{\"metadata\":{\"name\":\"a\",\"labels\":{\"app\":\"x\"}},\"status\":{\"conditions\":[" +
			"{\"type\":\"Ready\",\"status\":\"True\"},{\"type\":\"Initialized\",\"status\":\"True\"}]}}," +
			"{\"metadata\":{\"name\":\"b\"},\"status\":{\"conditions\":[]}}," +
			"{\"metadata\":{\"name\":\"c\"},\"status\":{\"phase\":null,\"restarts\":1.50e1}}" +
			"]}";
		JFlat jFlat = new JFlat(list, streaming);
		jFlat.parse();

		// One row per element, in the order of the array
		assertEquals(
			"/items[0];a;x;\n/items[1];b;;\n/items[2];c;;\n",
			jFlat.toCSV("/items", new String[] { "metadata/name", "metadata/labels/app" }, ";").toString()
		);

		// Entry key below the elements, with properties of the element
		assertEquals(
			"/items[0]/status/conditions[0];Ready;a;\n/items[0]/status/conditions[1];Initialized;a;\n" +
			"/items[1]/status/conditions;;b;\n",
			jFlat.toCSV("/items/status/conditions", new String[] { "type", "../../metadata/name" }, ";").toString()
		);

		// Markers, null and numbers as the default mode writes them
		assertEquals(
			"/items[0]/status;{object};;;\n/items[1]/status;{object};;;\n/items[2]/status;{object};NULL;15.0;\n",
			jFlat.toCSV("items/status", new String[] { ".", "phase", "restarts" }, ";").toString()
		);

		// A property above the element
		assertEquals(
			"/items[0];a;PodList;\n/items[1];b;PodList;\n/items[2];c;PodList;\n",
			jFlat.toCSV("/items", new String[] { "metadata/name", "../kind" }, ";").toString()
		);

		// Case-insensitive entry key and properties, the IDs are written as in the document
		assertEquals(
			"/items[0];a;\n/items[1];b;\n/items[2];c;\n",
			jFlat.toCSV("/ITEMS", new String[] { "METADATA/NAME" }, ";").toString()
		);
	}

	/**
	 * The documents that the streaming mode must process as the default mode does
	 */
	@Test
	void streamingEdgeCases() {
		// Key "" in an element
		assertStreamingMatchesDefault("{\"items\":[{\"\":{\"name\":\"e\"},\"name\":\"n\"}]}", "/items", "spec/..//name");
		// Root key that contains "/" or "[", root key present twice
		assertStreamingMatchesDefault("{\"items\":{\"x\":{\"a\":1}},\"items/x\":{\"a\":2}}", "/items/x", "a");
		assertStreamingMatchesDefault("{\"items\":[{\"a\":1}],\"items[0]\":{\"a\":2}}", "/items", "a");
		assertStreamingMatchesDefault("{\"items\":[{\"a\":1}],\"Items\":[{\"a\":2}]}", "/items", "a");
		// Duplicate key in an element
		assertStreamingMatchesDefault(
			"{\"items\":[{\"a\":{\"b\":1},\"c\":3,\"a\":{\"d\":2}}]}",
			"/items",
			"a/b",
			"a/d",
			"c"
		);
		// Properties above the element and within the element
		assertStreamingMatchesDefault("{\"kind\":\"K\",\"items\":[{\"a\":1}]}", "/items", "a", "../kind");
		assertStreamingMatchesDefault(
			"{\"items\":[{\"name\":\"a\",\"spec\":{\"c\":[{\"x\":1},{\"x\":2}]}}]}",
			"/items/spec/c",
			"x",
			"../../name",
			"../../../../x"
		);
		// Wildcard below the value, below an element (where the default mode passes the entry through)
		assertStreamingMatchesDefault(
			"{\"items\":{\"spec\":{\"x\":{\"name\":\"a\"},\"y\":{\"name\":\"b\"}}}}",
			"/items/spec/*",
			"name"
		);
		assertStreamingMatchesDefault(
			"{\"items\":[{\"spec\":{\"x\":{\"name\":\"a\"},\"y\":{\"name\":\"b\"}}},[1,[2]]]}",
			"/items/spec/*",
			"name"
		);
		assertStreamingMatchesDefault("{\"items\":[[1,2],[3],{\"a\":[4]}]}", "/items/*", ".");
		assertStreamingMatchesDefault("{\"items\":[[1,2],[3],{\"a\":[4]}]}", "/items/*/a", ".");
		// Empty list, scalar, null, escaped wildcard
		assertStreamingMatchesDefault("{\"items\":[]}", "/items", ".");
		assertStreamingMatchesDefault("{\"items\":[]}", "/items/*", ".");
		assertStreamingMatchesDefault("{\"items\":\"x\"}", "/items", ".");
		assertStreamingMatchesDefault("{\"items\":null}", "items", ".");
		assertStreamingMatchesDefault("{\"*\":[{\"a\":1}],\"b\":2}", "/\\*", "a", "../b");
		// Keys equal whatever the case, non-ASCII keys
		assertStreamingMatchesDefault(
			"{\"items\":[{\"Name\":\"a\",\"name\":\"b\",\"K\":1,\"k\":2}]}",
			"/ITEMS",
			"NAME",
			"k"
		);
		assertStreamingMatchesDefault("{\"items\":[{\"été\":{\"a\":1},\"b\":2}]}", "/items", "ÉTÉ/a", "b");
		// Numbers, trailing content, number out of range
		assertStreamingMatchesDefault("{\"items\":[{\"a\":1.50e1,\"b\":-0,\"c\":1E+2}]} trailing", "/items", "a", "b", "c");
		assertStreamingMatchesDefault("{\"items\":[{\"a\":1}],\"b\":1e99999999999}", "/items", "a");
	}

	/**
	 * Assert that the streaming mode gives the same result as the default mode, with and without the nodes
	 * without value
	 *
	 * @param json The document
	 * @param entryKey The entry key
	 * @param properties The properties
	 */
	private static void assertStreamingMatchesDefault(String json, String entryKey, String... properties) {
		boolean[] streamed = new boolean[1];
		for (boolean removeNodes : new boolean[] { false, true }) {
			assertEquals(
				run(new JFlat(json), removeNodes, entryKey, properties.clone(), ";", streamed),
				run(new JFlat(json, true), removeNodes, entryKey, properties.clone(), ";", streamed),
				json
			);
		}
	}

	/**
	 * The streaming mode gives the same result as the default mode, or throws the same exception, on generated
	 * documents, entry keys and properties
	 */
	@Test
	void streamingMatchesDefault() {
		Random random = new Random(20261008L);
		int streamed = 0;
		int calls = 0;
		for (int d = 0; d < 4000; d++) {
			String json = randomDocument(random);
			for (int k = 0; k < 6; k++) {
				String entryKey = randomEntryKey(random);
				String[] properties = randomProperties(random);
				String separator = SEPARATORS[random.nextInt(SEPARATORS.length)];
				boolean removeNodes = random.nextBoolean();

				boolean[] streamedCall = new boolean[1];
				String expected = run(new JFlat(json), removeNodes, entryKey, properties.clone(), separator, streamedCall);
				String actual = run(new JFlat(json, true), removeNodes, entryKey, properties.clone(), separator, streamedCall);
				assertEquals(
					expected,
					actual,
					"Document: " + json + "\nEntry key: " + entryKey + "\nProperties: " + String.join(", ", properties)
				);

				calls++;
				if (streamedCall[0]) {
					streamed++;
				}
			}
		}

		// Most calls must be streamed, not processed as in the default mode
		assertTrue(streamed > calls / 2, streamed + " streamed calls out of " + calls);
	}

	/**
	 * Parse and convert a document twice, then dump the flat tree
	 *
	 * @param streamed Set to whether the conversions were streamed (without the map of the whole document)
	 * @return The CSV and the flat tree, or the exception
	 */
	private static String run(
		JFlat jFlat,
		boolean removeNodes,
		String entryKey,
		String[] properties,
		String separator,
		boolean[] streamed
	) {
		StringBuilder result = new StringBuilder();
		streamed[0] = false;
		try {
			jFlat.parse(removeNodes);
			result.append(jFlat.toCSV(entryKey, properties, separator));
			result.append(jFlat.toCSV(entryKey, properties, separator));
			Field fullMap = JFlat.class.getDeclaredField("fullMap");
			fullMap.setAccessible(true);
			streamed[0] = !(Boolean) fullMap.get(jFlat);
			result.append("--\n").append(jFlat.getFlatTree());
		} catch (Exception e) {
			result.append(e.getClass().getName()).append(": ").append(e.getMessage());
		}
		return result.toString();
	}

	private static final String[] SEPARATORS = { null, ";", "," };

	private static final String[] KEYS = {
		"items",
		"items",
		"items",
		"Items",
		"metadata",
		"name",
		"Name",
		"kind",
		"spec",
		"status",
		"conditions",
		"containers",
		"id",
		"*",
		"",
		"..",
		".",
		"a/b",
		"x[0]",
		"x]",
		"items/name",
		"items[0]",
		"été",
		"K",
		"K"
	};

	private static final String[] ROOT_KEYS = { "kind", "apiVersion", "metadata", "status" };

	private static final String[] NUMBERS = {
		"0",
		"-0",
		"1",
		"-12",
		"1.50",
		"1e3",
		"1E-2",
		"2.0e+10",
		"12345678901234567890",
		"0.000"
	};

	private static final String[] STRINGS = {
		"",
		"a",
		"B",
		"true",
		"{object}",
		"x;y",
		"line\\nbreak",
		"\\u00e9",
		"\\\"q\\\""
	};

	private static final String[] ENTRY_ELEMENTS = {
		"items",
		"items",
		"items",
		"Items",
		"metadata",
		"name",
		"spec",
		"status",
		"conditions",
		"containers",
		"containers[0]",
		"x[0]",
		"*",
		"\\*",
		"..",
		"",
		"kind",
		"été"
	};

	private static final String[] PROPERTIES = {
		".",
		"name",
		"NAME",
		"id",
		"kind",
		"../kind",
		"../../kind",
		"../name",
		"../../name",
		"../../../x",
		"spec/name",
		"./name",
		"/name",
		"//name",
		"nonexistent",
		"a/b",
		"*",
		"..",
		"",
		"./",
		"spec/../name",
		"spec/..//name",
		"containers[0]/name",
		"conditions[1]",
		"x[0]",
		"metadata/name",
		"status/conditions",
		"été",
		"k"
	};

	private static String randomDocument(Random random) {
		switch (random.nextInt(20)) {
			case 0:
				return randomArray(random, 1);
			case 1:
				return randomValue(random, 4);
			case 2:
				return "{\"items\":[" + randomObject(random, 2) + "," + randomObject(random, 2);
			default:
				StringBuilder json = new StringBuilder("{");
				int keys = random.nextInt(5);
				for (int i = 0; i < keys; i++) {
					json.append(randomRootKey(random)).append(":").append(randomValue(random, 1)).append(",");
				}
				json.append("\"items\":");
				json.append(random.nextInt(4) == 0 ? randomValue(random, 1) : randomList(random));
				keys = random.nextInt(3);
				for (int i = 0; i < keys; i++) {
					json.append(",").append(randomRootKey(random)).append(":").append(randomValue(random, 1));
				}
				return json.append('}').append(random.nextInt(10) == 0 ? " trailing" : "").toString();
		}
	}

	private static String randomList(Random random) {
		StringBuilder json = new StringBuilder("[");
		int length = random.nextInt(5);
		for (int i = 0; i < length; i++) {
			if (i > 0) {
				json.append(',');
			}
			json.append(random.nextInt(8) == 0 ? randomValue(random, 2) : randomObject(random, 2));
		}
		return json.append(']').toString();
	}

	private static String randomKey(Random random) {
		return "\"" + KEYS[random.nextInt(KEYS.length)] + "\"";
	}

	private static String randomRootKey(Random random) {
		return random.nextInt(6) == 0 ? randomKey(random) : "\"" + ROOT_KEYS[random.nextInt(ROOT_KEYS.length)] + "\"";
	}

	private static String randomValue(Random random, int depth) {
		switch (random.nextInt(depth > 4 ? 6 : 9)) {
			case 0:
				return "\"" + STRINGS[random.nextInt(STRINGS.length)] + "\"";
			case 1:
			case 2:
				return NUMBERS[random.nextInt(NUMBERS.length)];
			case 3:
				return "true";
			case 4:
				return "false";
			case 5:
				return "null";
			case 6:
			case 7:
				return randomObject(random, depth + 1);
			default:
				return randomArray(random, depth + 1);
		}
	}

	private static String randomObject(Random random, int depth) {
		StringBuilder json = new StringBuilder("{");
		int keys = random.nextInt(6);
		for (int i = 0; i < keys; i++) {
			if (i > 0) {
				json.append(',');
			}
			json.append(randomKey(random)).append(':').append(randomValue(random, depth));
		}
		return json.append('}').toString();
	}

	private static String randomArray(Random random, int depth) {
		StringBuilder json = new StringBuilder("[");
		int length = random.nextInt(4);
		for (int i = 0; i < length; i++) {
			if (i > 0) {
				json.append(',');
			}
			json.append(randomValue(random, depth));
		}
		return json.append(']').toString();
	}

	private static String randomEntryKey(Random random) {
		StringBuilder entryKey = new StringBuilder(random.nextInt(5) == 0 ? "" : "/");
		int length = random.nextInt(20) == 0 ? 0 : 1 + random.nextInt(4);
		for (int i = 0; i < length; i++) {
			if (i > 0) {
				entryKey.append('/');
			}
			entryKey.append(
				i == 0 && random.nextInt(4) > 0 ? "items" : ENTRY_ELEMENTS[random.nextInt(ENTRY_ELEMENTS.length)]
			);
		}
		return entryKey.toString();
	}

	private static String[] randomProperties(Random random) {
		String[] properties = new String[random.nextInt(5)];
		for (int i = 0; i < properties.length; i++) {
			properties[i] = PROPERTIES[random.nextInt(PROPERTIES.length)];
		}
		return properties;
	}

	/**
	 * Reads the specified resource file and returns its content as a String
	 *
	 * @param path Path to the resource file
	 * @return The content of the resource file as a String
	 */
	private static String getResourceAsString(String path) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(JFlatTest.class.getResourceAsStream(path)));
		StringBuilder builder = new StringBuilder();
		String l;
		try {
			while ((l = reader.readLine()) != null) {
				builder.append(l).append('\n');
			}
		} catch (IOException e) {
			return null;
		}

		return builder.toString();
	}
}
