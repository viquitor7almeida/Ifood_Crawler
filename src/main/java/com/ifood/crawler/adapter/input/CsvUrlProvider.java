package main.java.com.ifood.crawler.adapter.input;

import com.ifood.crawler.core.port.input.UrlProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 *le urls de um arquivo CSV. Supõe:
 *sem cabecalho, primeira coluna = URL.
 *ou se houver cabecalho, coluna chamada "url"
 */
public class CsvUrlProvider implements UrlProvider {

    private final Path csvPath;
    private long totalLines = -1;

    public CsvUrlProvider(Path csvPath) {
        this.csvPath = csvPath;
    }

    @Override
    public Stream<String> urls() {
        try {
            BufferedReader reader = Files.newBufferedReader(csvPath);
            //detecta cabecalho
            String firstLine = reader.readLine();
            if (firstLine == null) return Stream.empty();

            boolean hasHeader = firstLine.toLowerCase().contains("url");
            int urlColumnIndex = 0;

            if (hasHeader) {
                String[] headers = firstLine.split(",");
                for (int i = 0; i < headers.length; i++) {
                    if (headers[i].trim().equalsIgnoreCase("url")) {
                        urlColumnIndex = i;
                        break;
                    }
                }
            } else {
                //reprocessar a primeira linha como dado
                reader.close();
                return Files.lines(csvPath)
                        .map(line -> line.split(",")[0].trim())
                        .filter(url -> !url.isBlank());
            }

            //se tem cabecalho le as proximas linhas
            final int finalIndex = urlColumnIndex;
            return reader.lines()
                    .map(line -> line.split(",")[finalIndex].trim())
                    .filter(url -> !url.isBlank())
                    .onClose(() -> {
                        try { reader.close(); } catch (IOException e) {}
                    });

        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler arquivo CSV: " + csvPath, e);
        }
    }

    @Override
    public long totalUrls() {
        if (totalLines == -1) {
            try (Stream<String> stream = Files.lines(csvPath)) {
                totalLines = stream.count();
                //se tiver cabecalho, subtrai 1
                try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
                    String first = reader.readLine();
                    if (first != null && first.toLowerCase().contains("url")) totalLines--;
                }
            } catch (IOException e) {
                totalLines = 0;
            }
        }
        return totalLines;
    }
}