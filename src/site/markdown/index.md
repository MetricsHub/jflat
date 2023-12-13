# JFlat Utility

The JFlat Utility allows to parse JSON files into CSV or Flat map.

# How to run the JFlat Utility inside Java

Add JFlat in the list of dependencies in your [Maven **pom.xml**](https://maven.apache.org/pom.html):

```xml
<dependencies>
	<!-- [...] -->
	<dependency>
		<groupId>org.sentrysoftware</groupId>
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

import org.sentrysoftware.jflat.JFlat;

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