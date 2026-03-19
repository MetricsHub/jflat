# JFlat Utility

The JFlat Utility allows to parse JSON files into CSV or Flat map.

# How to run the JFlat Utility inside Java

Add JFlat in the list of dependencies in your [Maven **pom.xml**](https://maven.apache.org/pom.html):

```xml
<dependencies>
	<!-- [...] -->
	<dependency>
		<groupId>org.metricshub</groupId>
		<artifactId>jflat</artifactId>
		<version>${project.version}</version>
	</dependency>
</dependencies>
```

Instantiate it as follows:
```Java
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import org.metricshub.jflat.JFlat;

public class Main {

	public static void main(String[] args) throws ParseException, IOException {

		// Action value to export to
		final int actionFlatMap = 1;
		final int actionToCsv = 2;
		final String defaultSeparator = ";";

		// Initialization
		final int action = actionToCsv;

		/*
		 * 
		   [
			  {
			    "array": [
			      {
			        "id": "1",
			        "value": "0"
			      },
			      {
			        "id": "2",
			        "value": "0"
			      },
			      {
			        "id": "3",
			        "value": "0"
			      }
			    ]
			  },
			  {
			    "array": [
			      {
			        "id": "1",
			        "value": "0"
			      },
			      {
			        "id": "2",
			        "value": "0"
			      },
			      {
			        "id": "3",
			        "value": "0"
			      }
			    ]
			  }
			]
		 * 
		 */
		final String json = "[{\"array\":[{\"id\":\"1\",\"value\":\"0\"},{\"id\":\"2\",\"value\":\"0\"},{\"id\":\"3\",\"value\":\"0\"}]},{\"array\":[{\"id\":\"1\",\"value\":\"0\"},{\"id\":\"2\",\"value\":\"0\"},{\"id\":\"3\",\"value\":\"0\"}]}]";
		final String jsonEntryKey = "/array";
		// properties to export into CSV
		final List<String> propertyList = Arrays.asList("id", "value");
		final String separator = defaultSeparator;
		final boolean removeNodes = false;

		// Parse the JSON with JFlat
		JFlat jsonFlat = new JFlat(json);

		jsonFlat.parse(removeNodes);

		// toCSV or flatMap?
		if (action == actionToCsv) {
			System.out.print(
					jsonFlat.toCSV(jsonEntryKey, propertyList.toArray(new String[propertyList.size()]), separator));
		} else if (action == actionFlatMap) {
			System.out.print(jsonFlat.getFlatTree());
		}

	}

}
```

# Wildcard Support for Object Keys

When a JSON structure uses dynamic object keys (e.g. a map of items keyed by UID), use the `*` wildcard in the entry key path to expand all direct children of that object into individual CSV rows.

For example, given the following JSON:

```json
{
  "members": {
    "abc123": { "id": 0, "name": "Drive 0", "type": { "default": "NVMe" } },
    "def456": { "id": 1, "name": "Drive 1", "type": { "default": "NVMe" } }
  }
}
```

Use `*` to iterate over all children of `members`:

```Java
JFlat jFlat = new JFlat(json);
jFlat.parse();

// List all entries under members
System.out.print(jFlat.toCSV("/members/*", null, ";"));
// Output:
// /members/abc123;
// /members/def456;

// Extract properties (including nested paths)
System.out.print(jFlat.toCSV("/members/*", new String[] { "id", "name", "type/default" }, ";"));
// Output:
// /members/abc123;0;Drive 0;NVMe;
// /members/def456;1;Drive 1;NVMe;
```

## Escaping the Wildcard

If you have a JSON property literally named `*`, escape it with a backslash:

```Java
// Refers to the literal property "*" under members, not a wildcard
jFlat.toCSV("/members/\\*", new String[] { "name" }, ";");
```