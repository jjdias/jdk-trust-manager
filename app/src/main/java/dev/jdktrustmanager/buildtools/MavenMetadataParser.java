package dev.jdktrustmanager.buildtools;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Reads the {@code <version>} list from a Maven Central {@code maven-metadata.xml}. */
public final class MavenMetadataParser {

    private MavenMetadataParser() {
    }

    public static List<String> versions(String xml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            NodeList nodes = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)))
                    .getElementsByTagName("version");
            List<String> versions = new ArrayList<>();
            for (int index = 0; index < nodes.getLength(); index++) {
                String version = nodes.item(index).getTextContent().trim();
                if (!version.isEmpty() && !versions.contains(version)) {
                    versions.add(version);
                }
            }
            return versions;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Could not read Maven metadata: " + ex.getMessage(), ex);
        }
    }
}
