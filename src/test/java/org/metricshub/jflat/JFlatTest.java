package org.metricshub.jflat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import org.junit.jupiter.api.Test;

public class JFlatTest {

	@Test
	void flatMap() throws IllegalStateException, ParseException, IOException {
		JFlat jFlat;

		jFlat = new JFlat(getResourceAsString("/simple.json"));
		jFlat.parse();
		assertEquals(getResourceAsString("/simple-flatMap.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/simple.json"));
		jFlat.parse(true);
		assertEquals(getResourceAsString("/simple-flatMap-removeNodes.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/complex.json"));
		jFlat.parse();
		assertEquals(getResourceAsString("/complex-flatMap.txt"), jFlat.getFlatTree().toString());

		jFlat = new JFlat(getResourceAsString("/large.json"));
		jFlat.parse();
		assertEquals(getResourceAsString("/large-flatMap.txt"), jFlat.getFlatTree().toString());
	}

	@Test
	void edgeCases() throws IllegalStateException, ParseException, IOException {
		// parse() not done
		JFlat simple = new JFlat(getResourceAsString("/simple.json"));
		assertThrows(
			IllegalStateException.class,
			() -> simple.getFlatTree(),
			"Non-parsed JSON document should throw an IllegalStateException"
		);

		// empty JSON
		JFlat empty = new JFlat("");
		assertThrows(
			ParseException.class,
			() -> empty.parse(),
			"Empty JSON document should trigger a ParseException error"
		);

		// syntax error
		JFlat wrong = new JFlat("{ this: is a wrong JSON document");
		assertThrows(
			ParseException.class,
			() -> wrong.parse(),
			"JSON document with syntax error should trigger a ParseException error"
		);
	}

	@Test
	void csv() throws IllegalStateException, ParseException, IOException {
		JFlat simple = new JFlat(getResourceAsString("/simple.json"));
		simple.parse();
		assertEquals("[0];\n[1];\n", simple.toCSV("/", null, null).toString());
		assertEquals("[0]/attribute1;\n[1]/attribute1;\n", simple.toCSV("/attribute1", null, null).toString());
		assertEquals(
			"[0]/arrayA[0];\n[0]/arrayA[1];\n[0]/arrayA[2];\n[1]/arrayA[0];\n[1]/arrayA[1];\n[1]/arrayA[2];\n",
			simple.toCSV("/arrayA", null, null).toString()
		);
		assertEquals(
			"[0]/arrayB[0]/id;\n[0]/arrayB[1]/id;\n[0]/arrayB[2]/id;\n[1]/arrayB[0]/id;\n[1]/arrayB[1]/id;\n[1]/arrayB[2]/id;\n",
			simple.toCSV("/arrayB/id", null, null).toString()
		);
		assertEquals("", simple.toCSV("/nonexistent", null, null).toString());
	}

	@Test
	void csvProperties() throws IllegalStateException, ParseException, IOException {
		JFlat simple = new JFlat(getResourceAsString("/simple.json"));
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
