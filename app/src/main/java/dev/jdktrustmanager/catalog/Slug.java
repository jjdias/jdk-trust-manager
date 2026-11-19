package dev.jdktrustmanager.catalog;

public final class Slug {

    private Slug() {
    }

    public static String of(String apiParameter, boolean javafxBundled, String architecture) {
        String slug = apiParameter.replace('_', '-');
        if (javafxBundled) {
            slug = slug + "-fx";
        }
        if ("arm64".equalsIgnoreCase(architecture)) {
            slug = slug + "-arm64";
        }
        return slug;
    }
}
