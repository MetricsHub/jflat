package org.metricshub.jflat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class JFlatTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void flatMap(boolean eventParsing) throws IllegalStateException, ParseException, IOException {
		JFlat jFlat;

		jFlat = new JFlat(getResourceAsString("/simple.json"), eventParsing);
		jFlat.parse();
		assertEquals(getResourceAsString("/simple-flatMap.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/simple.json"), eventParsing);
		jFlat.parse(true);
		assertEquals(getResourceAsString("/simple-flatMap-removeNodes.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/complex.json"), eventParsing);
		jFlat.parse();
		assertEquals(getResourceAsString("/complex-flatMap.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/large.json"), eventParsing);
		jFlat.parse();
		assertEquals(getResourceAsString("/large-flatMap.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/object-keys.json"), eventParsing);
		jFlat.parse();
		assertEquals(getResourceAsString("/object-keys-flatMap.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(new StringReader(getResourceAsString("/object-keys.json")), eventParsing);
		jFlat.parse();
		assertEquals(getResourceAsString("/object-keys-flatMap.txt"), jFlat.getFlatTree().toString());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void edgeCases(boolean eventParsing) throws IllegalStateException, ParseException, IOException {
		// parse() not done
		JFlat simple = new JFlat(getResourceAsString("/simple.json"), eventParsing);
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
		JFlat empty = new JFlat("", eventParsing);
		assertThrows(
			ParseException.class,
			() -> empty.parse(),
			"Empty JSON document should trigger a ParseException error"
		);

		// syntax error
		JFlat wrong = new JFlat("{ this: is a wrong JSON document", eventParsing);
		assertThrows(
			ParseException.class,
			() -> wrong.parse(),
			"JSON document with syntax error should trigger a ParseException error"
		);

		// reader that fails: IOException, and the reader is closed
		boolean[] closed = new boolean[1];
		Reader failing = new Reader() {
			@Override
			public int read(char[] buffer, int offset, int length) throws IOException {
				throw new IOException("Read failure");
			}

			@Override
			public void close() {
				closed[0] = true;
			}
		};
		JFlat broken = new JFlat(failing, eventParsing);
		assertThrows(IOException.class, () -> broken.parse(), "A reader that fails should trigger an IOException");
		assertTrue(closed[0], "The reader should be closed");
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void csv(boolean eventParsing) throws IllegalStateException, ParseException, IOException {
		JFlat simple = new JFlat(getResourceAsString("/simple.json"), eventParsing);
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
	void csvProperties(boolean eventParsing) throws IllegalStateException, ParseException, IOException {
		JFlat simple = new JFlat(getResourceAsString("/simple.json"), eventParsing);
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
	void csvWildcard(boolean eventParsing) throws IllegalStateException, ParseException, IOException {
		JFlat nodeDrives = new JFlat(getResourceAsString("/object-keys.json"), eventParsing);
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
	void csvWildcardEscape(boolean eventParsing) throws IllegalStateException, ParseException, IOException {
		JFlat jFlat = new JFlat(getResourceAsString("/wildcard-key.json"), eventParsing);
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
	void csvList(boolean eventParsing) throws IllegalStateException, ParseException, IOException {
		String list =
			"{\"kind\":\"PodList\",\"items\":[" +
			"{\"metadata\":{\"name\":\"a\",\"labels\":{\"app\":\"x\"}},\"status\":{\"conditions\":[" +
			"{\"type\":\"Ready\",\"status\":\"True\"},{\"type\":\"Initialized\",\"status\":\"True\"}]}}," +
			"{\"metadata\":{\"name\":\"b\"},\"status\":{\"conditions\":[]}}," +
			"{\"metadata\":{\"name\":\"c\"},\"status\":{\"phase\":null,\"restarts\":1.50e1}}" +
			"]}";
		JFlat jFlat = new JFlat(list, eventParsing);
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

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void table(boolean eventParsing) throws IllegalStateException, ParseException, IOException {
		String list =
			"{\"kind\":\"PodList\",\"items\":[" +
			"{\"metadata\":{\"name\":\"a\",\"labels\":{\"app\":\"x\"}},\"status\":{\"conditions\":[" +
			"{\"type\":\"Ready\",\"status\":\"True\"},{\"type\":\"Initialized\",\"status\":\"True\"}]}}," +
			"{\"metadata\":{\"name\":\"b\"},\"status\":{\"conditions\":[]}}," +
			"{\"metadata\":{\"name\":\"line1\\nline2;c\"},\"status\":{\"phase\":null,\"restarts\":1.50e1}}" +
			"]}";
		JFlat jFlat = new JFlat(list, eventParsing);
		jFlat.parse();

		// Nested objects, missing property, a value with a line feed and the separator kept whole
		assertEquals(
			Arrays.asList(
				Arrays.asList("/items[0]", "a", "x"),
				Arrays.asList("/items[1]", "b", ""),
				Arrays.asList("/items[2]", "line1\nline2;c", "")
			),
			jFlat.toTable("/items", new String[] { "metadata/name", "metadata/labels/app" })
		);
		// toCSV() writes the same rows, which cannot be cut back into rows and cells
		assertEquals(
			"/items[0];a;x;\n/items[1];b;;\n/items[2];line1\nline2;c;;\n",
			jFlat.toCSV("/items", new String[] { "metadata/name", "metadata/labels/app" }, ";").toString()
		);

		// Arrays below the elements, ../ properties
		assertEquals(
			Arrays.asList(
				Arrays.asList("/items[0]/status/conditions[0]", "Ready", "a", "PodList"),
				Arrays.asList("/items[0]/status/conditions[1]", "Initialized", "a", "PodList"),
				Arrays.asList("/items[1]/status/conditions", "", "b", "PodList")
			),
			jFlat.toTable("/items/status/conditions", new String[] { "type", "../../metadata/name", "../../../kind" })
		);

		// Markers, null and numbers, case-insensitive entry key and properties
		assertEquals(
			Arrays.asList(
				Arrays.asList("/items[0]/status", "{object}", "", ""),
				Arrays.asList("/items[1]/status", "{object}", "", ""),
				Arrays.asList("/items[2]/status", "{object}", "NULL", "15.0")
			),
			jFlat.toTable("ITEMS/STATUS", new String[] { ".", "PHASE", "restarts" })
		);

		// No properties, no entry
		assertEquals(
			Arrays.asList(Arrays.asList("/items[0]"), Arrays.asList("/items[1]"), Arrays.asList("/items[2]")),
			jFlat.toTable("/items", null)
		);
		assertEquals(Arrays.asList(), jFlat.toTable("/nonexistent", new String[] { "." }));

		// Arguments
		assertThrows(IllegalArgumentException.class, () -> jFlat.toTable(null, null));
		assertThrows(IllegalArgumentException.class, () -> jFlat.toTable("/items", new String[] { "a", null }));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void tableWildcard(boolean eventParsing) throws IllegalStateException, ParseException, IOException {
		// Children of an object
		JFlat wildcard = new JFlat(getResourceAsString("/wildcard-key.json"), eventParsing);
		wildcard.parse();
		assertEquals(
			Arrays.asList(
				Arrays.asList("/items/*", "1", "star"),
				Arrays.asList("/items/alpha", "2", "alpha"),
				Arrays.asList("/items/beta", "3", "beta")
			),
			wildcard.toTable("/items/*", new String[] { "id", "name" })
		);

		// Elements of arrays: root array, then arrayB
		JFlat simple = new JFlat(getResourceAsString("/simple.json"), eventParsing);
		simple.parse();
		assertEquals(
			Arrays.asList(
				Arrays.asList("[0]/arrayB[0]", "1", "1"),
				Arrays.asList("[0]/arrayB[1]", "2", "1"),
				Arrays.asList("[0]/arrayB[2]", "3", "1"),
				Arrays.asList("[1]/arrayB[0]", "1", "2"),
				Arrays.asList("[1]/arrayB[1]", "2", "2"),
				Arrays.asList("[1]/arrayB[2]", "3", "2")
			),
			simple.toTable("/*/arrayB/*", new String[] { "id", "../attribute1" })
		);
		assertEquals(
			Arrays.asList(Arrays.asList("[0]/arrayA[0]", "value1"), Arrays.asList("[0]/arrayA[1]", "value2")),
			simple.toTable("/arrayA", new String[] { "." }).subList(0, 2)
		);
	}

	/**
	 * In event parsing mode, the rows are written unit after unit: when a later unit makes the event parsing give up
	 * (here, a duplicate key in the second element), the rows written so far are dropped and the whole document is
	 * processed again, so no row is written twice.
	 */
	@Test
	void eventParsingFallbackWritesEachRowOnce() throws IllegalStateException, ParseException, IOException {
		String json = "{\"items\":[{\"a\":\"1\"},{\"a\":\"2\",\"a\":\"3\"},{\"a\":\"4\"}]}";
		JFlat tree = new JFlat(json, false);
		tree.parse();
		JFlat events = new JFlat(json, true);
		events.parse();

		assertEquals(
			"/items[0];1;\n/items[1];3;\n/items[2];4;\n",
			tree.toCSV("/items", new String[] { "a" }, ";").toString()
		);
		assertEquals(
			tree.toCSV("/items", new String[] { "a" }, ";").toString(),
			events.toCSV("/items", new String[] { "a" }, ";").toString()
		);
		assertEquals(tree.toTable("/items", new String[] { "a" }), events.toTable("/items", new String[] { "a" }));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void flatMapView(boolean eventParsing) throws IllegalStateException, ParseException, IOException {
		// Before parse()
		JFlat notParsed = new JFlat("{}", eventParsing);
		assertThrows(IllegalStateException.class, () -> notParsed.getFlatMap());
		assertThrows(IllegalStateException.class, () -> notParsed.toTable("/", null));

		// The same entries as the flat tree
		JFlat simple = new JFlat(getResourceAsString("/simple.json"), eventParsing);
		simple.parse();
		simple.toCSV("/arrayB", new String[] { "id" }, ";");
		SortedMap<String, String> map = simple.getFlatMap();
		StringBuilder tree = new StringBuilder();
		for (Map.Entry<String, String> entry : map.entrySet()) {
			tree.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
		}
		assertEquals(getResourceAsString("/simple-flatMap.txt"), tree.toString());
		assertEquals("{array}", map.get("/"));
		assertEquals("[0]", map.headMap("[0]/").lastKey());

		// Case-insensitive keys
		assertEquals(String.CASE_INSENSITIVE_ORDER, map.comparator());
		assertEquals("2", map.get("[0]/ARRAYB[1]/ID"));

		// Unmodifiable
		assertThrows(UnsupportedOperationException.class, () -> map.put("/x", "y"));
		assertThrows(UnsupportedOperationException.class, () -> map.remove("/"));
		assertThrows(UnsupportedOperationException.class, () -> map.entrySet().iterator().next().setValue("x"));

		// Values of the literals and containers, keys that differ only by case
		String literals =
			"{\"t\":true,\"f\":false,\"n\":null,\"num\":1.50e1,\"s\":\"a\\nb\",\"o\":{},\"a\":[[1]],\"Name\":\"x\",\"name\":\"y\"}";
		JFlat jFlat = new JFlat(literals, eventParsing);
		jFlat.parse();
		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("/", "{object}");
		expected.put("/a", "{array}");
		expected.put("/a[0]", "{array}");
		expected.put("/a[0][0]", "1");
		expected.put("/f", "FALSE");
		expected.put("/n", "NULL");
		expected.put("/Name", "y");
		expected.put("/num", "15.0");
		expected.put("/o", "{object}");
		expected.put("/s", "a\nb");
		expected.put("/t", "TRUE");
		assertEquals(new ArrayList<>(expected.entrySet()), new ArrayList<>(jFlat.getFlatMap().entrySet()));

		// Without the nodes without value
		JFlat removeNodes = new JFlat(literals, eventParsing);
		removeNodes.parse(true);
		expected.keySet().removeAll(Arrays.asList("/", "/a", "/a[0]", "/o"));
		assertEquals(new ArrayList<>(expected.entrySet()), new ArrayList<>(removeNodes.getFlatMap().entrySet()));
	}

	/**
	 * The documents that the event parsing mode must process as the default mode does
	 */
	@Test
	void eventParsingEdgeCases() {
		// Key "" in an element
		assertEventParsingMatchesDefault("{\"items\":[{\"\":{\"name\":\"e\"},\"name\":\"n\"}]}", "/items", "spec/..//name");
		// Root key that contains "/" or "[", root key present twice
		assertEventParsingMatchesDefault("{\"items\":{\"x\":{\"a\":1}},\"items/x\":{\"a\":2}}", "/items/x", "a");
		assertEventParsingMatchesDefault("{\"items\":[{\"a\":1}],\"items[0]\":{\"a\":2}}", "/items", "a");
		assertEventParsingMatchesDefault("{\"items\":[{\"a\":1}],\"Items\":[{\"a\":2}]}", "/items", "a");
		// Duplicate key in an element
		assertEventParsingMatchesDefault(
			"{\"items\":[{\"a\":{\"b\":1},\"c\":3,\"a\":{\"d\":2}}]}",
			"/items",
			"a/b",
			"a/d",
			"c"
		);
		// Properties above the element and within the element
		assertEventParsingMatchesDefault("{\"kind\":\"K\",\"items\":[{\"a\":1}]}", "/items", "a", "../kind");
		assertEventParsingMatchesDefault(
			"{\"items\":[{\"name\":\"a\",\"spec\":{\"c\":[{\"x\":1},{\"x\":2}]}}]}",
			"/items/spec/c",
			"x",
			"../../name",
			"../../../../x"
		);
		// Wildcard below the value, below an element (where the default mode passes the entry through)
		assertEventParsingMatchesDefault(
			"{\"items\":{\"spec\":{\"x\":{\"name\":\"a\"},\"y\":{\"name\":\"b\"}}}}",
			"/items/spec/*",
			"name"
		);
		assertEventParsingMatchesDefault(
			"{\"items\":[{\"spec\":{\"x\":{\"name\":\"a\"},\"y\":{\"name\":\"b\"}}},[1,[2]]]}",
			"/items/spec/*",
			"name"
		);
		assertEventParsingMatchesDefault("{\"items\":[[1,2],[3],{\"a\":[4]}]}", "/items/*", ".");
		assertEventParsingMatchesDefault("{\"items\":[[1,2],[3],{\"a\":[4]}]}", "/items/*/a", ".");
		// Empty list, scalar, null, escaped wildcard
		assertEventParsingMatchesDefault("{\"items\":[]}", "/items", ".");
		assertEventParsingMatchesDefault("{\"items\":[]}", "/items/*", ".");
		assertEventParsingMatchesDefault("{\"items\":\"x\"}", "/items", ".");
		assertEventParsingMatchesDefault("{\"items\":null}", "items", ".");
		assertEventParsingMatchesDefault("{\"*\":[{\"a\":1}],\"b\":2}", "/\\*", "a", "../b");
		// Keys equal whatever the case, non-ASCII keys
		assertEventParsingMatchesDefault(
			"{\"items\":[{\"Name\":\"a\",\"name\":\"b\",\"K\":1,\"k\":2}]}",
			"/ITEMS",
			"NAME",
			"k"
		);
		assertEventParsingMatchesDefault("{\"items\":[{\"été\":{\"a\":1},\"b\":2}]}", "/items", "ÉTÉ/a", "b");
		// Numbers, trailing content, number out of range
		assertEventParsingMatchesDefault(
			"{\"items\":[{\"a\":1.50e1,\"b\":-0,\"c\":1E+2}]} trailing",
			"/items",
			"a",
			"b",
			"c"
		);
		assertEventParsingMatchesDefault("{\"items\":[{\"a\":1}],\"b\":1e99999999999}", "/items", "a");
	}

	/**
	 * Assert that the event parsing mode gives the same result as the default mode, with and without the nodes
	 * without value
	 *
	 * @param json The document
	 * @param entryKey The entry key
	 * @param properties The properties
	 */
	private static void assertEventParsingMatchesDefault(String json, String entryKey, String... properties) {
		boolean[] byEvents = new boolean[1];
		for (boolean removeNodes : new boolean[] { false, true }) {
			assertEquals(
				run(new JFlat(json), removeNodes, entryKey, properties.clone(), ";", byEvents),
				run(new JFlat(json, true), removeNodes, entryKey, properties.clone(), ";", byEvents),
				json
			);
		}
	}

	/**
	 * The event parsing mode gives the same result as the default mode, or throws the same exception, on generated
	 * documents, entry keys and properties
	 */
	@Test
	void eventParsingMatchesDefault() {
		Random random = new Random(20261008L);
		int byEvents = 0;
		int calls = 0;
		for (int d = 0; d < 4000; d++) {
			String json = randomDocument(random);
			for (int k = 0; k < 6; k++) {
				String entryKey = randomEntryKey(random);
				String[] properties = randomProperties(random);
				String separator = SEPARATORS[random.nextInt(SEPARATORS.length)];
				boolean removeNodes = random.nextBoolean();

				boolean[] byEventsCall = new boolean[1];
				String expected = run(new JFlat(json), removeNodes, entryKey, properties.clone(), separator, byEventsCall);
				String actual = run(new JFlat(json, true), removeNodes, entryKey, properties.clone(), separator, byEventsCall);
				assertEquals(
					expected,
					actual,
					"Document: " + json + "\nEntry key: " + entryKey + "\nProperties: " + String.join(", ", properties)
				);

				calls++;
				if (byEventsCall[0]) {
					byEvents++;
				}
			}
		}

		// Most calls must be read by events, not processed as in the default mode
		assertTrue(byEvents > calls / 2, byEvents + " calls read by events out of " + calls);
	}

	/**
	 * Parse and convert a document twice, then dump the flat tree
	 *
	 * @param byEvents Set to whether the conversions were read by events (without the map of the whole document)
	 * @return The CSV and the flat tree, or the exception
	 */
	private static String run(
		JFlat jFlat,
		boolean removeNodes,
		String entryKey,
		String[] properties,
		String separator,
		boolean[] byEvents
	) {
		StringBuilder result = new StringBuilder();
		byEvents[0] = false;
		try {
			jFlat.parse(removeNodes);
			result.append(jFlat.toCSV(entryKey, properties, separator));
			result.append(jFlat.toCSV(entryKey, properties, separator));
			Field fullMap = JFlat.class.getDeclaredField("fullMap");
			fullMap.setAccessible(true);
			byEvents[0] = !(Boolean) fullMap.get(jFlat);
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
	 * toCSV() writes exactly what it wrote before it was built on toTable(): src/test/resources/toCSV-golden.txt was
	 * captured with {@link #csvGoldenLines()} from toCSV() of version 1.2.01, then regenerated once when toTable()
	 * stopped cleaning the caller's properties array (the only change: no more "properties after=") and the random
	 * documents went from 300 to 50 (the first 50 are unchanged)
	 */
	@Test
	void csvGolden() {
		assertIterableEquals(Arrays.asList(getResourceAsString("/toCSV-golden.txt").split("\n")), csvGoldenLines());
	}

	private static final String[] GOLDEN_KEYS = { "/", "", "/*", "/nonexistent" };

	private static final String[][] GOLDEN_PROPERTIES = {
		null,
		{},
		{ "." },
		{ "nonexistent" },
		{ "./x", "/x", "//x", "", ".." }
	};

	private static final String[] GOLDEN_SEPARATORS = { null, ";", ",", " ", "", "||", "\n", "\t" };

	/**
	 * The toCSV() outputs of the golden test: the test documents, edge cases and random documents, with each entry
	 * key, properties, separator, parsing mode and removeNodes
	 *
	 * @return One line per call, with the call and its output (or its SHA-256 when it is long), or its exception
	 */
	static List<String> csvGoldenLines() {
		List<String> lines = new ArrayList<>();
		goldenGroup(
			lines,
			"simple",
			getResourceAsString("/simple.json"),
			new String[] {
				"/attribute1",
				"/*/attribute1",
				"/arrayA",
				"/arrayA/*",
				"/arrayB",
				"/arrayB/id",
				"/arrayB/*/id",
				"/arrayB[*]",
				"[0]/arrayB"
			},
			new String[][] { { "id", "value" }, { "id", "../attribute1" }, { "../lastAttribute", "../../x" }, { "arrayA" } }
		);
		goldenGroup(
			lines,
			"complex",
			getResourceAsString("/complex.json"),
			new String[] { "/children", "/children/costumes", "/children/*/costumes" },
			new String[][] { { "objName", "scale", "isDraggable" }, { "costumeName", "baseLayerID", "../objName" } }
		);
		goldenGroup(
			lines,
			"large",
			getResourceAsString("/large.json"),
			new String[] { "/groupsState", "/groupsState/groupCopiesState", "/clustersPhoenixClustersState" },
			new String[][] {
				{ "groupUID/id", "enabled" },
				{ "enabled", "journalState", "copyUID/globalCopyUID/clusterUID/id", "../groupUID/id", "../../x" },
				{ "clusterName", "cluster/id", "status" }
			}
		);
		goldenGroup(
			lines,
			"object-keys",
			getResourceAsString("/object-keys.json"),
			new String[] { "/members/*", "/members", "/members/*/displayname/args" },
			new String[][] {
				{ "id", "name" },
				{ "name", "manufacturing/serialNumber", "type/default" },
				{ "../arraySerialNumber", "../../arraySerialNumber", "../../../arraySerialNumber" }
			}
		);
		goldenGroup(
			lines,
			"wildcard-key",
			getResourceAsString("/wildcard-key.json"),
			new String[] { "/items", "/items/*", "/items/\\*" },
			new String[][] { { "id", "name" } }
		);
		goldenGroup(
			lines,
			"list",
			"{\"kind\":\"PodList\",\"items\":[" +
			"{\"metadata\":{\"name\":\"a\",\"labels\":{\"app\":\"x\"}},\"status\":{\"conditions\":[" +
			"{\"type\":\"Ready\",\"status\":\"True\"},{\"type\":\"Initialized\",\"status\":\"True\"}]}}," +
			"{\"metadata\":{\"name\":\"b\"},\"status\":{\"conditions\":[]}}," +
			"{\"metadata\":{\"name\":\"c\"},\"status\":{\"phase\":null,\"restarts\":1.50e1}}" +
			"]}",
			new String[] { "/items", "/items/status/conditions", "items/status", "/ITEMS", "/items/*" },
			new String[][] {
				{ "metadata/name", "metadata/labels/app" },
				{ "type", "../../metadata/name" },
				{ ".", "phase", "restarts" },
				{ "metadata/name", "../kind" },
				{ "METADATA/NAME" }
			}
		);
		goldenGroup(
			lines,
			"special-values",
			"{\"items\":[{\"name\":\"line1\\nline2\",\"v\":\"a;b\",\"w\":\"x,y\",\"t\":\"tab\\there\",\"crlf\":\"a\\r\\nb\"," +
			"\"sep\":\"||\",\"u\":\"été ჻\"},{\"name\":\"\",\"v\":null,\"w\":true,\"t\":false,\"crlf\":1.50e1," +
			"\"sep\":-0}]}",
			new String[] { "/items", "/items/*" },
			new String[][] { { "name", "v", "w", "t", "crlf", "sep", "u" } }
		);
		goldenGroup(
			lines,
			"empty-containers",
			"{\"items\":[],\"o\":{},\"a\":[[],[1,[2,3]],{}]}",
			new String[] { "/items", "/o", "/a", "/a/*", "/o/*", "/a[1]" },
			new String[][] {}
		);
		goldenGroup(
			lines,
			"case",
			"{\"Items\":[{\"a\":1}],\"items\":[{\"a\":2}],\"ITEMS2\":{\"B\":3}}",
			new String[] { "/items", "/ITEMS2" },
			new String[][] { { "a", "b", "A" } }
		);
		goldenGroup(
			lines,
			"empty-key",
			"{\"\":{\"x\":1},\"k\":{\"\":{\"y\":2}}}",
			new String[] { "//", "/k/", "/k//" },
			new String[][] { { "x", "/y", "y" } }
		);
		goldenGroup(
			lines,
			"trailing",
			"{\"items\":[{\"a\":1}]} trailing",
			new String[] { "/items" },
			new String[][] { { "a" } }
		);
		String[] scalars = { "\"scalar\"", "42", "null", "true", "[]", "{}", "[1,\"two\",null,{\"a\":1},[3]]", "{ wrong" };
		for (String scalar : scalars) {
			goldenGroup(lines, scalar, scalar, new String[] { "/a" }, new String[][] { { "a" } });
		}

		// Invalid arguments, and toCSV() before parse()
		String[] results = new String[3];
		for (boolean eventParsing : new boolean[] { false, true }) {
			JFlat jFlat = new JFlat("{\"a\":1}", eventParsing);
			String notParsed = goldenCsv(jFlat, "/", null, null);
			parse(jFlat, false);
			String nullKey = goldenCsv(jFlat, null, null, null);
			String nullProperty = goldenCsv(jFlat, "/", new String[] { "a", null }, null);
			results[0] = bothModes(results[0], notParsed);
			results[1] = bothModes(results[1], nullKey);
			results[2] = bothModes(results[2], nullProperty);
		}
		lines.add("not parsed -> " + results[0]);
		lines.add("null key -> " + results[1]);
		lines.add("null property -> " + results[2]);

		// Random documents, entry keys, properties and separators, with toCSV() called twice
		Random random = new Random(20261111L);
		for (int d = 0; d < 50; d++) {
			String json = randomDocument(random);
			for (int k = 0; k < 3; k++) {
				String entryKey = randomEntryKey(random);
				String[] properties = randomProperties(random);
				String separator = SEPARATORS[random.nextInt(SEPARATORS.length)];
				boolean removeNodes = random.nextBoolean();
				String result = null;
				for (boolean eventParsing : new boolean[] { false, true }) {
					JFlat jFlat = new JFlat(json, eventParsing);
					String[] clone = properties.clone();
					result =
						bothModes(
							result,
							parse(jFlat, removeNodes) +
							goldenCsv(jFlat, entryKey, clone, separator) +
							" then " +
							goldenCsv(jFlat, entryKey, clone, separator)
						);
				}
				lines.add("random " + d + "." + k + " -> " + result);
			}
		}
		return lines;
	}

	/**
	 * @param tree The result in the default mode, null when the call is in the default mode
	 * @param result The result of the call
	 * @return The result of the call in the default mode, followed by the result in event parsing mode when it differs
	 */
	private static String bothModes(String tree, String result) {
		if (tree == null) {
			return result;
		}
		return tree.equals(result) ? tree : tree + " | events -> " + result;
	}

	/**
	 * Add the golden lines of a document: each entry key and properties (with the generic ones), in both parsing
	 * modes, with and without the nodes without value
	 */
	private static void goldenGroup(
		List<String> lines,
		String name,
		String json,
		String[] keys,
		String[][] propertySets
	) {
		List<String> allKeys = new ArrayList<>(Arrays.asList(GOLDEN_KEYS));
		allKeys.addAll(Arrays.asList(keys));
		List<String[]> allProperties = new ArrayList<>(Arrays.asList(GOLDEN_PROPERTIES));
		allProperties.addAll(Arrays.asList(propertySets));
		for (int k = 0; k < allKeys.size(); k++) {
			String key = allKeys.get(k);
			for (boolean removeNodes : new boolean[] { false, true }) {
				JFlat tree = new JFlat(json);
				String treeParsed = parse(tree, removeNodes);
				JFlat events = new JFlat(json, true);
				String eventsParsed = parse(events, removeNodes);
				for (int p = 0; p < allProperties.size(); p++) {
					String separator = GOLDEN_SEPARATORS[(k * allProperties.size() + p) % GOLDEN_SEPARATORS.length];
					String[] properties = allProperties.get(p);
					lines.add(
						quote(name) +
						" removeNodes=" +
						removeNodes +
						" key=" +
						quote(key) +
						" properties=" +
						quote(properties) +
						" separator=" +
						quote(separator) +
						" -> " +
						bothModes(
							treeParsed + goldenCsv(tree, key, properties == null ? null : properties.clone(), separator),
							eventsParsed + goldenCsv(events, key, properties == null ? null : properties.clone(), separator)
						)
					);
				}
			}
		}
	}

	/**
	 * @return "" when the document is parsed, the exception otherwise
	 */
	private static String parse(JFlat jFlat, boolean removeNodes) {
		try {
			jFlat.parse(removeNodes);
			return "";
		} catch (Exception e) {
			return "parse " + describe(e) + " ";
		}
	}

	/**
	 * @return The class of the exception, with its message when JFlat writes it (the messages of the JDK change
	 *         with its version)
	 */
	private static String describe(Exception e) {
		boolean jflatMessage =
			e instanceof ParseException || e instanceof IllegalArgumentException || e instanceof IllegalStateException;
		return e.getClass().getName() + (jflatMessage ? ": " + quote(e.getMessage()) : "");
	}

	/**
	 * @return The toCSV() output (or its SHA-256 and length when it is long) and the properties when toCSV() cleaned
	 *         them, or the exception
	 */
	private static String goldenCsv(JFlat jFlat, String key, String[] properties, String separator) {
		String[] before = properties == null ? null : properties.clone();
		try {
			String csv = jFlat.toCSV(key, properties, separator).toString();
			String escaped = escape(csv);
			String result = escaped.length() <= 200
				? "\"" + escaped + "\""
				: "sha256:" + sha256(csv) + " length:" + csv.length();
			return Arrays.equals(before, properties) ? result : result + " properties after=" + quote(properties);
		} catch (Exception e) {
			return describe(e);
		}
	}

	private static String quote(String[] values) {
		if (values == null) {
			return "null";
		}
		List<String> quoted = new ArrayList<>();
		for (String value : values) {
			quoted.add(quote(value));
		}
		return "[" + String.join(",", quoted) + "]";
	}

	private static String quote(String value) {
		return value == null ? "null" : "\"" + escape(value) + "\"";
	}

	/**
	 * @return The value in ASCII, on one line
	 */
	private static String escape(String value) {
		StringBuilder escaped = new StringBuilder();
		for (char c : value.toCharArray()) {
			if (c == '\\') {
				escaped.append("\\\\");
			} else if (c == '\n') {
				escaped.append("\\n");
			} else if (c == '\r') {
				escaped.append("\\r");
			} else if (c == '\t') {
				escaped.append("\\t");
			} else if (c < 0x20 || c > 0x7e) {
				escaped.append(String.format("\\u%04x", (int) c));
			} else {
				escaped.append(c);
			}
		}
		return escaped.toString();
	}

	private static String sha256(String value) {
		try {
			StringBuilder hex = new StringBuilder();
			for (byte b : MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
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
