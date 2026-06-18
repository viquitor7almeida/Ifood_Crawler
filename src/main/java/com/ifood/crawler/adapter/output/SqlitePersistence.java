package main.java.com.ifood.crawler.adapter.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ifood.crawler.adapter.config.AppConfig;
import com.ifood.crawler.core.model.CrawlResult;
import com.ifood.crawler.core.model.ProductData;
import com.ifood.crawler.core.port.output.PersistencePort;
import com.ifood.crawler.infra.logging.StructuredLogger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Persiste resultados em SQLite (checkpoint) e exporta JSON/CSV.
 * Com suporte a backup automático e recuperação de falhas.
 */
public class SqlitePersistence implements PersistencePort {

    private static final StructuredLogger log = StructuredLogger.getLogger(SqlitePersistence.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path dbPath;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public SqlitePersistence(AppConfig config) {
        this.dbPath = config.getCheckpointDbPath();
        createTableIfNotExists();
        // Cria backup inicial se não existir
        backupDatabase();
    }

    /**
     * Cria a tabela de resultados se não existir.
     */
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
            log.info("Tabela crawl_results verificada/criada com sucesso");
        } catch (SQLException e) {
            log.error("Falha ao criar tabela", e);
            throw new RuntimeException("Falha ao criar tabela", e);
        }
    }

    /**
     * Cria um backup do banco de dados antes de operações críticas.
     */
    private void backupDatabase() {
        try {
            if (Files.exists(dbPath)) {
                Path backup = dbPath.resolveSibling(dbPath.getFileName() + ".backup");
                Files.copy(dbPath, backup, StandardCopyOption.REPLACE_EXISTING);
                log.debug("Backup do banco criado: {}", backup);
            }
        } catch (IOException e) {
            log.warn("Falha ao criar backup do banco: {}", e.getMessage());
        }
    }

    private Connection connect() throws SQLException {
        // Configura SQLite para melhor performance e concorrência
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        // WAL mode para melhor concorrência
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA cache_size=10000");
        }
        return conn;
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
            log.warn("Erro ao verificar URL já processada: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void saveResult(CrawlResult result) {
        // Tenta salvar, se falhar, tenta restaurar do backup
        try {
            doSaveResult(result);
        } catch (SQLException e) {
            log.error("Erro ao salvar resultado para URL: {}", result.url(), e);
            // Tenta restaurar do backup
            try {
                Path backup = dbPath.resolveSibling(dbPath.getFileName() + ".backup");
                if (Files.exists(backup)) {
                    log.info("Restaurando banco do backup...");
                    Files.copy(backup, dbPath, StandardCopyOption.REPLACE_EXISTING);
                    // Tenta novamente após restaurar
                    doSaveResult(result);
                    log.info("Resultado salvo com sucesso após restauração");
                } else {
                    throw new RuntimeException("Falha ao salvar resultado e backup não disponível", e);
                }
            } catch (Exception ex) {
                throw new RuntimeException("Falha crítica no checkpoint", ex);
            }
        }
    }

    private void doSaveResult(CrawlResult result) throws SQLException {
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
        }
    }

    /**
     * Salva múltiplos resultados em batch para melhor performance.
     */
    public void batchSave(List<CrawlResult> results) {
        if (results == null || results.isEmpty()) return;
        
        String sql = """
                INSERT OR REPLACE INTO crawl_results
                (url, title, normal_price, discount_price, image_url, status, error_message,
                 attempt_count, duration_millis, timestamp, is_from_retry)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            int batchSize = 0;
            
            for (CrawlResult result : results) {
                ProductData data = result.productData();
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
                pstmt.addBatch();
                batchSize++;
                
                if (batchSize % 100 == 0) {
                    pstmt.executeBatch();
                }
            }
            
            pstmt.executeBatch();
            conn.commit();
            log.info("Batch de {} resultados salvo com sucesso", results.size());
            
        } catch (SQLException e) {
            log.error("Erro ao salvar batch de resultados", e);
            throw new RuntimeException("Falha ao salvar batch", e);
        }
    }

    @Override
    public List<CrawlResult> getAllResults() {
        List<CrawlResult> list = new ArrayList<>();
        String sql = "SELECT * FROM crawl_results ORDER BY timestamp DESC";
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
            log.info("Recuperados {} resultados do banco", list.size());
        } catch (Exception e) {
            log.error("Erro ao recuperar resultados", e);
            throw new RuntimeException("Erro ao recuperar resultados", e);
        }
        return list;
    }

    @Override
    public void exportFinalResults(List<CrawlResult> results) {
        // Exporta em JSON
        exportToJson(results);
        // Exporta em CSV (diferencial)
        exportToCsv(results);
    }

    /**
     * Exporta resultados para JSON.
     */
    private void exportToJson(List<CrawlResult> results) {
        Path jsonPath = Path.of("output/results.json");
        try {
            Files.createDirectories(jsonPath.getParent());
            objectMapper.writeValue(jsonPath.toFile(), results);
            log.info("Resultados exportados para JSON: {}", jsonPath);
        } catch (Exception e) {
            log.error("Falha ao exportar JSON", e);
            throw new RuntimeException("Falha ao exportar JSON", e);
        }
    }

    /**
     * Exporta resultados para CSV (diferencial).
     */
    private void exportToCsv(List<CrawlResult> results) {
        Path csvPath = Path.of("output/results.csv");
        try {
            Files.createDirectories(csvPath.getParent());
            
            try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
                // Cabeçalho
                writer.write("URL,Título,Preço Normal,Preço Desconto,URL Imagem,Status,Mensagem Erro,Tentativas,Duração(ms),Timestamp,Retry");
                writer.newLine();
                
                // Dados
                for (CrawlResult result : results) {
                    ProductData data = result.productData();
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,\"%s\",%b",
                            escapeCsv(result.url()),
                            escapeCsv(data.title()),
                            escapeCsv(data.normalPrice()),
                            escapeCsv(data.discountPrice()),
                            escapeCsv(data.imageUrl() != null ? data.imageUrl().toString() : ""),
                            data.status().name(),
                            escapeCsv(data.errorMessage()),
                            result.attemptCount(),
                            result.durationMillis(),
                            result.timestamp().toString(),
                            result.isFromRetry()
                    ));
                    writer.newLine();
                }
            }
            log.info("Resultados exportados para CSV: {}", csvPath);
        } catch (Exception e) {
            log.error("Falha ao exportar CSV", e);
            // Não lança exceção, apenas loga (CSV é diferencial)
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    /**
     * Limpa checkpoints antigos para evitar crescimento infinito.
     * @param daysToKeep Dias de histórico a manter
     */
    public void cleanOldCheckpoints(int daysToKeep) {
        String sql = "DELETE FROM crawl_results WHERE timestamp < ? AND status = 'SUCCESS'";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            Instant cutoff = Instant.now().minus(Duration.ofDays(daysToKeep));
            pstmt.setString(1, cutoff.toString());
            int deleted = pstmt.executeUpdate();
            
            log.info("Removidos {} registros antigos (> {} dias)", deleted, daysToKeep);
            
            // VACUUM para liberar espaço
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("VACUUM");
            }
            
        } catch (SQLException e) {
            log.warn("Falha ao limpar checkpoints antigos: {}", e.getMessage());
        }
    }
}