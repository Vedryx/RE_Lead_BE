package com.vedryxtech.voiceagent.storage.api;

import com.vedryxtech.voiceagent.storage.ProjectMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A place to put a project's brochure and photos without touching object storage directly.
 *
 * <p>One folder per project, brochure and photos together — see
 * {@link ProjectMediaService}. Uploading here is exactly the same as dropping the file
 * into that folder by hand; this just means Swagger can do it instead of an S3 client.
 */
@Tag(name = "9. Project media",
        description = "Upload the brochure and photos a project's leads get on WhatsApp when they ask for details.")
@RestController
@RequestMapping(path = "/api/v1/project-media", produces = "application/json")
public class ProjectMediaController {

    private final ProjectMediaService projectMediaService;

    public ProjectMediaController(ProjectMediaService projectMediaService) {
        this.projectMediaService = projectMediaService;
    }

    @Operation(summary = "Upload a brochure or photo for a project",
            description = "The file lands in that project's folder under the name it's uploaded with. "
                    + "Uploading a name that's already there replaces it. `project` must match a lead's "
                    + "`project` field exactly (it's slugged the same way on both sides).")
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, String>> upload(@RequestParam String project,
                                                       @RequestParam MultipartFile file) {
        try {
            String key = projectMediaService.upload(project, file.getOriginalFilename(),
                    file.getBytes(), file.getContentType());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("key", key));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Operation(summary = "What's in a project's media folder right now",
            description = "The brochure/document keys and the photo keys found under that project's "
                    + "folder — a quick way to confirm an upload landed before relying on it.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'AGENT', 'VIEWER')")
    public Map<String, List<String>> list(@RequestParam String project) {
        var media = projectMediaService.mediaFor(project);
        Map<String, List<String>> response = new LinkedHashMap<>();
        response.put("documents", media.documentKeys());
        response.put("photos", media.photoKeys());
        return response;
    }
}
