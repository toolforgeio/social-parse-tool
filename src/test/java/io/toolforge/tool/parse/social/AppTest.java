/*-
 * =================================LICENSE_START==================================
 * social-parse-tool
 * ====================================SECTION=====================================
 * Copyright (C) 2022 - 2023 ToolForge
 * ====================================SECTION=====================================
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
 * ==================================LICENSE_END===================================
 */
package io.toolforge.tool.parse.social;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import java.io.File;
import java.io.FileInputStream;
import org.junit.Test;
import com.google.common.io.Resources;
import com.sigpwned.tabular4j.SpreadsheetFactory;
import com.sigpwned.tabular4j.model.TabularWorksheetReader;
import io.toolforge.toolforge4j.io.InputSource;
import io.toolforge.toolforge4j.io.OutputSink;

public class AppTest {
  @Test
  public void test() throws Exception {
    File csvEntitiesFile = File.createTempFile("entities.", ".csv");
    csvEntitiesFile.deleteOnExit();

    File xlsxEntitiesFile = File.createTempFile("entities.", ".xlsx");
    xlsxEntitiesFile.deleteOnExit();

    File csvCountsFile = File.createTempFile("counts.", ".csv");
    csvCountsFile.deleteOnExit();

    File xlsxCountsFile = File.createTempFile("counts.", ".xlsx");
    xlsxCountsFile.deleteOnExit();

    Configuration configuration = new Configuration();
    configuration.socialPosts = new InputSource(Resources.getResource("tweets.csv").toURI());
    configuration.entitiesCsv = new OutputSink(csvEntitiesFile.toURI());
    configuration.entitiesXlsx = new OutputSink(xlsxEntitiesFile.toURI());
    configuration.countsCsv = new OutputSink(csvCountsFile.toURI());
    configuration.countsXlsx = new OutputSink(xlsxCountsFile.toURI());
    configuration.idColumnName = "id";
    configuration.textColumnName = "text";

    App.main(configuration);

    final String[][] expectedEntries = new String[][] {{"1618002665627353089", "emoji", "➡️"},
        {"1618220776515309569", "mention", "@joshmalina"},
        {"1618083024209412096", "url", "https://bit.ly/3FgIra4"},
        {"1618011970686578688", "hashtag", "#ut27"},
        {"1618011970686578688", "hashtag", "#gonetotexas"},
        {"1618011970686578688", "hashtag", "#bealonghorn"}};

    for (File file : new File[] {csvEntitiesFile, xlsxEntitiesFile}) {
      try (TabularWorksheetReader rows = SpreadsheetFactory.getInstance()
          .readActiveTabularWorksheet(() -> new FileInputStream(file))) {
        assertThat(rows.stream().map(row -> {
          return new String[] {
              row.findCellByColumnName("id").map(c -> c.getValue(String.class)).get(),
              row.findCellByColumnName("type").map(c -> c.getValue(String.class)).get(),
              row.findCellByColumnName("value").map(c -> c.getValue(String.class)).get()};
        }).toArray(String[][]::new), is(expectedEntries));
      }
    }

    final Object[][] expectedCounts = new Object[][] {{"hashtag", "#bealonghorn", 1},
        {"hashtag", "#gonetotexas", 1}, {"hashtag", "#ut27", 1}, {"mention", "@joshmalina", 1},
        {"url", "https://bit.ly/3FgIra4", 1}, {"emoji", "➡️", 1}};

    for (File file : new File[] {csvCountsFile, xlsxCountsFile}) {
      try (TabularWorksheetReader rows = SpreadsheetFactory.getInstance()
          .readActiveTabularWorksheet(() -> new FileInputStream(file))) {
        assertThat(rows.stream().map(row -> {
          return new Object[] {
              row.findCellByColumnName("type").map(c -> c.getValue(String.class)).get(),
              row.findCellByColumnName("value").map(c -> c.getValue(String.class)).get(),
              row.findCellByColumnName("count").map(c -> c.getValue(Integer.class)).get()};
        }).toArray(Object[][]::new), is(expectedCounts));
      }
    }
  }
}
