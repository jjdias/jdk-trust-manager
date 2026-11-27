package dev.jdktrustmanager.persist;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.jdktrustmanager.model.CatalogDocument;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.model.SourcesDocument;
import dev.jdktrustmanager.model.ToolInstallation;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class JsonStores {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public void writeInstallations(Path file, List<Installation> installations) throws IOException {
        write(file, installations);
    }

    public List<Installation> readInstallations(Path file) throws IOException {
        return mapper.readerForListOf(Installation.class).readValue(file.toFile());
    }

    public void writeTools(Path file, List<ToolInstallation> tools) throws IOException {
        write(file, tools);
    }

    /** Missing file means nothing is installed yet. */
    public List<ToolInstallation> readTools(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        return mapper.readerForListOf(ToolInstallation.class).readValue(file.toFile());
    }

    public void writeSettings(Path file, Settings settings) throws IOException {
        write(file, settings);
    }

    public Settings readSettings(Path file) throws IOException {
        return mapper.readValue(file.toFile(), Settings.class);
    }

    public void writeCatalog(Path file, CatalogDocument catalog) throws IOException {
        write(file, catalog);
    }

    public CatalogDocument readCatalog(Path file) throws IOException {
        return mapper.readValue(file.toFile(), CatalogDocument.class);
    }

    public void writeSources(Path file, SourcesDocument sources) throws IOException {
        write(file, sources);
    }

    public SourcesDocument readSources(Path file) throws IOException {
        return mapper.readValue(file.toFile(), SourcesDocument.class);
    }

    private void write(Path file, Object value) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
