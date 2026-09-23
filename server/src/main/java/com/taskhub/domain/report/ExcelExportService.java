package com.taskhub.domain.report;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ExcelExportService {
  public static final String MIME = "text/csv; charset=UTF-8";
  private static final Pattern FORMULA = Pattern.compile("^[=+\\-@]");

  public byte[] csv(List<String> headers, List<List<?>> rows) {
    var out = new StringBuilder("\uFEFF");
    line(out, headers);
    rows.forEach(row -> line(out, row));
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  public String safeCell(Object value) {
    String text = Objects.toString(value, "").replace("\"", "\"\"");
    if (FORMULA.matcher(text).find()) text = "'" + text;
    return '"' + text + '"';
  }

  private void line(StringBuilder out, List<?> cells) {
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) out.append(',');
      out.append(safeCell(cells.get(i)));
    }
    out.append('\n');
  }

  public String safeFilename(String prefix, LocalDate from, LocalDate to) {
    String p = prefix == null ? "报表" : prefix.replaceAll("[^\\p{L}\\p{N}_-]", "_");
    return p + "_" + from + "_" + to + ".csv";
  }
}
