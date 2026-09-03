package com.vedryxtech.voiceagent.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a project's brochure and photos back out of its folder.
 *
 * <p>Nothing here writes the folder — for now someone puts the files there directly (same
 * bucket, {@code app.storage.project-media-prefix}), and this service just lists what is
 * under a project's slug and mints links to it. Works with or without storage configured,
 * the same way {@link CallArtifactService} does: a CRM with no bucket has nothing to send,
 * not an error.
 */
@Service
public class ProjectMediaService {

    private static final Logger log = LoggerFactory.getLogger(ProjectMediaService.class);

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final ObjectStorageProperties properties;
    private final Optional<CallArtifactStore> store;

    public ProjectMediaService(ObjectStorageProperties properties, Optional<CallArtifactStore> store) {
        this.properties = properties;
        this.store = store;
    }

    public boolean isEnabled() {
        return properties.isEnabled() && store.isPresent();
    }

    /** Everything found in the project's folder, brochures first, in the order storage lists them. */
    public ProjectMedia mediaFor(String project) {
        if (!isEnabled()) {
            return ProjectMedia.EMPTY;
        }
        String folder = ProjectMediaKeys.folder(properties.getProjectMediaPrefix(), project);
        List<String> keys;
        try {
            keys = store.orElseThrow().list(folder);
        } catch (RuntimeException ex) {
            log.error("Could not list project media at {}: {}", folder, ex.getMessage());
            return ProjectMedia.EMPTY;
        }

        List<String> documents = keys.stream().filter(key -> hasExtension(key, DOCUMENT_EXTENSIONS)).toList();
        List<String> photos = keys.stream().filter(key -> hasExtension(key, IMAGE_EXTENSIONS)).toList();
        return new ProjectMedia(documents, photos);
    }

    public String presignedGet(String key) {
        return store.orElseThrow().presignedGet(key);
    }

    /**
     * Add one file to a project's folder. No listing of what's already there, no
     * overwrite check — the folder is the whole model, so uploading {@code brochure.pdf}
     * twice just replaces it.
     */
    public String upload(String project, String filename, byte[] content, String contentType) {
        if (!isEnabled()) {
            throw new IllegalStateException("Object storage is not configured (app.storage.enabled=false)");
        }
        String key = ProjectMediaKeys.folder(properties.getProjectMediaPrefix(), project) + filename;
        store.orElseThrow().putObject(key, content, contentType);
        return key;
    }

    private static boolean hasExtension(String key, Set<String> extensions) {
        int dot = key.lastIndexOf('.');
        if (dot < 0 || dot == key.length() - 1) {
            return false;
        }
        return extensions.contains(key.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** What one project's folder holds, split by what a WhatsApp message can carry as. */
    public record ProjectMedia(List<String> documentKeys, List<String> photoKeys) {

        static final ProjectMedia EMPTY = new ProjectMedia(List.of(), List.of());

        public boolean isEmpty() {
            return documentKeys.isEmpty() && photoKeys.isEmpty();
        }
    }
}
