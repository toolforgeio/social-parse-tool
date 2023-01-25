/*-
 * =================================LICENSE_START==================================
 * toolforge-ngrams-tool
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

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sigpwned.discourse.core.util.Discourse;
import com.sigpwned.tabular4j.SpreadsheetFactory;
import com.sigpwned.tabular4j.csv.CsvSpreadsheetFormatFactory;
import com.sigpwned.tabular4j.excel.XlsxSpreadsheetFormatFactory;
import com.sigpwned.tabular4j.model.TabularWorksheetReader;
import com.sigpwned.tabular4j.model.TabularWorksheetRowWriter;
import com.sigpwned.tabular4j.model.WorksheetCellDefinition;
import com.sigpwned.uax29.Token;
import com.sigpwned.uax29.UAX29URLEmailTokenizer;

public class App {
  private static final int MAX_ENTITY_COUNT = 1000000;

  private static final String CSV = CsvSpreadsheetFormatFactory.DEFAULT_FILE_EXTENSION;

  private static final String XLSX = XlsxSpreadsheetFormatFactory.DEFAULT_FILE_EXTENSION;

  public static void main(String[] args) throws Exception {
    main(Discourse.configuration(Configuration.class, args).validate());
  }

  public static void main(Configuration configuration) throws Exception {
    AtomicLong count = new AtomicLong(0L);

    ConcurrentMap<Entity, Long> counts = new ConcurrentHashMap<>();

    System.out.printf("Parsing entities from SocialPosts input...\n");

    try (TabularWorksheetRowWriter csv = SpreadsheetFactory.getInstance()
        .writeTabularActiveWorksheet(
            () -> new BufferedOutputStream(configuration.entitiesCsv.getOutputStream()), CSV)
        .writeHeaders("id", "type", "value")) {
      try (TabularWorksheetRowWriter xlsx = SpreadsheetFactory.getInstance()
          .writeTabularActiveWorksheet(
              () -> new BufferedOutputStream(configuration.entitiesXlsx.getOutputStream()), XLSX)
          .writeHeaders("id", "type", "value")) {
        try (TabularWorksheetReader rows = SpreadsheetFactory.getInstance()
            .readActiveTabularWorksheet(configuration.socialPosts::getInputStream)) {
          final int idColumnIndex = rows.findColumnName(configuration.idColumnName)
              .orElseThrow(() -> new IllegalArgumentException(
                  "No column with name " + configuration.idColumnName));

          final int textColumnIndex = rows.findColumnName(configuration.textColumnName)
              .orElseThrow(() -> new IllegalArgumentException(
                  "No column with name " + configuration.textColumnName));

          rows.stream().parallel().map(r -> {
            String id =
                Optional.ofNullable(r.getCell(idColumnIndex).getValue(String.class)).orElse("");
            String text =
                Optional.ofNullable(r.getCell(textColumnIndex).getValue(String.class)).orElse("");
            return SocialPost.of(id, text);
          }).filter(p -> !p.text().isBlank()).flatMap(p -> tokens(p).stream()).filter(t -> {
            return switch (t.type()) {
              case ALPHANUM, HANGUL, HIRAGANA, IDEOGRAPHIC, KATAKANA, NUM, SOUTHEAST_ASIAN -> false;
              case CASHTAG, EMAIL, EMOJI, HASHTAG, MENTION, URL -> true;
            };
          }).filter(t -> count.incrementAndGet() < MAX_ENTITY_COUNT)
              .peek(t -> counts.merge(Entity.fromSocialToken(t), 1L, (a, b) -> a + b))
              .forEach(t -> {
                try {
                  synchronized (csv) {
                    csv.writeRow(asList(WorksheetCellDefinition.ofValue(t.id()),
                        WorksheetCellDefinition.ofValue(t.type().toString().toLowerCase()),
                        WorksheetCellDefinition.ofValue(t.text())));
                  }

                  synchronized (xlsx) {
                    xlsx.writeRow(asList(WorksheetCellDefinition.ofValue(t.id()),
                        WorksheetCellDefinition.ofValue(t.type().toString().toLowerCase()),
                        WorksheetCellDefinition.ofValue(t.text())));
                  }
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
        }
      }
    }

    System.out.println(configuration.entitiesCsv.toString());

    System.out.printf("Found a total of %d entities\n", count.get());
    if (count.get() <= MAX_ENTITY_COUNT)
      System.out.printf("WARNING: Entities is truncated at %d\n", MAX_ENTITY_COUNT);

    System.out.printf("Writing frequency analyses...\n");

    try (TabularWorksheetRowWriter csv = SpreadsheetFactory.getInstance()
        .writeTabularActiveWorksheet(
            () -> new BufferedOutputStream(configuration.countsCsv.getOutputStream()), CSV)
        .writeHeaders("type", "value", "count")) {
      try (TabularWorksheetRowWriter xlsx = SpreadsheetFactory.getInstance()
          .writeTabularActiveWorksheet(
              () -> new BufferedOutputStream(configuration.countsXlsx.getOutputStream()), XLSX)
          .writeHeaders("type", "value", "count")) {
        counts.entrySet().stream()
            .map(e -> EntityCount.of(e.getKey().type(), e.getKey().text(), e.getValue()))
            .sorted(Comparator.comparingLong(EntityCount::count).reversed()
                .thenComparing(EntityCount::text))
            .forEach(c -> {
              try {
                synchronized (csv) {
                  csv.writeRow(
                      List.of(WorksheetCellDefinition.ofValue(c.type().toString().toLowerCase()),
                          WorksheetCellDefinition.ofValue(c.text()),
                          WorksheetCellDefinition.ofValue(c.count())));
                }
                synchronized (xlsx) {
                  xlsx.writeRow(
                      List.of(WorksheetCellDefinition.ofValue(c.type().toString().toLowerCase()),
                          WorksheetCellDefinition.ofValue(c.text()),
                          WorksheetCellDefinition.ofValue(c.count())));
                }
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
      }
    }

    System.out.printf("Done!\n");
  }

  public static record EntityCount(Token.Type type, String text, long count) {
    public static EntityCount of(Token.Type type, String text, long count) {
      return new EntityCount(type, text, count);
    }

    public EntityCount(Token.Type type, String text, long count) {
      this.type = requireNonNull(type);
      this.text = requireNonNull(text);
      this.count = count;
    }
  }

  public static record Entity(Token.Type type, String text) {
    public static Entity fromSocialToken(SocialToken t) {
      return of(t.type(), t.text());
    }

    public static Entity of(Token.Type type, String text) {
      return new Entity(type, text);
    }

    public Entity(Token.Type type, String text) {
      this.type = requireNonNull(type);
      this.text = requireNonNull(text);
    }
  }

  public static record SocialPost(String id, String text) {
    public static SocialPost of(String id, String text) {
      return new SocialPost(id, text);
    }

    public SocialPost(String id, String text) {
      this.id = requireNonNull(id);
      this.text = requireNonNull(text);
    }
  }

  public static record SocialToken(String id, Token.Type type, String text) {
    public static SocialToken of(String id, Token token) {
      return new SocialToken(id, token.getType(), token.getText());
    }

    public static SocialToken of(String id, Token.Type type, String text) {
      return new SocialToken(id, type, text);
    }

    public SocialToken(String id, Token.Type type, String text) {
      this.id = requireNonNull(id);
      this.type = requireNonNull(type);
      this.text = requireNonNull(text);
    }
  }

  public static List<SocialToken> tokens(SocialPost post) {
    List<SocialToken> result = new ArrayList<>();
    try {
      String id = post.id();
      try (UAX29URLEmailTokenizer tokenizer = new UAX29URLEmailTokenizer(post.text())) {
        for (Token token = tokenizer.nextToken(); token != null; token =
            tokenizer.nextToken(token)) {
          Token.Type type = token.getType();

          String text = switch (type) {
            case CASHTAG, EMAIL, HASHTAG, MENTION -> token.getText().toLowerCase();
            case ALPHANUM, EMOJI, HANGUL, HIRAGANA, IDEOGRAPHIC, KATAKANA, NUM, SOUTHEAST_ASIAN -> token
                .getText();
            case URL -> standardizeUrl(token.getText());
          };

          result.add(SocialToken.of(id, type, text));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to tokenize text", e);
    }
    return result;
  }

  private static final Pattern URL = Pattern.compile("^((?:\\w+://)?[^/]+)(.*)$");

  private static String standardizeUrl(String url) {
    String result;

    Matcher m = URL.matcher(url);
    if (m.find())
      result = m.group(1).toLowerCase() + m.group(2);
    else
      result = url;

    return result;
  }
}
