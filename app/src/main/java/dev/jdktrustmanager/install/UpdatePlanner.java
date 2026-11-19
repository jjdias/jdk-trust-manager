package dev.jdktrustmanager.install;

import dev.jdktrustmanager.model.CatalogPackage;
import dev.jdktrustmanager.model.Installation;
import java.util.ArrayList;
import java.util.List;

public final class UpdatePlanner {

    public List<Installation> preview(List<Installation> installed, List<CatalogPackage> latest) {
        List<Installation> replacements = new ArrayList<>();
        for (Installation installation : installed) {
            if (installation.pinned()) {
                continue;
            }
            for (CatalogPackage pkg : latest) {
                if (pkg.distribution().equals(installation.vendorSlug())
                        && pkg.major() == installation.major()
                        && pkg.architecture().equals(installation.architecture())
                        && pkg.javafxBundled() == installation.javafxBundled()
                        && !pkg.javaVersion().equals(installation.javaVersion())) {
                    replacements.add(installation);
                }
            }
        }
        return replacements;
    }
}
