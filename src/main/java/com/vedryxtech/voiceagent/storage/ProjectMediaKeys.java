package com.vedryxtech.voiceagent.storage;

/**
 * Where one project's WhatsApp-able media lives: a single flat folder, brochure and photos
 * together.
 *
 * <pre>
 * project-details/{project-slug}/
 *     brochure.pdf
 *     photo-1.jpg
 *     photo-2.jpg
 * </pre>
 *
 * <p>One folder rather than a per-file record, deliberately — there is nothing here for a
 * person to manage file-by-file yet. Someone drops files into the folder for a project's
 * slug, and {@link ProjectMediaService} hands out whatever it finds.
 */
final class ProjectMediaKeys {

    private ProjectMediaKeys() {
    }

    /** The folder for one project, with a trailing slash. */
    static String folder(String root, String project) {
        return "%s/%s/".formatted(trimSlashes(root), CallArtifactKeys.slug(project));
    }

    private static String trimSlashes(String value) {
        return value == null ? "" : value.replaceAll("^/+|/+$", "");
    }
}
