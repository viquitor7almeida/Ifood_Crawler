package main.java.com.ifood.crawler.adapter.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ifood.crawler.adapter.config.AppConfig;
import com.ifood.crawler.core.model.CrawlResult;
import com.ifood.crawler.core.model.ProductData;
import com.ifood.crawler.core.port.output.PersistencePort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Persiste resultados em SQLite (checkpoint) e exporta JSON final.
 */
public class SqlitePersistence implements PersistencePort {

    private final Path dbPath;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public SqlitePersistence(AppConfig config) {
        this.dbPath = config.getCheckpointDbPath();
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        String sql = """
                CREATE TABLE IF NOT EXISTS crawl_results (
                    url TEXT PRIMARY KEY,
                    title TEXT,
                    normal_price TEXT,
                    discount_price TEXT,
                    image_url TEXT,
                    status TEXT NOT NULL,
                    error_message TEXT,
                    attempt_count INTEGER,
                    duration_millis INTEGER,
                    timestamp TEXT,
                    is_from_retry INTEGER
                )
                """;
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao criar tabela", e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    @Override
    public boolean isAlreadyProcessed(String url) {
        String sql = "SELECT 1 FROM crawl_results WHERE url = ? AND status = 'SUCCESS'";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, url);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void saveResult(CrawlResult result) {
        String sql = """
                INSERT OR REPLACE INTO crawl_results
                (url, title, normal_price, discount_price, image_url, status, error_message,
                 attempt_count, duration_millis, timestamp, is_from_retry)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        ProductData data = result.productData();
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, result.url());
            pstmt.setString(2, data.title());
            pstmt.setString(3, data.normalPrice());
            pstmt.setString(4, data.discountPrice());
            pstmt.setString(5, data.imageUrl() != null ? data.imageUrl().toString() : null);
            pstmt.setString(6, data.status().name());
            pstmt.setString(7, data.errorMessage());
            pstmt.setInt(8, result.attemptCount());
            pstmt.setLong(9, result.durationMillis());
            pstmt.setString(10, result.timestamp().toString());
            pstmt.setInt(11, result.isFromRetry() ? 1 : 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar resultado", e);
        }
    }

    @Override
    public List<CrawlResult> getAllResults() {
        List<CrawlResult> list = new ArrayList<>();
        String sql = "SELECT * FROM crawl_results";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ProductData data = new ProductData(
                        rs.getString("title"),
                        rs.getString("normal_price"),
                        rs.getString("discount_price"),
                        new java.net.URL(rs.getString("url")),
                        rs.getString("image_url") != null ? new java.net.URL(rs.getString("image_url")) : null,
                        com.ifood.crawler.core.model.CrawlStatus.valueOf(rs.getString("status")),
                        rs.getString("error_message")
                );
                CrawlResult result = new CrawlResult(
                        rs.getString("url"),
                        data,
                        rs.getInt("attempt_count"),
                        rs.getLong("duration_millis"),
                        java.time.Instant.parse(rs.getString("timestamp")),
                        rs.getInt("is_from_retry") == 1
                );
                list.add(result);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao recuperar resultados", e);
        }
        return list;
    }

    @Override
    public void exportFinalResults(List<CrawlResult> results) {
        Path outputPath = Path.of("output/results.json");
        try {
            Files.createDirectories(outputPath.getParent());
            objectMapper.writeValue(outputPath.toFile(), results);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao exportar JSON", e);
        }
    }
}