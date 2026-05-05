package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.dto.BackupItemDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class BackupService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.ROOT);

    private final S3Client s3;

    @Value("${app.backup.b2-bucket}")
    private String bucket;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String pgUser;

    @Value("${spring.datasource.password}")
    private String pgPassword;

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    public BackupService(S3Client s3) {
        this.s3 = s3;
    }

    public List<BackupItemDto> list(String db) {
        String prefix = prefixFor(db);
        List<BackupItemDto> out = new ArrayList<>();
        String token = null;
        do {
            ListObjectsV2Request req = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(token)
                    .build();
            ListObjectsV2Response res = s3.listObjectsV2(req);
            for (S3Object o : res.contents()) {
                LocalDateTime lm = o.lastModified() != null
                        ? LocalDateTime.ofInstant(o.lastModified(), ZoneId.systemDefault())
                        : null;
                out.add(new BackupItemDto(o.key(), o.size() != null ? o.size() : 0, lm));
            }
            token = res.isTruncated() ? res.nextContinuationToken() : null;
        } while (token != null);
        out.sort(Comparator.comparing(BackupItemDto::lastModified, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    public BackupItemDto generate(String db) {
        String extension = isMongo(db) ? ".archive.gz" : ".dump";
        String key = prefixFor(db) + TS.format(LocalDateTime.now()) + extension;
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("rb-backup-");
            Path file = tmpDir.resolve(key); 
            if (isPostgres(db)) {
                runPgDump(file);
            } else if (isMongo(db)) {
                runMongoDump(file);
            } else {
                throw new IllegalArgumentException("DB inválida.");
            }
            put(key, file);
            BackupItemDto dto = new BackupItemDto(key, Files.size(file), LocalDateTime.now());
            safeDelete(file);
            safeDeleteDir(tmpDir);
            return dto;
        } catch (IOException e) {
            safeDeleteDir(tmpDir);
            throw new IllegalArgumentException("No se pudo generar el backup.");
        }
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key requerida.");
        }
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public void restore(String db, String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key requerida.");
        }
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("rb-restore-");
            Path file = tmpDir.resolve("restore.bin");
            downloadTo(key, file);
            if (isPostgres(db)) {
                runPgRestore(file);
            } else if (isMongo(db)) {
                runMongoRestore(file);
            } else {
                throw new IllegalArgumentException("DB inválida.");
            }
            safeDelete(file);
            safeDeleteDir(tmpDir);
        } catch (IOException e) {
            safeDeleteDir(tmpDir);
            throw new IllegalArgumentException("No se pudo restaurar el backup.");
        }
    }

    private void put(String keyBase, Path file) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(keyBase)
                .contentType("application/octet-stream")
                .build();
        s3.putObject(req, RequestBody.fromFile(file));
    }

    private void downloadTo(String key, Path file) throws IOException {
        GetObjectRequest req = GetObjectRequest.builder().bucket(bucket).key(key).build();
        try (ResponseInputStream<?> in = s3.getObject(req)) {
            Files.copy(in, file);
        }
    }

    private String prefixFor(String db) {
        if (isPostgres(db)) return "backup_postgresql_";
        if (isMongo(db)) return "backup_mongodb_";
        throw new IllegalArgumentException("DB inválida.");
    }

    private boolean isPostgres(String db) {
        return db != null && (db.equalsIgnoreCase("postgresql") || db.equalsIgnoreCase("postgres"));
    }

    private boolean isMongo(String db) {
        return db != null && db.equalsIgnoreCase("mongodb");
    }

    private void runPgDump(Path out) {
        PgConn c = PgConn.fromJdbc(jdbcUrl);
        List<String> cmd = List.of(
                "pg_dump",
                "-Fc",
                "-h", c.host,
                "-p", String.valueOf(c.port),
                "-U", pgUser,
                "-f", out.toAbsolutePath().toString(),
                c.db
        );
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", pgPassword != null ? pgPassword : "");
        pb.redirectErrorStream(true);
        runOrThrow(pb, "pg_dump");
    }

    private void runPgRestore(Path in) {
        PgConn c = PgConn.fromJdbc(jdbcUrl);
        List<String> cmd = List.of(
                "pg_restore",
                "--clean",
                "--if-exists",
                "-h", c.host,
                "-p", String.valueOf(c.port),
                "-U", pgUser,
                "-d", c.db,
                in.toAbsolutePath().toString()
        );
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", pgPassword != null ? pgPassword : "");
        pb.redirectErrorStream(true);
        runOrThrow(pb, "pg_restore");
    }

    private void runMongoDump(Path out) {
        List<String> cmd = List.of(
                "mongodump",
                "--uri=" + (mongoUri != null ? mongoUri : ""),
                "--archive=" + out.toAbsolutePath(),
                "--gzip"
        );
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        runOrThrow(pb, "mongodump");
    }

    private void runMongoRestore(Path in) {
        List<String> cmd = List.of(
                "mongorestore",
                "--uri=" + (mongoUri != null ? mongoUri : ""),
                "--archive=" + in.toAbsolutePath(),
                "--gzip",
                "--drop"
        );
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        runOrThrow(pb, "mongorestore");
    }

    private void runOrThrow(ProcessBuilder pb, String tool) {
        try {
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                is.transferTo(OutputStreamNull.INSTANCE);
            }
            int code = p.waitFor();
            if (code != 0) {
                throw new IllegalArgumentException("Fallo " + tool + ".");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(tool + " no disponible.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Operación interrumpida.");
        }
    }

    private void safeDelete(Path p) {
        try {
            if (p != null) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    private void safeDeleteDir(Path dir) {
        if (dir == null) return;
        try {
            try (var st = Files.list(dir)) {
                st.forEach(this::safeDelete);
            }
        } catch (IOException ignored) {
        }
        safeDelete(dir);
    }

    private static final class PgConn {
        final String host;
        final int port;
        final String db;

        private PgConn(String host, int port, String db) {
            this.host = host;
            this.port = port;
            this.db = db;
        }

        static PgConn fromJdbc(String jdbc) {
            String raw = jdbc != null ? jdbc.trim() : "";
            String s = raw.startsWith("jdbc:") ? raw.substring(5) : raw;
            if (!s.startsWith("postgresql://")) {
                throw new IllegalArgumentException("JDBC inválido.");
            }
            s = s.substring("postgresql://".length());
            String hostPort;
            String dbPart;
            int slash = s.indexOf('/');
            hostPort = slash >= 0 ? s.substring(0, slash) : s;
            dbPart = slash >= 0 ? s.substring(slash + 1) : "";
            String db = dbPart;
            int q = db.indexOf('?');
            if (q >= 0) db = db.substring(0, q);
            String host = hostPort;
            int port = 5432;
            int colon = hostPort.lastIndexOf(':');
            if (colon > 0) {
                host = hostPort.substring(0, colon);
                try {
                    port = Integer.parseInt(hostPort.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                }
            }
            return new PgConn(host, port, db.isBlank() ? "postgres" : db);
        }
    }

    private static final class OutputStreamNull extends java.io.OutputStream {
        static final OutputStreamNull INSTANCE = new OutputStreamNull();

        @Override
        public void write(int b) {
        }
    }
}

